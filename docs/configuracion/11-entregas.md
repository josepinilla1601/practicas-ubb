# Cierre del laboratorio y etiquetas
1. Publicar la rama: git push -u origin lab/configuracion.
2. Crear en GitHub un pull request hacia main y asignar a otro integrante. Adjuntar las evidencias reales y copiar la URL del PR en el laboratorio. No marcar revisado antes de recibir su revision.
3. El revisor ejecuta verify y comprueba las cinco HU. Una vez aprobado, integrar el PR.
4. Identificar el commit del Incremento 1. En la captura previa figura 3555d21; comprobar su contenido antes de etiquetarlo.
   git tag -a v1.0.0 3555d21 -m "Incremento 1 - seguimiento de practicas"
   git push origin v1.0.0
5. Tras integrar el laboratorio, si se entrega como parche del mismo incremento, cambiar la version del proyecto a 1.0.1 con Maven versions:set -DnewVersion=1.0.1 -DgenerateBackupPoms=false. Actualizar tambien COMPILAR.bat al nuevo nombre del JAR, verificar y registrar ese cambio en un commit.
6. Crear v1.0.1 sobre ese commit verificado y publicarla. Si el equipo lo considera un nuevo incremento funcional, acordar su numero de version antes de cambiarlo.
7. Verificar git tag -n y la seccion Tags en GitHub. No recrear etiquetas ya publicadas sobre otro commit.
