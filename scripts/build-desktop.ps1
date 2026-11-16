# Costruisce l'app desktop JobPilot:
#   1. compila il frontend Angular e lo copia dentro il backend (static/)
#   2. crea il JAR Spring Boot
#   3. con jpackage crea dist\JobPilot\JobPilot.exe (runtime Java incluso)
#   4. crea il collegamento "JobPilot" sul Desktop
# Uso: .\scripts\build-desktop.ps1   (dalla cartella del progetto)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\dev-env.ps1"

# Se JobPilot.exe e' in esecuzione tiene bloccati i file di dist\JobPilot: lo chiudiamo prima
$running = Get-Process -Name "JobPilot" -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "Chiudo JobPilot.exe in esecuzione (PID $($running.Id -join ', '))" -ForegroundColor Yellow
    $running | Stop-Process -Force
    Start-Sleep -Seconds 2
}

Write-Host "`n== 1/4 Frontend Angular (build di produzione)" -ForegroundColor Cyan
Push-Location "$root\frontend"
npx ng build --configuration production
if ($LASTEXITCODE -ne 0) { throw "ng build fallito" }
Pop-Location

$static = "$root\backend\src\main\resources\static"
if (Test-Path $static) { Remove-Item $static -Recurse -Force }
New-Item -ItemType Directory -Path $static | Out-Null
Copy-Item "$root\frontend\dist\frontend\browser\*" $static -Recurse
Write-Host "Frontend copiato in $static"

Write-Host "`n== 2/4 Backend Spring Boot (JAR)" -ForegroundColor Cyan
Push-Location "$root\backend"
.\mvnw.cmd -q -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "mvnw package fallito" }
Pop-Location
$jar = Get-ChildItem "$root\backend\target\jobpilot-*.jar" | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1
Write-Host "JAR: $($jar.FullName)"

Write-Host "`n== 3/4 jpackage -> dist\JobPilot\JobPilot.exe" -ForegroundColor Cyan
$dist = "$root\dist"
if (Test-Path "$dist\JobPilot") { Remove-Item "$dist\JobPilot" -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$input = "$root\backend\target\jpackage-input"
if (Test-Path $input) { Remove-Item $input -Recurse -Force }
New-Item -ItemType Directory -Path $input | Out-Null
Copy-Item $jar.FullName "$input\jobpilot.jar"

& "$env:JAVA_HOME\bin\jpackage.exe" `
    --type app-image `
    --name JobPilot `
    --app-version 1.0.0 `
    --vendor "Patrizio Menegoni" `
    --description "JobPilot - gestione candidature LinkedIn e Indeed" `
    --input $input `
    --main-jar jobpilot.jar `
    --icon "$root\backend\src\main\resources\icons\jobpilot.ico" `
    --dest $dist `
    --java-options "-Djobpilot.desktop=true" `
    --java-options "-Dspring.config.additional-location=optional:file:`$APPDIR/../config/" `
    --java-options "-Xmx512m"
if ($LASTEXITCODE -ne 0) { throw "jpackage fallito" }

# Cartella config accanto all'eseguibile: qui si può mettere application.yml con chiave Claude e database
New-Item -ItemType Directory -Force -Path "$dist\JobPilot\config" | Out-Null
Copy-Item "$root\backend\src\main\resources\application-local.example.yml" "$dist\JobPilot\config\application.example.yml"

Write-Host "`n== 4/4 Collegamento sul Desktop" -ForegroundColor Cyan
$desktop = [Environment]::GetFolderPath("Desktop")
$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut("$desktop\JobPilot.lnk")
$lnk.TargetPath = "$dist\JobPilot\JobPilot.exe"
$lnk.WorkingDirectory = "$dist\JobPilot"
$lnk.IconLocation = "$dist\JobPilot\JobPilot.exe,0"
$lnk.Description = "JobPilot - gestione candidature"
$lnk.Save()

Write-Host "`nFatto: $dist\JobPilot\JobPilot.exe  (collegamento: $desktop\JobPilot.lnk)" -ForegroundColor Green
