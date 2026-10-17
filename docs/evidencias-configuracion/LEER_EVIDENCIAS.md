# Verificacion del laboratorio
Entorno: Linux temporal, Temurin 21.0.12.1, Maven 3.9.9 y MySQL Community Server 8.4.6. Se utilizo MySQL en 127.0.0.1:3307 y HTTP en 8085/8086 para la verificacion. Los valores habituales del README siguen siendo 3306 y 8080.

Resultados: 7 pruebas originales y 11 de la version modificada, sin fallos, errores ni omitidas. clean verify termino con BUILD SUCCESS en ambas copias. Dev y prod respondieron HTTP 200 en 8 rutas por perfil. V1 y V2 aplicadas; el usuario de pruebas no accede a desarrollo. Arbol: 7 dependencias directas y 65 transitivas.

Los registros contienen las salidas efectivas, no capturas simuladas. Se suprimen las claves temporales cuando aparecen. Los tiempos Maven corresponden a compilacion/pruebas, no al tiempo de incorporacion de una persona.

Limitaciones: no se ejecuto una clonacion del repositorio privado ni los scripts BAT/PowerShell en Windows; no se verificaron los equipos individuales, PR, tags o configuracion de alertas de GitHub. La comprobacion HTTP no sustituye una prueba visual del navegador. Flyway emite un aviso de compatibilidad con MySQL 8.4 que queda en el backlog.
