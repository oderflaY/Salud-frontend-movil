pub mod handlers;
pub mod models;

use axum::{routing::get, Router};

use crate::{limite, state::AppState};

/// Módulo 5: único endpoint sin sesión. El rate limit es por IP real del
/// cliente (`SmartIpKeyExtractor`, lee X-Forwarded-For detrás de Caddy) — una
/// tarjeta física perdida no debería poder usarse para tantear miles de IDs
/// por segundo.
pub fn router() -> Router<AppState> {
    Router::new()
        .route(
            "/tarjetas/:id_tarjeta_rfid/perfil-supervivencia",
            get(handlers::consultar_por_tarjeta),
        )
        .layer(limite::por_ip(1, 10))
}
