#!/usr/bin/env bash
# Instala o actualiza Prácticas UBB en Pacheco (Podman sin root + systemd de usuario).
# Requiere: ~/bestpem/app.jar y los archivos .pod/.container de esta carpeta en ~/bestpem/.
# La primera vez genera contraseñas aleatorias para MySQL. Las siguientes, las conserva.
# Uso: BOOTSTRAP_ADMIN_EMAIL=... BOOTSTRAP_ADMIN_PASSWORD=... bash ~/bestpem/instalar.sh
set -euo pipefail
cd "$HOME/bestpem"
umask 077

if [ ! -f db.env ]; then
  clave_root=$(openssl rand -hex 24); clave_app=$(openssl rand -hex 24)
  cat > db.env <<EOF
MYSQL_ROOT_PASSWORD=$clave_root
MYSQL_DATABASE=practicas_ubb
MYSQL_USER=practicas_app
MYSQL_PASSWORD=$clave_app
EOF
  cat > app.env <<EOF
SPRING_PROFILES_ACTIVE=prod
APP_ADDRESS=0.0.0.0
APP_PORT=8019
# El proxy de Pacheco quita "/bestpem" antes de llegar al puerto 8019: la app corre en la raíz.
# Además, el Apache del proxy reescribe toda ruta de cookie que empiece con "/" a "/bestcpem/" (con "c"),
# y el navegador nunca la devolvería. Una ruta sin "/" inicial no se reescribe y el navegador usa
# la ruta de la petición (/bestpem/api), donde están todas las llamadas con sesión.
# Si la universidad corrige el proxy, cambiar a SESSION_COOKIE_PATH=/bestpem.
APP_CONTEXT_PATH=
SESSION_COOKIE_NAME=BESTPEM_SESSION
SESSION_COOKIE_PATH=bestpem
DB_URL=jdbc:mysql://127.0.0.1:3306/practicas_ubb?serverTimezone=UTC&allowPublicKeyRetrieval=true&sslMode=DISABLED
DB_USER=practicas_app
DB_PASSWORD=$clave_app
BOOTSTRAP_ADMIN_EMAIL=${BOOTSTRAP_ADMIN_EMAIL:?Falta BOOTSTRAP_ADMIN_EMAIL}
BOOTSTRAP_ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD:?Falta BOOTSTRAP_ADMIN_PASSWORD}
EOF
  echo "Configuración creada en ~/bestpem (db.env y app.env, solo legibles por tu usuario)."
fi
chmod 600 db.env app.env
chmod 644 app.jar

mkdir -p "$HOME/.config/containers/systemd"
cp bestpem.pod bestpem-db.container bestpem-app.container "$HOME/.config/containers/systemd/"
systemctl --user daemon-reload
systemctl --user restart bestpem-db.service bestpem-app.service
echo "Servicios iniciados. Revisa con: systemctl --user status bestpem-app  |  podman logs -f bestpem-app"
