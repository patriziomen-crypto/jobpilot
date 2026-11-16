# Imposta, per la sessione PowerShell corrente, JDK 25 e Node 26 installati nella cartella utente
# (C:\Users\<utente>\.jdks e C:\Users\<utente>\.node). Uso:  . .\scripts\dev-env.ps1
$jdk  = Get-ChildItem "$env:USERPROFILE\.jdks"  -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
$node = Get-ChildItem "$env:USERPROFILE\.node"  -Directory -Filter "node-v26*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1

if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$($jdk.FullName)\bin;$env:Path"
    Write-Host "JAVA_HOME = $($jdk.FullName)"
} else {
    Write-Warning "JDK 25 non trovato in $env:USERPROFILE\.jdks"
}

if ($node) {
    $env:Path = "$($node.FullName);$env:Path"
    Write-Host "Node = $($node.FullName)"
} else {
    Write-Warning "Node 26 non trovato in $env:USERPROFILE\.node"
}

java -version
node --version
