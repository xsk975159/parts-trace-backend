[CmdletBinding()]
param([string]$ConfigPath, [switch]$CheckOnly, [switch]$NoBrowser)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$packageDirectory=Split-Path -Parent $PSScriptRoot
$ownedProcess=$null
$oldDirectory=Get-Location

function PlainText([Security.SecureString]$value) {
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
function ProtectValue([string]$value) {
    $secure=ConvertTo-SecureString -String $value -AsPlainText -Force
    try { return ConvertFrom-SecureString -SecureString $secure } finally { $secure.Dispose() }
}
function UnprotectValue([string]$value) {
    $secure=ConvertTo-SecureString -String $value
    try { return PlainText $secure } finally { $secure.Dispose() }
}
function RandomKey([int]$length) {
    $bytes=New-Object byte[] $length
    $generator=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes);return [Convert]::ToBase64String($bytes) } finally { $generator.Dispose() }
}
function ReadSecret([string]$prompt) {
    $secure=Read-Host $prompt -AsSecureString
    try { return PlainText $secure } finally { $secure.Dispose() }
}
function SetDefaultEnvironment([string]$name,[string]$value) {
    if([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))) {
        [Environment]::SetEnvironmentVariable($name,$value,'Process')
    }
}
function QuoteArgument([string]$value) {
    if($value.Contains('"')) { throw '启动路径或配置参数不能包含双引号。' }
    return '"'+$value+'"'
}

try {
    Set-Location -LiteralPath $packageDirectory
    if(-not $ConfigPath) { $ConfigPath=Join-Path $packageDirectory 'config/runtime.json' }
    $config=Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    SetDefaultEnvironment 'KEWEI_DBSERVER' ([string]$config.databaseHost)
    SetDefaultEnvironment 'KEWEI_DBPORT' ([string]$config.databasePort)
    SetDefaultEnvironment 'KEWEI_DBUSER' ([string]$config.databaseUser)
    SetDefaultEnvironment 'PARTS_TRACE_CHAIN_MODE' ([string]$config.chainMode)
    SetDefaultEnvironment 'PARTS_TRACE_OBJECT_MODE' ([string]$config.objectMode)
    $serverPort=[int]$config.serverPort
    $serverAddress=[string]$config.serverAddress
    if($serverPort -lt 1 -or $serverPort -gt 65535) { throw 'serverPort须为1至65535。' }
    if($serverAddress -notin @('127.0.0.1','0.0.0.0','localhost','::1','::')) { throw 'serverAddress请使用本机回环地址或通配监听地址。' }
    $javaPath=$null
    if($env:JAVA_HOME) {
        $candidate=Join-Path $env:JAVA_HOME 'bin/java.exe'
        if(Test-Path -LiteralPath $candidate -PathType Leaf) { $javaPath=$candidate }
    }
    if(-not $javaPath) { $javaCommand=Get-Command java.exe -ErrorAction SilentlyContinue;if($javaCommand){$javaPath=$javaCommand.Source} }
    if(-not $javaPath) { throw '未找到Java，请安装Java 8或Java 11，并设置JAVA_HOME或PATH。' }
    $oldPreference=$ErrorActionPreference
    try {$ErrorActionPreference='Continue';$javaVersion=(& $javaPath -version 2>&1 | Out-String)} finally {$ErrorActionPreference=$oldPreference}
    if($javaVersion -notmatch 'version "(?:1\.8\.|11\.)') { throw '该运行包已验证Java 8与Java 11，请配置对应版本。' }
    $jarPath=Join-Path $packageDirectory 'parts-trace-backend.jar'
    if(-not (Test-Path -LiteralPath $jarPath)) { throw '缺少parts-trace-backend.jar，请完整解压运行包。' }
    if(-not (Test-Path Env:KEWEI_DBPWD)) { $env:KEWEI_DBPWD=ReadSecret ('请输入MySQL账户 {0} 的密码' -f $env:KEWEI_DBUSER) }
    Write-Host '正在只读检查数据库与表结构……'
    $oldPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $databaseOutput=@(& $javaPath '-Dfile.encoding=UTF-8' '-Dloader.main=com.kewei.parts.infrastructure.desktop.DesktopDatabaseCheck' -cp $jarPath org.springframework.boot.loader.PropertiesLauncher 2>&1)
        $databaseExit=$LASTEXITCODE
    } finally { $ErrorActionPreference=$oldPreference }
    if($databaseExit -ne 0) { throw ('数据库检查失败。请检查config/runtime.json及数据库状态。'+($databaseOutput -join "`n")) }
    $encryptedLine=@($databaseOutput | Where-Object { [string]$_ -match '^ENCRYPTED_KEYS=\d+$' })
    if($encryptedLine.Count -ne 1) { throw '无法确认数据库密钥状态，未创建或替换应用密钥。' }
    $missing=@($databaseOutput | Where-Object { [string]$_ -match '^MISSING=' })
    if($missing.Count) { throw ('数据库需要升级：'+($missing -join ', ')+'。请先备份，再按说明执行db中的增量迁移，不能用init.sql覆盖现有库。') }
    $encryptedCount=[long](([string]$encryptedLine[0]).Substring(15))
    $secretsPath=if($env:PARTS_TRACE_SETTINGS_PATH){$env:PARTS_TRACE_SETTINGS_PATH}else{[string]$config.secretsFile}
    if(-not [IO.Path]::IsPathRooted($secretsPath)){$secretsPath=Join-Path $packageDirectory $secretsPath}
    $secretsPath=[IO.Path]::GetFullPath($secretsPath)
    $identity='{0}:{1}/parts_trace' -f $env:KEWEI_DBSERVER,$env:KEWEI_DBPORT
    $saved=$null
    if(Test-Path -LiteralPath $secretsPath) {
        $saved=Get-Content -LiteralPath $secretsPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if($saved.Database -and $saved.Database -ne $identity) { throw '密钥配置属于其他数据库，请为此环境单独设置secretsFile。' }
    }
    if([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $env:JWT_SECRET=if($saved -and $saved.JwtSecret){UnprotectValue $saved.JwtSecret}else{RandomKey 48}
    }
    if([string]::IsNullOrWhiteSpace($env:KEY_ENCRYPTION_KEY)) {
        if($saved -and $saved.EncryptionKey){$env:KEY_ENCRYPTION_KEY=UnprotectValue $saved.EncryptionKey}
        elseif($encryptedCount -gt 0){$env:KEY_ENCRYPTION_KEY=ReadSecret '数据库已有加密私钥，请输入原KEY_ENCRYPTION_KEY'}
        else{$env:KEY_ENCRYPTION_KEY=RandomKey 32}
    }
    if($env:JWT_SECRET.Length -lt 32) { throw 'JWT_SECRET至少需要32个字符。' }
    try {$keyBytes=[Convert]::FromBase64String($env:KEY_ENCRYPTION_KEY)} catch {throw '私钥加密主密钥须为32字节的Base64编码。'}
    if($keyBytes.Length -ne 32) { throw '私钥加密主密钥须为32字节。' }
    $secretsDirectory=Split-Path -Parent $secretsPath
    New-Item -ItemType Directory -Path $secretsDirectory -Force | Out-Null
    @{Version=1;Database=$identity;JwtSecret=(ProtectValue $env:JWT_SECRET);EncryptionKey=(ProtectValue $env:KEY_ENCRYPTION_KEY)} |
        ConvertTo-Json | Set-Content -LiteralPath $secretsPath -Encoding UTF8
    if($CheckOnly) { Write-Host '环境、数据库及应用密钥检查通过，未启动服务。';exit 0 }
    $probe=New-Object Net.Sockets.TcpClient
    try {
        $connect=$probe.BeginConnect('127.0.0.1',$serverPort,$null,$null)
        if($connect.AsyncWaitHandle.WaitOne(800) -and $probe.Connected){throw ('端口{0}已被占用，请关闭原服务或修改serverPort。' -f $serverPort)}
    } finally {$probe.Close()}
    $logDirectory=Join-Path $packageDirectory 'logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $javaArguments=@('-Dfile.encoding=UTF-8','-jar',(QuoteArgument $jarPath),'--parts.trace.desktop.enabled=true',('--server.address='+$serverAddress),('--server.port='+$serverPort))
    if($config.additionalConfig) {
        $additional=[string]$config.additionalConfig
        if(-not [IO.Path]::IsPathRooted($additional)){$additional=Join-Path $packageDirectory $additional}
        if(-not (Test-Path -LiteralPath $additional -PathType Leaf)){throw 'additionalConfig指定的外部配置文件不存在。'}
        $configUri=([Uri]([IO.Path]::GetFullPath($additional))).AbsoluteUri
        $javaArguments+=(QuoteArgument ('--spring.config.additional-location='+$configUri))
    }
    $ownedProcess=Start-Process -FilePath $javaPath -ArgumentList $javaArguments -WorkingDirectory $packageDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'console.log') -RedirectStandardError (Join-Path $logDirectory 'error.log')
    $browserHost=if($serverAddress -in @('::','::1')){'[::1]'}else{'127.0.0.1'}
    $url='http://{0}:{1}' -f $browserHost,$serverPort
    $ready=$false;$deadline=[DateTime]::UtcNow.AddMinutes(3)
    Write-Host '正在启动平台……'
    while([DateTime]::UtcNow -lt $deadline) {
        if($ownedProcess.HasExited){throw ('服务启动失败，请查看logs/error.log和logs/console.log，退出码：'+$ownedProcess.ExitCode)}
        try {
            $health=Invoke-RestMethod -Uri ($url+'/actuator/health') -TimeoutSec 2
            if($health.status -eq 'UP'){$ready=$true;break}
        } catch {}
        Start-Sleep -Milliseconds 500
    }
    if(-not $ready){throw '服务未在3分钟内就绪，请查看logs中的启动日志。'}
    Write-Host ('平台已启动：'+$url)
    Write-Host '按Ctrl+C或关闭此窗口停止本次启动的服务。'
    if(-not $NoBrowser){Start-Process -FilePath ($url+'/login') | Out-Null}
    while(-not $ownedProcess.HasExited){Start-Sleep -Milliseconds 500}
    if($ownedProcess.ExitCode -ne 0){throw ('服务已退出，退出码：'+$ownedProcess.ExitCode)}
} catch {
    Write-Host ('启动未完成：'+$_.Exception.Message) -ForegroundColor Red
    exit 1
} finally {
    if($null -ne $ownedProcess -and -not $ownedProcess.HasExited) {Stop-Process -Id $ownedProcess.Id -ErrorAction SilentlyContinue}
    Set-Location -LiteralPath $oldDirectory
}
