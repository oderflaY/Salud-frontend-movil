use sqlx::postgres::{PgPool, PgPoolOptions};

/// Axum se conecta con el rol dueño de la base (mismo `DATABASE_URL` que usan
/// las migraciones), no con `authenticator`/`anon`/`paciente`/`medico` — esos
/// tres últimos son exclusivos de PostgREST. Ver docs/base-de-datos-convenciones.md.
pub async fn conectar(database_url: &str) -> Result<PgPool, sqlx::Error> {
    PgPoolOptions::new()
        .max_connections(10)
        .connect(database_url)
        .await
}
