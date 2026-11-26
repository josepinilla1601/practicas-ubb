# Despliegue en Pacheco

Sitio público: **https://pacheco.chillan.ubiobio.cl/bestpem/**

- Servidor: `pacheco.chillan.ubiobio.cl`, usuario `josepinilla1601`, acceso con clave SSH.
- Puerto asignado: **8019**. El proxy HTTPS de la universidad recibe `/bestpem/...` y lo envía **sin** `/bestpem` a ese puerto, por lo que la app corre en la raíz y el frontend usa rutas relativas.
- Todo corre con **Podman sin root** y **systemd de usuario** (Linger activo): arranca solo si se reinicia el servidor y se reinicia si se cae.

| Archivo | Qué hace |
|---|---|
| `bestpem.pod` | Pod que agrupa MySQL y la app; solo publica el puerto 8019 |
| `bestpem-db.container` | MySQL 8.4 con datos en el volumen `bestpem-mysql` |
| `bestpem-app.container` | Java 21 que ejecuta `~/bestpem/app.jar` |
| `instalar.sh` | Copia los servicios, genera contraseñas de MySQL la primera vez, activa el respaldo diario y reinicia |

En el servidor, `~/bestpem/db.env` y `~/bestpem/app.env` guardan las contraseñas (permisos 600). No se suben a Git.

## Actualizar la aplicación

En tu computador, desde la raíz del proyecto:

```bash
./mvnw -q -DskipTests package
scp target/practicas-ubb-1.0.0.jar josepinilla1601@pacheco.chillan.ubiobio.cl:bestpem/app.jar
ssh josepinilla1601@pacheco.chillan.ubiobio.cl 'systemctl --user restart bestpem-app'
```

## Revisar el estado

```bash
ssh josepinilla1601@pacheco.chillan.ubiobio.cl 'podman ps; podman logs --tail 50 bestpem-app'
```

## Respaldos de la base de datos

Cada noche a las 03:00 se guarda un respaldo comprimido en `~/bestpem/respaldos/` y se conservan los últimos 14 días.
Incluye todo: cuentas, prácticas, notas, bitácoras y también los archivos entregados (se guardan dentro de MySQL).

| Archivo | Qué hace |
|---|---|
| `respaldo.sh` | Genera el respaldo y borra los más antiguos |
| `bestpem-respaldo.service` / `.timer` | Lo ejecutan todos los días a las 03:00 |

Ver los respaldos y la próxima ejecución:

```bash
ssh josepinilla1601@pacheco.chillan.ubiobio.cl 'ls -lh ~/bestpem/respaldos; systemctl --user list-timers bestpem-respaldo.timer'
```

Hacer un respaldo ahora (por ejemplo, antes de actualizar):

```bash
ssh josepinilla1601@pacheco.chillan.ubiobio.cl 'bash ~/bestpem/respaldo.sh'
```

Descargar el último respaldo a tu computador:

```bash
scp "josepinilla1601@pacheco.chillan.ubiobio.cl:bestpem/respaldos/$(ssh josepinilla1601@pacheco.chillan.ubiobio.cl 'ls -1t ~/bestpem/respaldos | head -1')" .
```

**Restaurar un respaldo** (reemplaza todos los datos actuales por los del respaldo; úsalo solo si se perdió información):

```bash
ssh josepinilla1601@pacheco.chillan.ubiobio.cl
systemctl --user stop bestpem-app
gunzip -c ~/bestpem/respaldos/practicas_ubb_AAAA-MM-DD_HHMM.sql.gz | podman exec -i bestpem-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
systemctl --user start bestpem-app
```

## Instalación desde cero

Copia `app.jar` y los archivos de esta carpeta a `~/bestpem/` y ejecuta:

```bash
BOOTSTRAP_ADMIN_EMAIL=correo@ejemplo.cl BOOTSTRAP_ADMIN_PASSWORD='clave-de-10-o-mas' bash ~/bestpem/instalar.sh
```

Luego carga los datos de demostración desde tu computador:

```bash
APP_URL=https://pacheco.chillan.ubiobio.cl/bestpem node scripts/datos-demo/cargar.mjs
```

## Activar el asistente con IA (Gemini)

Agrega la clave al archivo de configuración del servidor y reinicia la aplicación (la clave no se sube a Git):

```bash
ssh josepinilla1601@pacheco.chillan.ubiobio.cl
```

```bash
echo 'GEMINI_API_KEY=tu_clave' >> ~/bestpem/app.env && systemctl --user restart bestpem-app
```

Sin clave, el asistente sigue funcionando con la búsqueda por palabras clave y la opción "Explicar un documento" aparece como no activada.
