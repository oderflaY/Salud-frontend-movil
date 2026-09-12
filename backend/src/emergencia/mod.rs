pub mod handlers;
pub mod models;

use std::sync::Arc;

use axum::{routing::get, Router};
use tower_governor::{governor::GovernorConfigBuilder, key_extractor::SmartIpKeyExtractor, GovernorLayer};

use crate::state::AppState;

/// Módulo 5: único endpoint sin sesión. El rate limit es por IP real del
/// cliente (`SmartIpKeyExtractor`, lee X-Forwarded-For detrás de Caddy) — una
/// tarjeta física perdida no debería poder usarse para tantear miles de IDs
/// por segundo.
pub fn router() -> Router<AppState> {
    let config = Arc::new(
        GovernorConfigBuilder::default()
            .per_second(1)
            .burst_size(10)
            .key_extractor(SmartIpKeyExtractor)
            .finish()
            .expect("configuración de rate limit inválida"),
    );

    Router::new()
        .route(
            "/tarjetas/:id_tarjeta_rfid/perfil-supervivencia",
            get(handlers::consultar_por_tarjeta),
        )
        .layer(GovernorLayer { config })
}
