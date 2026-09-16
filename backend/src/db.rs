use std::time::Duration;

use sqlx::postgres::{PgPool, PgPoolOptions};

/// Axum se conecta con el rol dueño de la base (mismo `DATABASE_URL` que usan
/// las migraciones), no con `authenticator`/`anon`/`paciente`/`medico` — esos
/// tres últimos son exclusivos de PostgREST. Ver docs/base-de-datos-convenciones.md.
pub async fn conectar(database_url: &str) -> Result<PgPool, sqlx::Error> {
    PgPoolOptions::new()
        .max_connections(10)
        // Con la base saturada, mejor un 500 rápido que peticiones colgadas 30 s.
        .acquire_timeout(Duration::from_secs(5))
        .idle_timeout(Duration::from_secs(600))
        // Recicla conexiones: un failover o un cambio de configuración de
        // Postgres no deja conexiones viejas vivas para siempre.
        .max_lifetime(Duration::from_secs(1800))
        .connect(database_url)
        .await
}
