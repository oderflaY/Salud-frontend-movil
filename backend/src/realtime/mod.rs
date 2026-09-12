pub mod hub;
pub mod listener;
mod ws;

use axum::{routing::get, Router};

use crate::state::AppState;

/// Módulo 11: un único endpoint WebSocket multiplexado (ver
/// docs/contratos-datos-backend.md sección 11) en vez de un socket por
/// contrato — el cliente se suscribe a canales (`chat:{id}`, `agenda:{id}`,
/// `diario:{id}`) después de conectar.
pub fn router() -> Router<AppState> {
    Router::new().route("/realtime", get(ws::manejar_conexion))
}
