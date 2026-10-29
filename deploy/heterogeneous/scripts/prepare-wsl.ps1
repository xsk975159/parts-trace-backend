[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$WslInstaller,
      [Parameter(Mandatory=$true)][string]$ResultPath)
$ErrorActionPreference='Stop'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
$principal=New-Object Security.Principal.WindowsPrincipal($identity)
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$PSCommandPath+'"'),'-WslInstaller',('"'+$WslInstaller+'"'),'-ResultPath',('"'+$ResultPath+'"'))
    $process=Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -ArgumentList $arguments -Wait -PassThru
    exit $process.ExitCode
}
try {
    $signature=Get-AuthenticodeSignature -LiteralPath $WslInstaller
    if($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Microsoft Corporation') {throw 'WSL安装包的Microsoft签名不正确。'}
    $results=@()
    foreach($feature in @('Microsoft-Windows-Subsystem-Linux','VirtualMachinePlatform')) {
        $current=Get-WindowsOptionalFeature -Online -FeatureName $feature
        if($current.State -ne 'Enabled') {
            $enabled=Enable-WindowsOptionalFeature -Online -FeatureName $feature -All -NoRestart
            $results+=@{Feature=$feature;RestartNeeded=[bool]$enabled.RestartNeeded}
        } else {$results+=@{Feature=$feature;RestartNeeded=$false}}
    }
    $process=Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i',('"'+$WslInstaller+'"'),'/qn','/norestart') -WindowStyle Hidden -Wait -PassThru
    if($process.ExitCode -notin @(0,3010)) {throw ('WSL安装退出码：'+$process.ExitCode)}
    @{Success=$true;Features=$results;MsiExitCode=$process.ExitCode;RestartRequired=$true} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
} catch {
    @{Success=$false;Error=$_.Exception.Message} | ConvertTo-Json | Set-Content -LiteralPath $ResultPath -Encoding UTF8
    exit 1
}
