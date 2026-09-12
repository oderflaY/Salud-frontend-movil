pub mod auth;
pub mod config;
pub mod db;
pub mod emergencia;
pub mod error;
pub mod ia;
pub mod realtime;
pub mod routes;
pub mod state;
pub mod telemetry;

use std::sync::Arc;

use config::Config;
use realtime::hub::Hub;
use state::AppState;

pub async fn run() {
    // En Docker las env vars ya vienen del compose; .env solo importa para
    // `cargo run` local, así que un .env ausente no debe ser un error.
    let _ = dotenvy::dotenv();

    telemetry::init();

    let config = Config::from_env();
    let db = db::conectar(&config.database_url)
        .await
        .expect("no se pudo conectar a la base de datos");

    let hub = Hub::nuevo();
    tokio::spawn(realtime::listener::escuchar(config.database_url.clone(), hub.clone()));

    let puerto = config.backend_port;
    let http = reqwest::Client::new();
    let state = AppState {
        db,
        config: Arc::new(config),
        hub,
        http,
    };

    let app = routes::construir(state);

    let listener = tokio::net::TcpListener::bind(("0.0.0.0", puerto))
        .await
        .expect("no se pudo abrir el puerto de escucha");

    tracing::info!(puerto, "backend escuchando");

    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<std::net::SocketAddr>(),
    )
    .await
    .expect("el servidor terminó con error");
}
