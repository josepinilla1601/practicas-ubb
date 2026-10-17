$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'No se encuentra Java. Instala JDK 21 y abre una ventana nueva.' }
    $jar = Join-Path $projectRoot 'ejecutable\practicas-ubb.jar'
    if (-not (Test-Path $jar)) { throw 'Falta ejecutable\practicas-ubb.jar. Extrae todo el ZIP o ejecuta COMPILAR.bat.' }
    Write-Host 'SEGUIMIENTO DE PRACTICAS - SPRINT 1' -ForegroundColor Cyan
    $dbLogin = Read-Host 'Usuario MySQL [Enter = root]'
    if ([string]::IsNullOrWhiteSpace($dbLogin)) { $dbLogin = 'root' }
    $dbPort = Read-Host 'Puerto MySQL [Enter = 3306]'
    if ([string]::IsNullOrWhiteSpace($dbPort)) { $dbPort = '3306' }
    $portNumber = 0
    if (-not [int]::TryParse($dbPort, [ref]$portNumber) -or $portNumber -lt 1 -or $portNumber -gt 65535) { throw 'El puerto debe ser un numero entre 1 y 65535.' }
    $secret = Read-Host 'Contrasena de MySQL' -AsSecureString
    $env:DB_USER = $dbLogin
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$portNumber/practicas_ubb?createDatabaseIfNotExist=true&serverTimezone=UTC&allowPublicKeyRetrieval=true&sslMode=DISABLED"
    $webPort = if ($env:APP_PORT) { $env:APP_PORT } else { '8080' }
    Write-Host "Cuando aparezca Started PracticasApplication, abre http://localhost:$webPort" -ForegroundColor Green
    Write-Host 'Mantiene esta ventana abierta. Para detener la aplicacion: Ctrl+C.'
    & java '-jar' $jar
    if ($LASTEXITCODE -ne 0) { Write-Host 'La aplicacion termino con un error. Revisa el mensaje anterior y LEEME_PRIMERO.md.' -ForegroundColor Yellow }
} catch { Write-Host $_.Exception.Message -ForegroundColor Red }
finally { Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue }
