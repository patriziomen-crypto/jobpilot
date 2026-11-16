# Installa MySQL Server 9.7 (LTS) accanto a MySQL 8.0, come servizio Windows "MySQL97" sulla porta 3307.
# Va eseguito come amministratore. Riutilizzabile: se un passaggio e' gia' fatto lo salta.
$ErrorActionPreference = "Stop"

$zip      = "C:\Users\patri\Downloads\mysql-9.7.4-winx64.zip"
$baseDir  = "C:\Program Files\MySQL\MySQL Server 9.7"
$dataRoot = "C:\ProgramData\MySQL\MySQL Server 9.7"
$dataDir  = "$dataRoot\Data"
$iniFile  = "$dataRoot\my.ini"
$service  = "MySQL97"
$port     = 3307
$log      = "$PSScriptRoot\setup-mysql97.log"

Start-Transcript -Path $log -Append | Out-Null

# 1. Estrazione dell'archivio in Program Files
if (-not (Test-Path "$baseDir\bin\mysqld.exe")) {
    Write-Host "Estraggo $zip ..."
    $tmp = "C:\Program Files\MySQL\_estrazione_97"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $baseDir
    Remove-Item $tmp -Recurse -Force
    Write-Host "Estratto in $baseDir"
} else {
    Write-Host "MySQL 9.7 gia' presente in $baseDir"
}

# 2. File di configurazione
New-Item -ItemType Directory -Force -Path $dataRoot | Out-Null
if (-not (Test-Path $iniFile)) {
    $ini = @"
[mysqld]
basedir=$($baseDir -replace '\\','/')
datadir=$($dataDir -replace '\\','/')
port=$port
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+01:00

[client]
port=$port
"@
    [System.IO.File]::WriteAllText($iniFile, $ini, (New-Object System.Text.UTF8Encoding $false))
    Write-Host "Scritto $iniFile"
}

# 3. Inizializzazione della cartella dati (root senza password: la impostiamo subito dopo)
if (-not (Test-Path "$dataDir\mysql")) {
    Write-Host "Inizializzo la cartella dati ..."
    & "$baseDir\bin\mysqld.exe" --defaults-file="$iniFile" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) { throw "mysqld --initialize-insecure fallito (codice $LASTEXITCODE)" }
}

# 4. Servizio Windows
if (-not (Get-Service -Name $service -ErrorAction SilentlyContinue)) {
    Write-Host "Registro il servizio $service ..."
    & "$baseDir\bin\mysqld.exe" --install $service --defaults-file="$iniFile"
    if ($LASTEXITCODE -ne 0) { throw "mysqld --install fallito (codice $LASTEXITCODE)" }
    Set-Service -Name $service -StartupType Automatic
}
Start-Service -Name $service
Write-Host "Servizio $service avviato sulla porta $port"

# 5. Password di root, database e utente dell'app (solo al primo avvio, quando root e' senza password)
$sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY 'Root97!JobPilot';
CREATE DATABASE IF NOT EXISTS jobpilot CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jobpilot'@'localhost' IDENTIFIED BY 'jobpilot';
GRANT ALL PRIVILEGES ON jobpilot.* TO 'jobpilot'@'localhost';
FLUSH PRIVILEGES;
"@
# mysql.exe scrive avvisi su stderr: in PowerShell 5.1 con ErrorActionPreference=Stop verrebbero
# trattati come errori, quindi per questo blocco lo disattiviamo e passiamo da cmd.exe.
$ErrorActionPreference = "Continue"
$mysql = "$baseDir\bin\mysql.exe"
$sqlFile = "$env:TEMP\jobpilot-init.sql"
[System.IO.File]::WriteAllText($sqlFile, $sql, (New-Object System.Text.UTF8Encoding $false))
$ok = $false
for ($i = 0; $i -lt 15 -and -not $ok; $i++) {
    Start-Sleep -Seconds 2
    cmd /c "`"$mysql`" --port=$port --host=127.0.0.1 --user=root --skip-password < `"$sqlFile`" 2>nul"
    if ($LASTEXITCODE -eq 0) { $ok = $true }
}
Remove-Item $sqlFile -ErrorAction SilentlyContinue
if ($ok) {
    Write-Host "Database 'jobpilot' e utente 'jobpilot' creati; password di root impostata."
} else {
    Write-Host "Nota: root ha gia' una password (configurazione gia' fatta) oppure il server non ha risposto in tempo."
}

$env:MYSQL_PWD = "jobpilot"
cmd /c "`"$mysql`" --port=$port --host=127.0.0.1 --user=jobpilot -e `"SELECT VERSION() AS versione, DATABASE() AS db;`" jobpilot 2>nul"
Stop-Transcript | Out-Null
