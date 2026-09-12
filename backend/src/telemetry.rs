use tracing_subscriber::{fmt, layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

/// `RUST_LOG` controla el nivel (ver .env.example); sin variable, cae a
/// "info" para no quedar mudo en un despliegue donde alguien olvidó fijarla.
pub fn init() {
    let filtro = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));

    tracing_subscriber::registry()
        .with(filtro)
        .with(fmt::layer())
        .init();
}
