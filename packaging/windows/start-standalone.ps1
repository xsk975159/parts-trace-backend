[CmdletBinding()]
param([string]$ConfigPath, [switch]$CheckOnly, [switch]$NoBrowser)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$packageDirectory=Split-Path -Parent $PSScriptRoot
$dataDirectory=Join-Path $packageDirectory 'data'
$databaseDirectory=Join-Path $dataDirectory 'mysql'
$statePath=Join-Path $dataDirectory 'standalone-secrets.json'
$stopPath=Join-Path $dataDirectory 'stop.request'
$bootstrapPath=Join-Path $dataDirectory 'bootstrap.sql'
$java=Join-Path $packageDirectory 'runtime/java/bin/java.exe'
$mysqld=Join-Path $packageDirectory 'runtime/mysql/bin/mysqld.exe'
$jar=Join-Path $packageDirectory 'parts-trace-backend.jar'
$databaseProcess=$null; $applicationProcess=$null; $databaseOwned=$false
$oldDirectory=Get-Location

function RandomSecret([int]$length) {
    $bytes=New-Object byte[] $length
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {$rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes)} finally {$rng.Dispose()}
}
function ProtectSecret([string]$value) {
    $secure=ConvertTo-SecureString $value -AsPlainText -Force
    try {return ConvertFrom-SecureString $secure} finally {$secure.Dispose()}
}
function UnprotectSecret([string]$value) {
    $secure=ConvertTo-SecureString $value
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose()}
}
function SaveState {
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath ($statePath+'.tmp') -Encoding UTF8
    Move-Item -LiteralPath ($statePath+'.tmp') -Destination $statePath -Force
}
function QuoteArgument([string]$value) {
    if($value.Contains('"')) {throw '运行路径不能包含双引号。'}
    return '"'+$value+'"'
}
function PortOccupied([int]$port) {
    $client=New-Object Net.Sockets.TcpClient
    try {
        $attempt=$client.BeginConnect('127.0.0.1',$port,$null,$null)
        return ($attempt.AsyncWaitHandle.WaitOne(300) -and $client.Connected)
    } finally {$client.Dispose()}
}
function DatabaseCommand([string]$action) {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $lines=@(& $java '-Dfile.encoding=UTF-8' '-Dloader.main=com.kewei.parts.infrastructure.desktop.StandaloneDatabase' -cp $jar org.springframework.boot.loader.PropertiesLauncher $action 2>&1)
        $code=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previousPreference}
    return @{Code=$code; Lines=$lines}
}
function CheckSchema {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $lines=@(& $java '-Dfile.encoding=UTF-8' '-Dloader.main=com.kewei.parts.infrastructure.desktop.DesktopDatabaseCheck' -cp $jar org.springframework.boot.loader.PropertiesLauncher 2>&1)
        $code=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previousPreference}
    if($code -ne 0 -or @($lines | Where-Object {[string]$_ -match '^MISSING='}).Count) {
        throw '数据库结构检查未通过。请保留data目录并查看运行说明，不会自动重建已有表。'
    }
}

try {
    Set-Location -LiteralPath $packageDirectory
    if(-not $ConfigPath) {$ConfigPath=Join-Path $packageDirectory 'config/runtime.json'}
    $config=Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach($file in @($java,$mysqld,$jar)) {
        if(-not (Test-Path -LiteralPath $file -PathType Leaf)) {throw '运行环境文件不完整，请完整解压独立运行包。'}
    }
    if($config.serverAddress -ne '127.0.0.1') {throw '独立运行包仅支持本机访问，serverAddress须为127.0.0.1。'}
    $serverPort=[int]$config.serverPort
    if($serverPort -lt 1024 -or $serverPort -gt 65535) {throw 'serverPort须为1024至65535。'}
    if($config.chainMode -notin @('db','real') -or $config.objectMode -notin @('db','ipfs')) {throw '链或对象存储模式不正确。'}
    if($packageDirectory.Contains('"')) {throw '运行目录不能包含双引号。'}
    if([Text.Encoding]::Default.GetString([Text.Encoding]::Default.GetBytes($packageDirectory)) -ne $packageDirectory) {
        throw '当前Windows系统语言无法表示此目录中的字符，请将运行包完整解压到英文目录后启动。'
    }
    if($CheckOnly) {
        Write-Host '内置Java、MySQL和应用文件检查通过。此检查不启动数据库，也不初始化数据。'
        exit 0
    }
    if(PortOccupied $serverPort) {throw ('应用端口'+$serverPort+'已占用，请修改config/runtime.json中的serverPort。')}
    $env:JAVA_HOME=Join-Path $packageDirectory 'runtime/java'
    $env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
    # 数据和临时初始化凭据仅允许当前Windows账户及SYSTEM访问。
    $security=[IO.Directory]::GetAccessControl($dataDirectory,[Security.AccessControl.AccessControlSections]::Access)
    $security.SetAccessRuleProtection($true,$false)
    foreach($existingRule in $security.GetAccessRules($true,$false,[Security.Principal.SecurityIdentifier])) {
        $security.PurgeAccessRules($existingRule.IdentityReference)
    }
    foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,(New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
        $rule=New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
        $security.AddAccessRule($rule)
    }
    [IO.Directory]::SetAccessControl($dataDirectory,$security)
    if(Test-Path -LiteralPath $stopPath) {Remove-Item -LiteralPath $stopPath}
    if(Test-Path -LiteralPath $statePath) {
        $state=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
        if($state.Version -ne 1) {throw '本地配置版本不匹配，请保留data目录。'}
        $rootPassword=UnprotectSecret $state.RootPassword
        $applicationPassword=UnprotectSecret $state.ApplicationPassword
        $jwt=UnprotectSecret $state.JwtSecret
        $encryptionKey=UnprotectSecret $state.EncryptionKey
    } else {
        if((Test-Path -LiteralPath $databaseDirectory) -and @(Get-ChildItem -LiteralPath $databaseDirectory -Force).Count) {
            throw '数据库目录已存在但缺少原密钥配置，已停止；不能用新密码覆盖已有数据库。'
        }
        $preferredPort=[int]$config.databasePort
        if($preferredPort -ne 0 -and ($preferredPort -lt 1024 -or $preferredPort -gt 65535)) {throw 'databasePort须为0或1024至65535。'}
        $databasePort=$preferredPort
        if($databasePort -eq 0) {
            foreach($candidate in 13306..13336) {
                if($candidate -ne $serverPort -and -not (PortOccupied $candidate)) {$databasePort=$candidate;break}
            }
            if($databasePort -eq 0) {throw '未找到可用私有数据库端口。'}
        }
        if($databasePort -eq $serverPort -or (PortOccupied $databasePort)) {throw '配置的数据库端口不可用。'}
        $rootPassword=RandomSecret 36; $applicationPassword=RandomSecret 36
        $jwt=RandomSecret 48; $encryptionKey=RandomSecret 32
        $state=[pscustomobject]@{
            Version=1; InstanceId=[guid]::NewGuid().ToString(); DatabasePort=$databasePort
            Initialized=$false; Provisioned=$false
            RootPassword=(ProtectSecret $rootPassword); ApplicationPassword=(ProtectSecret $applicationPassword)
            JwtSecret=(ProtectSecret $jwt); EncryptionKey=(ProtectSecret $encryptionKey)
        }
        SaveState
    }
    $databasePort=[int]$state.DatabasePort
    if($databasePort -lt 1024 -or $databasePort -gt 65535 -or $databasePort -eq $serverPort) {throw '保存的数据库端口不正确。'}
    if(PortOccupied $databasePort) {throw ('私有数据库端口'+$databasePort+'已占用，未连接或停止该端口上的其他服务。')}
    $env:KEWEI_DBSERVER='127.0.0.1'; $env:KEWEI_DBPORT=[string]$databasePort
    $env:KEWEI_DBUSER='parts_trace_app'; $env:KEWEI_DBPWD=$applicationPassword
    # 防止宿主机已有Spring数据源环境变量将独立版指向其他业务库。
    $env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$databasePort/parts_trace?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    $env:SPRING_DATASOURCE_USERNAME='parts_trace_app'; $env:SPRING_DATASOURCE_PASSWORD=$applicationPassword
    $env:JWT_SECRET=$jwt; $env:KEY_ENCRYPTION_KEY=$encryptionKey
    $env:PARTS_TRACE_PRIVATE_DB_PORT=[string]$databasePort
    $env:PARTS_TRACE_PRIVATE_DB_DIRECTORY=$databaseDirectory
    $env:PARTS_TRACE_PRIVATE_DB_ID=[string]$state.InstanceId
    $env:PARTS_TRACE_PRIVATE_FILESYSTEM_CHARSET=switch([Text.Encoding]::Default.CodePage) {
        936 {'GBK'}; 950 {'Big5'}; 932 {'windows-31j'}; 949 {'x-windows-949'}; 65001 {'UTF-8'}
        default {'windows-'+[Text.Encoding]::Default.CodePage}
    }
    $env:PARTS_TRACE_PRIVATE_ROOT_PASSWORD=$rootPassword
    $env:PARTS_TRACE_CHAIN_MODE=[string]$config.chainMode
    $env:PARTS_TRACE_OBJECT_MODE=[string]$config.objectMode
    $logDirectory=Join-Path $packageDirectory 'logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $databaseDirectory -Force | Out-Null
    $iniPath=Join-Path $dataDirectory 'mysql.ini'
    $mysqlBase=(Join-Path $packageDirectory 'runtime/mysql').Replace('\','/')
    $mysqlData=$databaseDirectory.Replace('\','/')
    $mysqlLog=(Join-Path $logDirectory 'mysql-error.log').Replace('\','/')
    $ini=@"
[mysqld]
basedir="$mysqlBase"
datadir="$mysqlData"
port=$databasePort
bind-address=127.0.0.1
mysqlx=0
skip-name-resolve
skip-log-bin
local-infile=0
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
innodb-buffer-pool-size=64M
innodb-redo-log-capacity=64M
max-connections=30
log-error="$mysqlLog"
"@
    # Windows版MySQL按系统ANSI代码页读取配置路径；UTF-8在中文Windows上会导致路径乱码。
    [IO.File]::WriteAllText($iniPath,$ini,[Text.Encoding]::Default)
    $defaults=QuoteArgument ('--defaults-file='+$iniPath)
    if(-not $state.Initialized) {
        if(@(Get-ChildItem -LiteralPath $databaseDirectory -Force).Count) {throw '检测到上次初始化留下的数据，请保留data目录后排查，不会自动删除重建。'}
        Write-Host '首次运行：正在创建本地数据库，可能需要一至两分钟……'
        $initialization=Start-Process -FilePath $mysqld -ArgumentList @($defaults,'--initialize-insecure','--console') -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'mysql-init.log') -RedirectStandardError (Join-Path $logDirectory 'mysql-init-error.log')
        if($initialization.ExitCode -ne 0) {throw '数据库初始化失败，请查看logs/mysql-init-error.log。'}
        $state.Initialized=$true; SaveState
    } elseif(-not (Test-Path -LiteralPath (Join-Path $databaseDirectory 'mysql.ibd'))) {
        throw '本地数据库文件缺失，已停止启动。请恢复完整data目录。'
    }
    # 由启动器统一管理生命周期，避免Windows监控子进程重新解析含空格的路径。
    $databaseArguments=@($defaults,'--no-monitor','--console')
    if(-not $state.Provisioned) {
        $bootstrap="ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`nCREATE USER IF NOT EXISTS 'parts_trace_app'@'127.0.0.1' IDENTIFIED BY '$applicationPassword';`nGRANT ALL PRIVILEGES ON parts_trace.* TO 'parts_trace_app'@'127.0.0.1';`nCREATE USER IF NOT EXISTS 'parts_trace_shutdown'@'127.0.0.1' IDENTIFIED BY '$rootPassword';`nGRANT SHUTDOWN ON *.* TO 'parts_trace_shutdown'@'127.0.0.1';`n"
        [IO.File]::WriteAllText($bootstrapPath,$bootstrap,(New-Object Text.UTF8Encoding($false)))
        $databaseArguments+=(QuoteArgument ('--init-file='+$bootstrapPath))
    }
    Write-Host ('正在启动独立数据库，端口：'+$databasePort)
    $databaseProcess=Start-Process -FilePath $mysqld -ArgumentList $databaseArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'mysql-console.log') -RedirectStandardError (Join-Path $logDirectory 'mysql-console-error.log')
    $deadline=[DateTime]::UtcNow.AddMinutes(2)
    while([DateTime]::UtcNow -lt $deadline) {
        if(Test-Path -LiteralPath $stopPath) {throw '收到停止请求。'}
        if($databaseProcess.HasExited) {throw '数据库进程启动失败，请查看logs/mysql-console-error.log。'}
        if(PortOccupied $databasePort) {
            $probe=DatabaseCommand 'probe'
            if($probe.Code -eq 0) {$databaseOwned=$true;break}
        }
        Start-Sleep -Milliseconds 500
    }
    if(-not $databaseOwned) {throw '无法确认私有数据库已就绪，请查看logs/mysql-console-error.log。'}
    if(Test-Path -LiteralPath $bootstrapPath) {Remove-Item -LiteralPath $bootstrapPath -Force}
    $prepared=DatabaseCommand 'prepare'
    if($prepared.Code -ne 0) {throw ($prepared.Lines -join "`n")}
    CheckSchema
    $state.Provisioned=$true; SaveState
    Write-Host '数据库与初始数据已就绪。'
    $javaArguments=@('-Dfile.encoding=UTF-8','-jar',(QuoteArgument $jar),'--parts.trace.desktop.enabled=true','--server.address=127.0.0.1',('--server.port='+$serverPort))
    if($config.additionalConfig) {
        $additional=[string]$config.additionalConfig
        if(-not [IO.Path]::IsPathRooted($additional)) {$additional=Join-Path $packageDirectory $additional}
        if(-not (Test-Path -LiteralPath $additional -PathType Leaf)) {throw 'additionalConfig指定的文件不存在。'}
        $javaArguments+=(QuoteArgument ('--spring.config.additional-location='+([Uri]([IO.Path]::GetFullPath($additional))).AbsoluteUri))
    }
    $env:PARTS_TRACE_PRIVATE_ROOT_PASSWORD=$null
    $applicationProcess=Start-Process -FilePath $java -ArgumentList $javaArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'console.log') -RedirectStandardError (Join-Path $logDirectory 'error.log')
    $url='http://127.0.0.1:'+$serverPort
    $deadline=[DateTime]::UtcNow.AddMinutes(3); $ready=$false
    Write-Host '正在启动平台……'
    while([DateTime]::UtcNow -lt $deadline) {
        if(Test-Path -LiteralPath $stopPath) {break}
        if($applicationProcess.HasExited) {throw '应用启动失败，请查看logs/console.log与logs/error.log。'}
        try {if((Invoke-RestMethod ($url+'/actuator/health') -TimeoutSec 2).status -eq 'UP') {$ready=$true;break}} catch {}
        Start-Sleep -Milliseconds 500
    }
    if(-not $ready -and -not (Test-Path -LiteralPath $stopPath)) {throw '应用未在三分钟内就绪，请查看logs目录。'}
    if($ready) {
        Write-Host ('平台已启动：'+$url+'/login')
        Write-Host '初始管理员：admin / admin123。停止时请双击“停止溯源平台.cmd”。'
        if(-not $NoBrowser) {Start-Process ($url+'/login') | Out-Null}
        while(-not $applicationProcess.HasExited -and -not (Test-Path -LiteralPath $stopPath)) {Start-Sleep -Milliseconds 500}
        if($applicationProcess.HasExited -and $applicationProcess.ExitCode -ne 0) {throw '应用意外退出，请查看logs目录。'}
    }
} catch {
    Write-Host ('启动未完成：'+$_.Exception.Message) -ForegroundColor Red
    $failed=$true
} finally {
    if($applicationProcess -and -not $applicationProcess.HasExited) {Stop-Process -Id $applicationProcess.Id -ErrorAction SilentlyContinue}
    if($databaseOwned) {
        Write-Host '正在保存并停止本地数据库……'
        $env:PARTS_TRACE_PRIVATE_ROOT_PASSWORD=$rootPassword
        $shutdown=DatabaseCommand 'shutdown'
        if($shutdown.Code -eq 0) {
            $deadline=[DateTime]::UtcNow.AddSeconds(30)
            while((PortOccupied $databasePort) -and [DateTime]::UtcNow -lt $deadline) {Start-Sleep -Milliseconds 300}
            if(-not (PortOccupied $databasePort)) {Write-Host '本地数据库已停止。'} else {$failed=$true;Write-Host '数据库未及时停止，请保留data目录。'}
        } else {
            $shutdown.Lines | Set-Content -LiteralPath (Join-Path $logDirectory 'mysql-shutdown-error.log') -Encoding UTF8
            $failed=$true;Write-Host '数据库停止未完成，请保留data目录并查看logs/mysql-shutdown-error.log。'
        }
    }
    if(Test-Path -LiteralPath $bootstrapPath) {Remove-Item -LiteralPath $bootstrapPath -Force -ErrorAction SilentlyContinue}
    Set-Location -LiteralPath $oldDirectory
}
if($failed) {exit 1}
