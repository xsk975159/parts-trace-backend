[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$deploymentDirectory=Split-Path -Parent $PSScriptRoot
$statePath=Join-Path $deploymentDirectory 'runtime\network.json'
if(-not (Test-Path -LiteralPath $statePath)) {Write-Host '此目录尚未创建链网络。';exit 0}
$state=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
if($state.directory -ne $deploymentDirectory -or $state.project -notmatch '^parts-trace-[0-9a-f]{10}$') {throw '网络记录与当前目录不符，未停止任何容器。'}
$dockerDirectory=Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin'
if(-not (Test-Path -LiteralPath (Join-Path $dockerDirectory 'docker.exe'))) {$dockerDirectory=Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin'}
if(Test-Path -LiteralPath (Join-Path $dockerDirectory 'docker.exe')) {$env:PATH=$dockerDirectory+';'+$env:PATH}
$ErrorActionPreference='Continue'
& docker.exe --context desktop-linux compose -p $state.project -f (Join-Path $deploymentDirectory 'compose.yaml') stop
$code=$LASTEXITCODE
if($code -ne 0) {exit $code}
Write-Host '当前项目的链节点已停止。账本、证书和业务数据库均已保留。'
