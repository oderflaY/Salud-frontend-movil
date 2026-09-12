FROM postgres:16-bookworm

# pgTAP: pruebas unitarias en SQL para constraints, RLS y funciones RPC.
# pg_cron: expiración periódica de reservas de franja (declarativo, sin scheduler aparte).
# postgresql-16-postgres-fdw no se usa; se deja pgcrypto disponible por si una migración
# futura lo necesita (gen_random_uuid ya es nativo desde PG13, no depende de pgcrypto).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-16-pgtap \
        postgresql-16-cron \
    && rm -rf /var/lib/apt/lists/*
