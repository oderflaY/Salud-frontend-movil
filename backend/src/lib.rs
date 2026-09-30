pub mod adjuntos;
pub mod auth;
pub mod cli;
pub mod config;
pub mod dashboard;
pub mod db;
pub mod emergencia;
pub mod error;
pub mod ia;
pub mod limite;
pub mod realtime;
pub mod riesgo;
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

    let argumentos: Vec<String> = std::env::args().skip(1).collect();
    if argumentos.first().map(String::as_str) == Some("crear-admin") {
        cli::crear_admin(&argumentos[1..]).await;
        return;
    }

    let config = Config::from_env();
    let db = db::conectar(&config.database_url)
        .await
        .expect("no se pudo conectar a la base de datos");

    let hub = Hub::nuevo();
    tokio::spawn(realtime::listener::escuchar(config.database_url.clone(), hub.clone()));

    let puerto = config.backend_port;
    // Sin tiempo máximo, una API externa lenta (DeepSeek) dejaría peticiones
    // del paciente esperando indefinidamente.
    let http = reqwest::Client::builder()
        // Holgado a propósito: un modelo propio detrás de un túnel (ngrok)
        // tarda más en abrir la conexión que una API en un centro de datos.
        .connect_timeout(std::time::Duration::from_secs(10))
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .expect("no se pudo crear el cliente HTTP");
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
    .with_graceful_shutdown(senal_de_apagado())
    .await
    .expect("el servidor terminó con error");
}

/// `docker compose stop`/`up --build` mandan SIGTERM: se terminan las
/// peticiones en curso (un mensaje a medio guardar) antes de salir.
async fn senal_de_apagado() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let terminar = async {
        if let Ok(mut senal) = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate()) {
            senal.recv().await;
        }
    };
    #[cfg(not(unix))]
    let terminar = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminar => {},
    }
    tracing::info!("apagando: se terminan las peticiones en curso");
}
