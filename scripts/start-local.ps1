[CmdletBinding()]
param(
    [string]$JarPath,
    [string]$SettingsPath,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$backendDirectory = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $backendDirectory 'target/parts-trace-backend-1.0-SNAPSHOT.jar'
}
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw '未找到后端JAR，请先在后端目录执行 mvn package。'
}
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path

function ConvertTo-PlainText([Security.SecureString]$Secret) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Protect-LocalSecret([string]$Value) {
    $secure = ConvertTo-SecureString -String $Value -AsPlainText -Force
    try { return ConvertFrom-SecureString -SecureString $secure }
    finally { $secure.Dispose() }
}

function Unprotect-LocalSecret([string]$Value) {
    $secure = ConvertTo-SecureString -String $Value
    try { return ConvertTo-PlainText $secure }
    finally { $secure.Dispose() }
}

function New-RandomKey([int]$Length) {
    $bytes = New-Object byte[] $Length
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes); return [Convert]::ToBase64String($bytes) }
    finally { $random.Dispose() }
}

$javaExecutable = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (Test-Path -LiteralPath $candidate) { $javaExecutable = $candidate }
}
if (-not $javaExecutable -and (Test-Path -LiteralPath 'D:\java11\bin\java.exe')) {
    $javaExecutable = 'D:\java11\bin\java.exe'
}
if (-not $javaExecutable) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $javaExecutable = $javaCommand.Source }
}
if (-not $javaExecutable) { throw '未找到Java，请先设置JAVA_HOME。' }

$mysqlCommand = Get-Command mysql.exe -ErrorAction SilentlyContinue
$mysqlExecutable = if ($mysqlCommand) { $mysqlCommand.Source } else { 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
if (-not (Test-Path -LiteralPath $mysqlExecutable)) {
    throw '未找到MySQL客户端，请把mysql.exe所在目录加入PATH后重试。'
}

if ([string]::IsNullOrWhiteSpace($env:KEWEI_DBSERVER)) { $env:KEWEI_DBSERVER = '127.0.0.1' }
if ([string]::IsNullOrWhiteSpace($env:KEWEI_DBPORT)) { $env:KEWEI_DBPORT = '3306' }
if ([string]::IsNullOrWhiteSpace($env:KEWEI_DBUSER)) { $env:KEWEI_DBUSER = 'root' }
if (-not (Test-Path Env:KEWEI_DBPWD)) {
    $databasePassword = Read-Host ('请输入MySQL账户 {0} 的密码（输入不显示）' -f $env:KEWEI_DBUSER) -AsSecureString
    try { $env:KEWEI_DBPWD = ConvertTo-PlainText $databasePassword }
    finally { $databasePassword.Dispose() }
}

# 只读取项目库，不创建或修改任何表；确认已有加密私钥后禁止自动生成替代主密钥。
$schemaSqlPath = Join-Path $PSScriptRoot 'check-schema.sql'
if (-not (Test-Path -LiteralPath $schemaSqlPath)) { throw '缺少scripts/check-schema.sql，请恢复该文件后重试。' }
$startupQueries = "SELECT COUNT(*) FROM sys_user_rsa_key WHERE private_key LIKE 'enc:v1:%';`n" + (Get-Content -LiteralPath $schemaSqlPath -Raw -Encoding UTF8)
$oldMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
$oldErrorPreference = $ErrorActionPreference
try {
    $env:MYSQL_PWD = $env:KEWEI_DBPWD
    $ErrorActionPreference = 'Continue'
    $databaseResult = @(& $mysqlExecutable --no-defaults --protocol=TCP "--host=$env:KEWEI_DBSERVER" "--port=$env:KEWEI_DBPORT" "--user=$env:KEWEI_DBUSER" --database=parts_trace --batch --skip-column-names --connect-timeout=10 "--execute=$startupQueries" 2>&1)
    $databaseExit = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $oldErrorPreference
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldMysqlPassword, 'Process')
}
if ($databaseExit -ne 0) {
    throw ('数据库连接或项目库检查失败，请核对账户、密码、parts_trace库及数据库升级状态。数据库返回：' + ($databaseResult -join "`n"))
}
$encryptedCount = 0L
if ($databaseResult.Count -lt 1 -or -not [long]::TryParse(([string]$databaseResult[0]).Trim(), [ref]$encryptedCount) -or $encryptedCount -lt 0) {
    throw '数据库状态无法确认，未创建或替换应用密钥。'
}
if ($databaseResult.Count -gt 1) {
    $missingSchema = $databaseResult | Select-Object -Skip 1
    throw ('数据库尚未完成升级，缺少：' + ($missingSchema -join ', ') + '。请先备份parts_trace，再依次执行src/main/resources/db/cross_chain_migration.sql和src/main/resources/db/V20261025__security_completion.sql，完成后重新启动。不要执行init.sql覆盖已有数据。')
}
Write-Host '数据库连接已确认。'

$privateDirectory = Join-Path $backendDirectory '.local'
$secretsFile = if ($SettingsPath) { [IO.Path]::GetFullPath($SettingsPath) } else { Join-Path $privateDirectory 'startup-secrets.json' }
$privateDirectory = Split-Path -Parent $secretsFile
$databaseIdentity = '{0}:{1}/parts_trace' -f $env:KEWEI_DBSERVER, $env:KEWEI_DBPORT
$savedSecrets = $null
if (Test-Path -LiteralPath $secretsFile) {
    try { $savedSecrets = Get-Content -LiteralPath $secretsFile -Raw | ConvertFrom-Json }
    catch { throw '本机密钥配置无法读取。请保留原文件，核对当前Windows账户；不要直接删除后重新生成。' }
    if ($savedSecrets.Database -and $savedSecrets.Database -ne $databaseIdentity) {
        throw '本机密钥配置属于另一个数据库地址，请用-SettingsPath指定该环境独立的配置文件。'
    }
}
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    if ($savedSecrets -and $savedSecrets.JwtSecret) { $env:JWT_SECRET = Unprotect-LocalSecret $savedSecrets.JwtSecret }
    else { $env:JWT_SECRET = New-RandomKey 48 }
}
if ([string]::IsNullOrWhiteSpace($env:KEY_ENCRYPTION_KEY)) {
    if ($savedSecrets -and $savedSecrets.EncryptionKey) {
        $env:KEY_ENCRYPTION_KEY = Unprotect-LocalSecret $savedSecrets.EncryptionKey
    }
    elseif ($encryptedCount -gt 0) {
        $masterKey = Read-Host '数据库已有加密私钥，请输入原KEY_ENCRYPTION_KEY（不能用新密钥替代）' -AsSecureString
        try { $env:KEY_ENCRYPTION_KEY = ConvertTo-PlainText $masterKey }
        finally { $masterKey.Dispose() }
    }
    else { $env:KEY_ENCRYPTION_KEY = New-RandomKey 32 }
}
if ($env:JWT_SECRET.Length -lt 32) { throw 'JWT_SECRET至少需要32个字符。' }
try { $masterKeyBytes = [Convert]::FromBase64String($env:KEY_ENCRYPTION_KEY) }
catch { throw 'KEY_ENCRYPTION_KEY必须是32字节密钥的Base64编码。' }
if ($masterKeyBytes.Length -ne 32) { throw 'KEY_ENCRYPTION_KEY解码后必须为32字节。' }

# Windows DPAPI绑定当前用户和本机；文件不保存数据库密码，也不打印应用密钥。
if (-not (Test-Path -LiteralPath $privateDirectory)) { New-Item -ItemType Directory -Path $privateDirectory | Out-Null }
@{
    Version = 1
    Database = $databaseIdentity
    JwtSecret = Protect-LocalSecret $env:JWT_SECRET
    EncryptionKey = Protect-LocalSecret $env:KEY_ENCRYPTION_KEY
} | ConvertTo-Json | Set-Content -LiteralPath $secretsFile -Encoding UTF8

if ([string]::IsNullOrWhiteSpace($env:PARTS_TRACE_CHAIN_MODE)) { $env:PARTS_TRACE_CHAIN_MODE = 'db' }
if ([string]::IsNullOrWhiteSpace($env:PARTS_TRACE_OBJECT_MODE)) { $env:PARTS_TRACE_OBJECT_MODE = 'db' }
Write-Host ('应用密钥已就绪；本机加密配置保存在 {0}，请妥善保留。' -f $secretsFile)
if ($CheckOnly) { Write-Host '配置检查完成，未启动后端。'; return }
Push-Location $backendDirectory
try {
    & $javaExecutable -jar $JarPath
    if ($LASTEXITCODE -ne 0) { throw ('后端启动失败，退出码：' + $LASTEXITCODE + '；请查看 logs/parts-trace.log。') }
}
finally { Pop-Location }
