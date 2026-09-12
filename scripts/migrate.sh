#!/usr/bin/env bash
# Aplica, en orden y de forma idempotente, cada archivo de db/migrations contra
# DATABASE_URL. Forward-only a propósito: para este esquema, un rollback real
# de negocio (ej. deshacer una migración que ya movió datos de pacientes) es
# más seguro como una migración nueva que revierte, no como un "down" ciego.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL es requerida}"
MIGRATIONS_DIR="${1:-/migrations}"

psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "
  create schema if not exists app;
  create table if not exists app.schema_migraciones (
    version text primary key,
    aplicada_en timestamptz not null default now()
  );
"

for f in $(find "$MIGRATIONS_DIR" -maxdepth 1 -name '*.sql' | sort); do
  version=$(basename "$f")
  ya_aplicada=$(psql "$DATABASE_URL" -tA -c "select 1 from app.schema_migraciones where version = '$version'")
  if [ "$ya_aplicada" = "1" ]; then
    echo "== omitida (ya aplicada): $version"
    continue
  fi
  echo "== aplicando: $version"
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$f"
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "insert into app.schema_migraciones(version) values ('$version')"
done

echo "== migraciones al día"
