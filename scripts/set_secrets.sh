#!/usr/bin/env bash
# Fija/rota contraseñas de roles de base de datos desde variables de entorno.
# Se corre después de migrate.sh en cada arranque: ALTER ROLE es idempotente,
# así que repetirlo no tiene efecto secundario si la contraseña no cambió.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL es requerida}"
: "${AUTHENTICATOR_PASSWORD:?AUTHENTICATOR_PASSWORD es requerida}"

# Escapamos comillas simples a mano en vez de depender de la sustitución de
# variables `:'var'` de psql, que no aplica de forma fiable al texto pasado
# por `-c` en todas las versiones/imagenes.
escapada=$(printf '%s' "$AUTHENTICATOR_PASSWORD" | sed "s/'/''/g")
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "alter role authenticator with password '${escapada}';"

echo "== secretos de roles aplicados"
