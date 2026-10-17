# Revision de dependencias
El pom tiene 7 dependencias directas. No eliminar starters o Flyway porque dependency:analyze los marque como no usados: parte de su uso es por autoconfiguracion.
Ejecutar .\mvnw.cmd dependency:tree -DoutputFile=arbol-final.txt y .\mvnw.cmd dependency:analyze. Las transitivas se cuentan como coordenadas unicas resueltas, excluyendo la raiz y las 7 directas; incluir scope test.
Activar Dependabot alerts y security updates en Settings de GitHub. El YAML prepara actualizaciones semanales, pero no acredita que las alertas de la cuenta esten activadas.
Regla propuesta para adoptar en la revision: no integrar PR de Dependabot sin .\mvnw.cmd verify exitoso y revision de otro integrante. No actualizar Spring Boot de version mayor automaticamente.
