#!/usr/bin/env bash
# Respaldo de la base de datos de Prácticas UBB (incluye los archivos entregados, que se guardan en MySQL).
# Lo ejecuta cada noche el temporizador bestpem-respaldo.timer. También se puede correr a mano:
#   bash ~/bestpem/respaldo.sh
# Guarda los respaldos comprimidos en ~/bestpem/respaldos/ y conserva los últimos 14.
set -euo pipefail
umask 077
destino="$HOME/bestpem/respaldos"
conservar=14
mkdir -p "$destino"
archivo="$destino/practicas_ubb_$(date +%Y-%m-%d_%H%M).sql.gz"
temporal="$archivo.parcial"

# La contraseña se lee dentro del contenedor (variable de db.env); no aparece en la lista de procesos del servidor.
podman exec bestpem-db sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --hex-blob --databases practicas_ubb' 2>/dev/null \
  | gzip -9 > "$temporal"

# Un respaldo vacío o incompleto no reemplaza a los anteriores.
if [ ! -s "$temporal" ] || ! gzip -cd "$temporal" | tail -n 1 | grep -q "Dump completed"; then
  rm -f "$temporal"
  echo "ERROR: el respaldo quedó incompleto; se conservan los anteriores." >&2
  exit 1
fi
mv "$temporal" "$archivo"

# Borra los más antiguos y deja solo los últimos $conservar.
ls -1t "$destino"/practicas_ubb_*.sql.gz 2>/dev/null | tail -n +$((conservar + 1)) | xargs -r rm -f
echo "Respaldo listo: $archivo ($(du -h "$archivo" | cut -f1))."
