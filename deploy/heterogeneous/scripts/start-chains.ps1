[CmdletBinding()]
param([string]$ApplicationDirectory, [switch]$BuildImages, [switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$deploymentDirectory=Split-Path -Parent $PSScriptRoot
$runtimeDirectory=Join-Path $deploymentDirectory 'runtime'
$composePath=Join-Path $deploymentDirectory 'compose.yaml'
$dockerDirectory=Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin'
if(-not (Test-Path -LiteralPath (Join-Path $dockerDirectory 'docker.exe'))) {
    $dockerDirectory=Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin'
}
if(Test-Path -LiteralPath (Join-Path $dockerDirectory 'docker.exe')) {$env:PATH=$dockerDirectory+';'+$env:PATH}
$docker=(Get-Command docker.exe -ErrorAction Stop).Source
$encoding=[Text.UTF8Encoding]::new($false)

function RunDocker([string[]]$Arguments) {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & $docker --context desktop-linux @Arguments 2>&1 | ForEach-Object {
            if($_ -is [Management.Automation.ErrorRecord]) {Write-Host $_.Exception.Message}
            else {Write-Host $_}
        }
        $code=$LASTEXITCODE
    }
    finally {$ErrorActionPreference=$previousPreference}
    if($code -ne 0) {throw ('Docker命令失败，退出码：'+$code+'。请保留runtime目录和Docker数据卷。')}
}
function PrivateDirectory([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    $security=[IO.Directory]::GetAccessControl($Path,[Security.AccessControl.AccessControlSections]::Access)
    $security.SetAccessRuleProtection($true,$false)
    foreach($rule in $security.GetAccessRules($true,$false,[Security.Principal.SecurityIdentifier])) {$security.PurgeAccessRules($rule.IdentityReference)}
    foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,(New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
        $security.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))
    }
    [IO.Directory]::SetAccessControl($Path,$security)
}
function SaveText([string]$Path,[string]$Content) {[IO.File]::WriteAllText($Path,$Content,$encoding)}
function QuoteYaml([string]$Value) {return "'"+$Value.Replace("'","''")+"'"}

try {
    RunDocker @('version','--format','{{.Server.Os}}')
    $server=& $docker --context desktop-linux info --format '{{.OSType}}'
    if($LASTEXITCODE -ne 0 -or $server -ne 'linux') {throw '请先启动Docker Desktop，并使用Linux容器。'}
    PrivateDirectory $runtimeDirectory
    PrivateDirectory (Join-Path $runtimeDirectory 'sdk')
    $identity=[IO.Path]::GetFullPath($deploymentDirectory).TrimEnd('\').ToLowerInvariant()
    $sha=[Security.Cryptography.SHA256]::Create()
    try {$suffix=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($identity)))).Replace('-','').Substring(0,10).ToLowerInvariant()}
    finally {$sha.Dispose()}
    $project='parts-trace-'+$suffix
    $networkState=Join-Path $runtimeDirectory 'network.json'
    if(Test-Path -LiteralPath $networkState) {
        $previous=Get-Content -LiteralPath $networkState -Raw -Encoding UTF8 | ConvertFrom-Json
        if($previous.directory -ne $deploymentDirectory) {throw '部署目录已移动。请恢复原目录后启动，以保留原网络与数据卷。'}
        if([string]$previous.project -notmatch '^parts-trace-[a-f0-9]{10}$') {throw '网络标识格式不正确，请保留runtime目录后核对。'}
        $project=[string]$previous.project
    } else {SaveText $networkState (@{project=$project;directory=$deploymentDirectory} | ConvertTo-Json)}
    $composeArguments=@('compose','-p',$project,'-f',$composePath)
    RunDocker ($composeArguments+@('config','--quiet'))
    if($BuildImages) {
        RunDocker ($composeArguments+@('build','prepare','fisco-node1','cm-node1'))
        RunDocker ($composeArguments+@('pull','cm-vm1'))
    } elseif(Test-Path -LiteralPath (Join-Path $deploymentDirectory 'images.tar.gz')) {
        RunDocker @('load','-i',(Join-Path $deploymentDirectory 'images.tar.gz'))
    }
    foreach($image in @('parts-trace/chain-bootstrap:1.0','parts-trace/fisco-bcos:3.16.3',
                       'parts-trace/chainmaker:2.3.10','chainmakerofficial/chainmaker-vm-engine:v2.3.9')) {
        RunDocker @('image','inspect',$image,'--format','{{.RepoTags}}')
    }
    if(-not $VerifyOnly) {RunDocker ($composeArguments+@('--profile','tools','run','--rm','--no-deps','prepare'))}
    RunDocker ($composeArguments+@('up','-d','--no-build',
        'cm-node1','cm-node2','cm-node3','cm-node4','fisco-node1','fisco-node2','fisco-node3','fisco-node4'))
    $action=if($VerifyOnly){'verify'}else{'deploy'}
    RunDocker ($composeArguments+@('--profile','tools','run','--rm','--no-deps',$action))
    $sdkDirectory=(Join-Path $runtimeDirectory 'sdk').Replace('\','/')
    $verification=Get-Content -LiteralPath (Join-Path $sdkDirectory 'verification.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if(-not $verification.passed -or $verification.mode -ne 'real' -or @($verification.physicalLedgers).Count -ne 4) {throw '实链验收记录不完整。'}
    foreach($file in @(Get-ChildItem -LiteralPath $sdkDirectory -Recurse -File | Where-Object {$_.Extension -in @('.yml','.toml')})) {
        $content=[IO.File]::ReadAllText($file.FullName,[Text.Encoding]::UTF8)
        SaveText $file.FullName ($content.Replace('@SDK@',$sdkDirectory))
    }
    if(-not $ApplicationDirectory) {
        $candidates=@((Join-Path (Split-Path -Parent $deploymentDirectory) 'standalone'),
                      (Join-Path (Split-Path -Parent $deploymentDirectory) '工业零部件可信溯源平台-Windows-x64-独立版'),
                      (Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $deploymentDirectory))) 'output\standalone'))
        $ApplicationDirectory=$candidates | Where-Object {Test-Path -LiteralPath (Join-Path $_ 'scripts\start-standalone.ps1')} | Select-Object -First 1
    }
    if($ApplicationDirectory) {
        $ApplicationDirectory=(Resolve-Path -LiteralPath $ApplicationDirectory).Path
        $runtimeConfig=Join-Path $ApplicationDirectory 'config\runtime.json'
        if(-not (Test-Path -LiteralPath $runtimeConfig)) {throw '指定的独立版运行目录缺少config/runtime.json。'}
        $config=Get-Content -LiteralPath $runtimeConfig -Raw -Encoding UTF8 | ConvertFrom-Json
        $relay=Get-Content -LiteralPath (Join-Path $sdkDirectory 'relay.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $account=Get-Content -LiteralPath (Join-Path $sdkDirectory 'fisco-account.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $deployments=Get-Content -LiteralPath (Join-Path $sdkDirectory 'deployment.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $lines=@('blockchain:',('  relay-private-key: '+(QuoteYaml $relay.privateKey)),'  timeout-ms: 60000','  networks:')
        $cm=@{'main-regulator'='chain1';'sub-production'='chain2';'sub-quality'='chain3'}
        foreach($code in @('main-regulator','sub-production','sub-quality')) {
            $lines+=@(('    '+$code+':'),'      platform: CHAINMAKER',
                ('      sdk-config: '+(QuoteYaml ([Uri]([IO.Path]::GetFullPath((Join-Path $sdkDirectory ('cm/'+$cm[$code]+'.yml'))))).AbsoluteUri)),
                ('      expected-chain-id: '+$cm[$code]),'      contract-name: trace_anchor')
        }
        $lines+=@('    sub-logistics:','      platform: FISCO_BCOS','      expected-chain-id: chain0','      group: group0',
            ('      sdk-config: '+(QuoteYaml ([Uri]([IO.Path]::GetFullPath((Join-Path $sdkDirectory 'fisco.toml')))).AbsoluteUri)),
            ('      contract-address: '+(QuoteYaml $deployments.'sub-logistics'.contractAddress)),
            ('      expected-account: '+(QuoteYaml $account.address)))
        SaveText (Join-Path $sdkDirectory 'blockchain.yml') (($lines -join "`n")+"`n")
        $attachmentPath=Join-Path $runtimeDirectory 'application.json'
        if(Test-Path -LiteralPath $attachmentPath) {
            $attached=Get-Content -LiteralPath $attachmentPath -Raw -Encoding UTF8 | ConvertFrom-Json
            if($attached.directory -ne $ApplicationDirectory) {throw '本部署已接入另一个运行包，请先核对runtime/application.json。'}
            $priorConfig=[string]$attached.previousAdditionalConfig
        } else {
            $priorConfig=[string]$config.additionalConfig
            SaveText $attachmentPath (@{directory=$ApplicationDirectory;previousAdditionalConfig=$priorConfig;previousChainMode=$config.chainMode} | ConvertTo-Json)
            Copy-Item -LiteralPath $runtimeConfig -Destination (Join-Path $runtimeDirectory 'runtime-before-chains.json')
        }
        $imports=@()
        if($priorConfig -and $priorConfig -ne 'config/docker-chains.yml') {
            $priorPath=if([IO.Path]::IsPathRooted($priorConfig)){$priorConfig}else{Join-Path $ApplicationDirectory $priorConfig}
            if(-not (Test-Path -LiteralPath $priorPath -PathType Leaf)) {throw '原additionalConfig文件不存在，未切换应用配置。'}
            $imports+=('    - '+(QuoteYaml ([Uri]([IO.Path]::GetFullPath($priorPath))).AbsoluteUri))
        }
        $imports+=('    - '+(QuoteYaml ([Uri]([IO.Path]::GetFullPath((Join-Path $sdkDirectory 'blockchain.yml')))).AbsoluteUri))
        SaveText (Join-Path $ApplicationDirectory 'config\docker-chains.yml') ((@('spring:','  config:','    import:')+($imports | ForEach-Object {'  '+$_})) -join "`n")
        $config | Add-Member -NotePropertyName chainMode -NotePropertyValue 'real' -Force
        $config | Add-Member -NotePropertyName additionalConfig -NotePropertyValue 'config/docker-chains.yml' -Force
        SaveText ($runtimeConfig+'.tmp') ($config | ConvertTo-Json -Depth 30)
        Move-Item -LiteralPath ($runtimeConfig+'.tmp') -Destination $runtimeConfig -Force
        Write-Host '已接入独立运行包。请正常停止并重新启动“启动溯源平台.exe”，链模式为real。'
    } else {Write-Host '实链已启动。使用-ApplicationDirectory指定独立运行包目录可完成接入。'}
    Write-Host ('验收记录：'+(Join-Path $runtimeDirectory 'sdk\verification.json'))
    Write-Host '本机RPC端口：长安链12301、FISCO BCOS 20200。跨链采用授权中继签名；本网络未配置多机构联合核验服务。'
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
