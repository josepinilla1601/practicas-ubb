# Seguimiento de practicas UBB

Sistema del Grupo 4 para registrar estudiantes, centros y practicas, adjuntar documentos y consultar los recibidos y pendientes. Backend Spring Boot, JDBC, Flyway y MySQL; interfaz HTML, CSS y JavaScript servida por el mismo backend.

## Requisitos
- JDK 21 LTS. Comprobar `java -version` y que JAVA_HOME apunte al JDK 21.
- MySQL Server 8.4.6 iniciado en el equipo local. Consultar `SELECT VERSION();` en Workbench.
- Git e Internet para la primera descarga. Maven 3.9.9 viene fijado por el Wrapper.
- No se requiere Node, npm, Docker ni Maven instalado globalmente.

## Preparacion desde cero en Windows PowerShell
```powershell
git clone https://github.com/josepinilla1601/practicas-ubb.git
cd practicas-ubb
Copy-Item .env.example .env
Copy-Item docs/crear-base.sql docs/crear-base.local.sql
```
El repositorio es privado: primero aceptar la invitacion de colaborador e iniciar sesion con una cuenta autorizada.
Editar `.env` y `docs/crear-base.local.sql` con dos claves privadas distintas que coincidan entre ambos archivos. Abrir el SQL local con MySQL Workbench y ejecutarlo una vez con una cuenta administradora. No modificar ni borrar las bases de datos existentes. Si los usuarios ya existen, conservar sus claves o cambiarlas deliberadamente con ALTER USER.
`.env` usa formato Java properties: no poner comillas alrededor de valores. Si una clave contiene barra inversa, escribirla doble. No subir `.env` ni el SQL local al repositorio.
Comprobar que `practicas_app` solo accede a `practicas_ubb` y `practicas_test` solo a `practicas_ubb_test`. Flyway crea tablas y catalogos al iniciar.

## Ejecutar
Desde la raiz del proyecto:
```powershell
.\mvnw.cmd -v
.\mvnw.cmd spring-boot:run
```
El perfil por defecto es dev. Esperar `Started PracticasApplication` y abrir http://localhost:8080. Para otro puerto, cambiar APP_PORT en `.env`. Mantener abierta la terminal; Ctrl+C detiene el servidor.
El frontend funciona en la misma direccion, sin otro comando. En Linux/macOS usar `./mvnw` en lugar de `.\mvnw.cmd`.

## Pruebas y compilacion
```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```
La suite contiene 7 pruebas de integracion y 4 unitarias. Las de integracion usan el perfil test y MySQL real; borran exclusivamente registros de la base de pruebas entre casos. Nunca asignar TEST_DB_URL a la base de desarrollo o produccion. El usuario de pruebas debe carecer de permisos sobre esas bases.
Para ejecutar solamente validaciones unitarias, sin MySQL: `.\mvnw.cmd -Dtest=SolicitudesTest test`. Esto no sustituye la verificacion completa.
Para compilar y copiar el JAR local ejecutar `COMPILAR.bat`. Primero deben pasar todas las pruebas. Luego `INICIAR.bat` inicia el JAR con dev y lee `.env`. No subir target ni el JAR a Git.

## Configuracion por entorno
`application.properties` contiene configuracion comun e importa opcionalmente `.env`. Los archivos dev, prod y test separan sus ajustes. Las variables del sistema tienen prioridad sobre el archivo.
Para probar el perfil prod localmente, usar otra terminal y exportar DB_URL, DB_USER y DB_PASSWORD con los valores de una base de validacion. Ejecutar:
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```
No ejecutar dev y prod simultaneamente en el mismo puerto. Este perfil no equivale a un despliegue productivo: antes de publicar faltan autenticacion, permisos, TLS y copias de seguridad. En un despliegue real no copiar el `.env` local; inyectar secretos del entorno y configurar DB_URL con TLS apropiado.

## Migraciones y actualizaciones
Conservar V1 y V2 intactas; cualquier cambio nuevo va en V3 o superior. Consultar flyway_schema_history para acreditar su ejecucion. No se usa Hibernate: ddl-auto no aplica.
Dependabot revisa Maven semanalmente cuando su configuracion llegue a la rama principal. Activar tambien alertas y security updates en GitHub. Antes de integrar una actualizacion: ejecutar verify y pedir revision a otro integrante.

## Evidencias y versiones
Ejecutar `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/evidencias-laboratorio.ps1`. Guarda salidas reales en docs/evidencias-configuracion. Revisarlas antes de subirlas; no contienen el archivo `.env`.
El Incremento 1 corresponde a la version 1.0.0. Las etiquetas se deben crear sobre el commit efectivamente entregado; ver docs/configuracion/11-entregas.md. No se considera creada una etiqueta por escribirla en este README.

## Errores habituales
- JDK incorrecto: abrir nueva terminal tras cambiar JAVA_HOME y confirmar ambas versiones.
- Access denied: revisar usuarios, claves privadas y permisos sobre la base correcta.
- Could not resolve placeholder: falta completar la variable indicada en `.env`.
- Unknown database: ejecutar antes el SQL de creacion con una cuenta administradora.
- No se descarga Maven o el parent: revisar conexion a Internet/proxy; no cambiar la version al azar.
- Checksum mismatch de Flyway: restaurar el script aplicado y crear una migracion nueva.

## Equipo
Jose Pinilla, Carla Vera, Matias Arriagada y Jesus Vivanco.
Ingenieria de Software. Profesora Danelys Brito.
