[CmdletBinding()]
param([string]$OutputDirectory)
$ErrorActionPreference='Stop'
$sourceDirectory=Split-Path -Parent $PSScriptRoot
if(-not $OutputDirectory) {$OutputDirectory=Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $sourceDirectory))) 'output/docker'}
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
if($OutputDirectory -eq $sourceDirectory) {throw '导出目录不能覆盖源码目录。'}
$dockerDirectory=Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'
if(-not (Test-Path (Join-Path $dockerDirectory 'docker.exe'))) {$dockerDirectory=Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin'}
if(Test-Path (Join-Path $dockerDirectory 'docker.exe')) {$env:PATH=$dockerDirectory+';'+$env:PATH}
$docker=(Get-Command docker.exe -ErrorAction Stop).Source
$images=@('parts-trace/chainmaker:2.3.10','parts-trace/fisco-bcos:3.16.3','parts-trace/chain-bootstrap:1.0','chainmakerofficial/chainmaker-vm-engine:v2.3.9')
foreach($image in $images) {& $docker --context desktop-linux image inspect $image --format '{{.Id}}'; if($LASTEXITCODE -ne 0){throw ('缺少镜像：'+$image)}}
New-Item -ItemType Directory -Force $OutputDirectory,(Join-Path $OutputDirectory 'scripts'),(Join-Path $OutputDirectory 'vendor') | Out-Null
foreach($name in @('start-chains.cmd','stop-chains.cmd','README.md')) {Copy-Item -LiteralPath (Join-Path $sourceDirectory $name) -Destination (Join-Path $OutputDirectory $name)}
foreach($name in @('start-chains.ps1','stop-chains.ps1')) {Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $OutputDirectory ('scripts/'+$name))}
foreach($name in @('README.md','FISCO-BCOS-LICENSE')) {Copy-Item -LiteralPath (Join-Path $sourceDirectory ('vendor/'+$name)) -Destination (Join-Path $OutputDirectory ('vendor/'+$name))}
$compose=[IO.File]::ReadAllText((Join-Path $sourceDirectory 'compose.yaml'),[Text.Encoding]::UTF8)
$compose=[regex]::Replace($compose,'(?m)^([ ]*)build:\r?\n\1  context: [^\r\n]+\r?\n\1  dockerfile: [^\r\n]+\r?\n','')
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'compose.yaml'),$compose,[Text.UTF8Encoding]::new($false))
$temporaryTar=Join-Path $OutputDirectory 'images.building.tar'
$temporaryGzip=Join-Path $OutputDirectory 'images.building.tar.gz'
& $docker --context desktop-linux save --output $temporaryTar @images
if($LASTEXITCODE -ne 0){throw '镜像导出失败。'}
$inputStream=[IO.File]::OpenRead($temporaryTar)
try {
    $outputStream=[IO.File]::Create($temporaryGzip)
    try {
        $gzip=[IO.Compression.GZipStream]::new($outputStream,[IO.Compression.CompressionLevel]::Optimal,$true)
        try {$inputStream.CopyTo($gzip)} finally {$gzip.Dispose()}
    } finally {$outputStream.Dispose()}
} finally {$inputStream.Dispose()}
Move-Item -LiteralPath $temporaryGzip -Destination (Join-Path $OutputDirectory 'images.tar.gz') -Force
Remove-Item -LiteralPath $temporaryTar
# Only explicitly listed distribution files are included. Local runtime credentials are excluded.
$files=@('compose.yaml','start-chains.cmd','stop-chains.cmd','README.md','scripts/start-chains.ps1','scripts/stop-chains.ps1','vendor/README.md','vendor/FISCO-BCOS-LICENSE','images.tar.gz')
if(Test-Path -LiteralPath (Join-Path $OutputDirectory '验证记录.json')) {$files+='验证记录.json'}
$checks=@(foreach($name in $files) {
    $path=Join-Path $OutputDirectory $name
    @{File=$name;Bytes=(Get-Item -LiteralPath $path).Length;SHA256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
})
[IO.File]::WriteAllText((Join-Path $OutputDirectory '文件校验.json'),(ConvertTo-Json -InputObject $checks -Depth 4),[Text.UTF8Encoding]::new($false))
$archive=Join-Path (Split-Path -Parent $OutputDirectory) '工业零部件可信溯源平台-异构链Docker环境.zip'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temporaryZip=$archive+'.building'
if(Test-Path -LiteralPath $temporaryZip) {throw '存在未完成的导出压缩包，请先核对 .building 文件。'}
$zip=[IO.Compression.ZipFile]::Open($temporaryZip,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach($name in ($files+@('文件校验.json'))) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $OutputDirectory $name),('docker/'+$name),[IO.Compression.CompressionLevel]::NoCompression) | Out-Null
    }
} finally {$zip.Dispose()}
Move-Item -LiteralPath $temporaryZip -Destination $archive -Force
Write-Output ('离线运行包：'+$archive)
