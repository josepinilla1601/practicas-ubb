$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instala JDK 21 y abre una terminal nueva.' }
    if (-not (Test-Path '.env')) { throw 'Copia .env.example como .env y completa las claves. Consulta README.md.' }
    $jar = Join-Path $projectRoot 'ejecutable\practicas-ubb.jar'
    if (-not (Test-Path $jar)) { throw 'Primero ejecuta COMPILAR.bat.' }
    Write-Host 'Espera Started PracticasApplication y abre http://localhost:8080 (o APP_PORT).' -ForegroundColor Green
    & java '-jar' $jar '--spring.profiles.active=dev'
    if ($LASTEXITCODE -ne 0) { throw 'La aplicacion termino con error. Revisa la consola y README.md.' }
} finally { Pop-Location }
