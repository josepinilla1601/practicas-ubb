@echo off
cd /d "%~dp0"
call mvnw.cmd clean verify
if errorlevel 1 goto fallo
if not exist ejecutable mkdir ejecutable
copy /y target\practicas-ubb-1.0.0.jar ejecutable\practicas-ubb.jar
if errorlevel 1 goto fallo
echo Compilacion y pruebas terminadas. Ejecuta INICIAR.bat.
pause
exit /b 0
:fallo
echo No se completo la verificacion. Revisa MySQL, .env y los mensajes anteriores.
pause
exit /b 1
