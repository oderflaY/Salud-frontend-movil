use axum::{routing::get, Router};
use tower_http::trace::TraceLayer;

use crate::{adjuntos, auth, dashboard, emergencia, ia, realtime, state::AppState};

pub fn construir(state: AppState) -> Router {
    Router::new()
        .route("/healthz", get(|| async { "ok" }))
        .merge(auth::router())
        .merge(emergencia::router())
        .merge(realtime::router())
        .merge(ia::router())
        .merge(adjuntos::router())
        .nest("/api/v1", dashboard::router(&state.config.cors_origenes))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use sqlx::postgres::PgPoolOptions;

    use crate::{config::Config, realtime::hub::Hub, state::AppState};

    /// Axum no detecta rutas en conflicto al compilar: revienta al arrancar el
    /// servidor. Con varias rutas colgando de `/chat/*` (IA, adjuntos,
    /// resumen por mensaje), esto lo atrapa sin levantar Postgres: el pool es
    /// perezoso y nunca se conecta.
    #[tokio::test]
    async fn el_router_completo_se_construye_sin_rutas_en_conflicto() {
        let config = Config {
            database_url: "postgres://nadie@localhost:1/nada".to_string(),
            jwt_secret: "secreto".to_string(),
            jwt_expiracion_horas: 1,
            argon2_secret_key: "pepper".to_string(),
            backend_port: 0,
            deepseek_api_key: None,
            deepseek_base_url: "http://localhost:1".to_string(),
            deepseek_modelo: "modelo".to_string(),
            cors_origenes: vec!["http://localhost:5173".to_string()],
        };
        let state = AppState {
            db: PgPoolOptions::new().connect_lazy(&config.database_url).unwrap(),
            config: Arc::new(config),
            hub: Hub::nuevo(),
            http: reqwest::Client::new(),
        };
        let _ = super::construir(state);
    }
}
