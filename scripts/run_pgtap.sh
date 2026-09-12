#!/usr/bin/env bash
# Corre cada archivo de db/tests contra DATABASE_URL y falla si alguna
# aserción pgTAP reporta "not ok". No depende de pg_prove/Perl: pgTAP emite
# TAP plano por stdout, y basta con revisar esa salida.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL es requerida}"
TESTS_DIR="${1:-/tests}"

# pgTAP se instala solo para pruebas (imagen db/Dockerfile la trae, pero la
# extensión no se crea en 0001_bootstrap.sql a propósito: no es una
# dependencia de negocio, y así una base de producción con esa misma imagen
# no la carga a menos que alguien la pida explícitamente).
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "create extension if not exists pgtap;"

fallo=0
for f in $(find "$TESTS_DIR" -maxdepth 1 -name '*.sql' | sort); do
  echo "== ejecutando: $(basename "$f")"
  salida=$(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -qAt -f "$f")
  echo "$salida"
  if echo "$salida" | grep -q '^not ok'; then
    echo "!! fallo en $(basename "$f")"
    fallo=1
  fi
done

if [ "$fallo" -ne 0 ]; then
  echo "== pgTAP: hay pruebas en rojo"
  exit 1
fi

echo "== pgTAP: todo en verde"
