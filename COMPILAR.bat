@echo off
cd /d "%~dp0"
call mvnw.cmd -DskipTests clean package
if errorlevel 1 goto fallo
if not exist ejecutable mkdir ejecutable
copy /y target\practicas-ubb-1.0.0.jar ejecutable\practicas-ubb.jar
if errorlevel 1 goto fallo
echo Compilacion terminada. Ejecuta INICIAR.bat.
pause
exit /b 0
:fallo
echo No se completo la compilacion. Revisa los mensajes anteriores.
pause
exit /b 1
