$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
  $out = 'docs/evidencias-configuracion'
  New-Item -ItemType Directory -Force $out | Out-Null
  & java -version 2>&1 | Out-File "$out/java.txt" -Encoding utf8
  & .\mvnw.cmd -v 2>&1 | Out-File "$out/maven.txt" -Encoding utf8
  & .\mvnw.cmd dependency:tree "-DoutputFile=$out/arbol-final.txt"
  if ($LASTEXITCODE -ne 0) { throw 'No se pudo obtener el arbol.' }
  & .\mvnw.cmd verify 2>&1 | Tee-Object -FilePath "$out/verify.txt"
  if ($LASTEXITCODE -ne 0) { throw 'verify fallo. No registrar las pruebas como aprobadas.' }
  & git ls-files mvnw mvnw.cmd .mvn/wrapper | Out-File "$out/wrapper.txt" -Encoding utf8
  & git check-ignore .env docs/crear-base.local.sql | Out-File "$out/ignorados.txt" -Encoding utf8
  & git tag -n | Out-File "$out/etiquetas.txt" -Encoding utf8
  Write-Host 'Revisar evidencias. Faltan salidas de perfiles, historial Flyway y prueba de clon limpio por otro integrante.'
} finally { Pop-Location }
