[CmdletBinding()]
param(
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$MavenPath='mvn.cmd',
    [string]$NodePath='node.exe',
    [string]$GoPath='go.exe',
    [string]$MavenRepository,
    [switch]$Offline,
    [switch]$Standalone,
    [string]$MySqlHome,
    [string]$JreArchive,
    [string]$JreSha256
)
$ErrorActionPreference='Stop'
$backendDirectory=Split-Path -Parent $PSScriptRoot
$workspaceDirectory=Split-Path -Parent $backendDirectory
$frontendDirectory=Join-Path $workspaceDirectory 'parts-trace-frontend'
$buildDirectory=Join-Path $backendDirectory 'target/windows-package'
$packageName=if($Standalone){'工业零部件可信溯源平台-Windows-x64-独立版'}else{'工业零部件可信溯源平台-Windows-x64'}
$stageDirectory=Join-Path $buildDirectory $packageName
$outputDirectory=Join-Path $workspaceDirectory 'output'
$softwareDirectory=Join-Path $outputDirectory $(if($Standalone){'standalone'}else{'software'})
$environmentBackup=@{}
foreach($name in @('JAVA_HOME','PATH','GOOS','GOARCH','CGO_ENABLED','GO111MODULE','GOTELEMETRY','GOCACHE')){$environmentBackup[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$oldDirectory=Get-Location
function RunChecked([string]$program,[string[]]$arguments) {
    $savedPreference=$ErrorActionPreference
    try {$ErrorActionPreference='Continue';& $program @arguments;$commandExit=$LASTEXITCODE}
    finally {$ErrorActionPreference=$savedPreference}
    if($commandExit -ne 0){throw ('构建命令失败：'+$program+'，退出码：'+$commandExit)}
}
try {
    if($Standalone) {
        if(-not (Test-Path -LiteralPath (Join-Path $MySqlHome 'bin/mysqld.exe'))) {throw '独立版需用MySqlHome指定MySQL 8.0解压目录。'}
        if(-not (Test-Path -LiteralPath $JreArchive -PathType Leaf) -or $JreSha256 -notmatch '^[a-fA-F0-9]{64}$') {throw '独立版需指定官方Java 11 JRE ZIP及SHA256。'}
        if((Get-FileHash -LiteralPath $JreArchive -Algorithm SHA256).Hash -ne $JreSha256) {throw 'JRE ZIP校验不一致。'}
        $JreArchive=(Resolve-Path -LiteralPath $JreArchive).Path
        $MySqlHome=(Resolve-Path -LiteralPath $MySqlHome).Path
    }
    if(-not $JavaHome){$JavaHome=Split-Path -Parent (Split-Path -Parent (Get-Command java.exe -ErrorAction Stop).Source)}
    $env:JAVA_HOME=$JavaHome
    $resolvedNode=(Get-Command $NodePath -ErrorAction Stop).Source
    $resolvedGo=(Get-Command $GoPath -ErrorAction Stop).Source
    $resolvedMaven=(Get-Command $MavenPath -ErrorAction Stop).Source
    $env:PATH=(Join-Path $JavaHome 'bin')+';'+(Split-Path -Parent $resolvedNode)+';'+$env:PATH
    if(Test-Path -LiteralPath $stageDirectory){
        $resolvedStage=[IO.Path]::GetFullPath($stageDirectory)
        $expectedParent=[IO.Path]::GetFullPath($buildDirectory)
        if((Split-Path -Parent $resolvedStage) -ne $expectedParent -or -not $resolvedStage.StartsWith([IO.Path]::GetFullPath((Join-Path $backendDirectory 'target'))+[IO.Path]::DirectorySeparatorChar)){throw '临时打包目录不在后端target中'}
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
    New-Item -ItemType Directory -Path $stageDirectory -Force | Out-Null
    Set-Location -LiteralPath $frontendDirectory
    if(-not (Test-Path -LiteralPath 'node_modules/vite/bin/vite.js')){RunChecked (Join-Path (Split-Path -Parent $resolvedNode) 'npm.cmd') @('ci')}
    RunChecked $resolvedNode @('node_modules/vite/bin/vite.js','build')
    Set-Location -LiteralPath $backendDirectory
    $mavenArguments=@('-Pwindows-package','-Dparts.build.directory=target/windows-package-build','package')
    if($Offline){$mavenArguments=@('-o')+$mavenArguments}
    if($MavenRepository){$mavenArguments+=('-Dmaven.repo.local='+$MavenRepository)}
    RunChecked $resolvedMaven $mavenArguments
    Copy-Item -LiteralPath (Join-Path $backendDirectory 'target/windows-package-build/parts-trace-backend-1.0-SNAPSHOT.jar') -Destination (Join-Path $stageDirectory 'parts-trace-backend.jar')
    $env:GOOS='windows';$env:GOARCH='amd64';$env:CGO_ENABLED='0';$env:GO111MODULE='off';$env:GOTELEMETRY='off';$env:GOCACHE=Join-Path $backendDirectory 'target/go-cache'
    $launcherPath=Join-Path $stageDirectory '启动溯源平台.exe'
    RunChecked $resolvedGo @('build','-trimpath','-buildvcs=false','-ldflags=-s -w','-o',$launcherPath,(Join-Path $backendDirectory 'packaging/windows/launcher.go'))
    foreach($name in @('scripts','config','db','licenses')){New-Item -ItemType Directory -Path (Join-Path $stageDirectory $name) -Force | Out-Null}
    Copy-Item -LiteralPath (Join-Path $backendDirectory 'packaging/windows/start-windows.ps1') -Destination (Join-Path $stageDirectory 'scripts/start-windows.ps1')
    Copy-Item -LiteralPath (Join-Path $backendDirectory 'packaging/windows/runtime.example.json') -Destination (Join-Path $stageDirectory 'config/runtime.json')
    $guide=if($Standalone){'docs/Windows独立运行包说明.md'}else{'docs/Windows运行包说明.md'}
    Copy-Item -LiteralPath (Join-Path $backendDirectory $guide) -Destination (Join-Path $stageDirectory 'Windows运行说明.md')
    foreach($name in @('init.sql','cross_chain_migration.sql','V20261025__security_completion.sql','V20261026__relay_confirmation.sql')){
        Copy-Item -LiteralPath (Join-Path $backendDirectory ('src/main/resources/db/'+$name)) -Destination (Join-Path $stageDirectory ('db/'+$name))
    }
    foreach($name in @('chainmaker','fisco','confirmation')){
        $configDirectory=Join-Path $stageDirectory ('config/'+$name)
        New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $backendDirectory ('src/main/resources/'+$name)) -Filter '*.example' | Copy-Item -Destination $configDirectory
    }
    $goRoot=Split-Path -Parent (Split-Path -Parent $resolvedGo)
    Copy-Item -LiteralPath (Join-Path $goRoot 'LICENSE') -Destination (Join-Path $stageDirectory 'licenses/Go-LICENSE.txt')
    if($Standalone) {
        Copy-Item -LiteralPath (Join-Path $backendDirectory 'packaging/windows/start-standalone.ps1') -Destination (Join-Path $stageDirectory 'scripts/start-standalone.ps1')
        Copy-Item -LiteralPath (Join-Path $backendDirectory 'packaging/windows/standalone.example.json') -Destination (Join-Path $stageDirectory 'config/runtime.json') -Force
        Copy-Item -LiteralPath (Join-Path $backendDirectory 'packaging/windows/stop-standalone.cmd') -Destination (Join-Path $stageDirectory '停止溯源平台.cmd')
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $extract=Join-Path $stageDirectory 'runtime/java-extract'
        [IO.Compression.ZipFile]::ExtractToDirectory($JreArchive,$extract)
        $jreRoot=@(Get-ChildItem -LiteralPath $extract -Directory)
        if($jreRoot.Count -ne 1 -or -not (Test-Path -LiteralPath (Join-Path $jreRoot[0].FullName 'bin/java.exe'))) {throw 'JRE ZIP目录结构不正确。'}
        Move-Item -LiteralPath $jreRoot[0].FullName -Destination (Join-Path $stageDirectory 'runtime/java')
        Remove-Item -LiteralPath $extract
        $mysqlDestination=Join-Path $stageDirectory 'runtime/mysql'
        New-Item -ItemType Directory -Path (Join-Path $mysqlDestination 'bin') -Force | Out-Null
        foreach($name in @('mysqld.exe','mysql.exe','mysqldump.exe','mysqladmin.exe')) {
            Copy-Item -LiteralPath (Join-Path $MySqlHome ('bin/'+$name)) -Destination (Join-Path $mysqlDestination 'bin')
        }
        Get-ChildItem -LiteralPath (Join-Path $MySqlHome 'bin') -Filter '*.dll' | Where-Object {$_.Name -notmatch 'debug'} | Copy-Item -Destination (Join-Path $mysqlDestination 'bin')
        foreach($name in @('share','lib')) {
            Get-ChildItem -LiteralPath (Join-Path $MySqlHome $name) -Recurse -File | Where-Object {$_.Name -notmatch '(?i)(debug|\.pdb$|\.lib$)'} | ForEach-Object {
                $relative=$_.FullName.Substring($MySqlHome.Length+1)
                $destination=Join-Path $mysqlDestination $relative
                New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
                Copy-Item -LiteralPath $_.FullName -Destination $destination
            }
        }
        foreach($name in @('LICENSE','README')) {Copy-Item -LiteralPath (Join-Path $MySqlHome $name) -Destination $mysqlDestination}
        # 使用Temurin分发包随附的CRT文件；保留整个JRE及legal目录中的许可。
        foreach($name in @('msvcp140.dll','vcruntime140.dll','vcruntime140_1.dll')) {
            Copy-Item -LiteralPath (Join-Path $stageDirectory ('runtime/java/bin/'+$name)) -Destination (Join-Path $mysqlDestination 'bin')
        }
        $runtimeInformation=[ordered]@{
            JavaArchive=(Split-Path -Leaf $JreArchive); JavaArchiveSHA256=$JreSha256.ToLowerInvariant()
            JavaSource='https://adoptium.net/temurin/releases/?version=11'; JavaSourceCode='https://github.com/adoptium/temurin11-binaries'
            MySQLVersion=(Get-Item -LiteralPath (Join-Path $MySqlHome 'bin/mysqld.exe')).VersionInfo.ProductVersion
            MySQLSourceCode='https://downloads.mysql.com/archives/community/'; MySQLLicense='runtime/mysql/LICENSE'
            JavaLicenses='runtime/java/legal'; DataIncluded=$false
        }
        $runtimeInformation | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stageDirectory 'licenses/运行环境来源.json') -Encoding UTF8
    }
    $manifest=@(Get-ChildItem -LiteralPath $stageDirectory -Recurse -File | Sort-Object FullName | ForEach-Object {
        [ordered]@{File=$_.FullName.Substring($stageDirectory.Length+1).Replace('\','/');Bytes=$_.Length;SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
    ConvertTo-Json -InputObject $manifest -Depth 5 | Set-Content -LiteralPath (Join-Path $stageDirectory 'Windows文件校验.json') -Encoding UTF8
    New-Item -ItemType Directory -Path $softwareDirectory -Force | Out-Null
    foreach($file in (Get-ChildItem -LiteralPath $stageDirectory -Recurse -File)){
        $relative=$file.FullName.Substring($stageDirectory.Length+1)
        $destination=Join-Path $softwareDirectory $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        if($relative -eq 'config\runtime.json' -and (Test-Path -LiteralPath $destination)){continue}
        Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
    }
    $zipPath=Join-Path $outputDirectory ($packageName+'.zip')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if(Test-Path -LiteralPath $zipPath){Remove-Item -LiteralPath $zipPath}
    [IO.Compression.ZipFile]::CreateFromDirectory($stageDirectory,$zipPath,[IO.Compression.CompressionLevel]::Optimal,$true)
    Write-Host ('可执行入口：'+(Join-Path $softwareDirectory '启动溯源平台.exe'))
    Write-Host ('运行包：'+$zipPath)
} finally {
    Set-Location -LiteralPath $oldDirectory
    foreach($name in $environmentBackup.Keys){[Environment]::SetEnvironmentVariable($name,$environmentBackup[$name],'Process')}
}
