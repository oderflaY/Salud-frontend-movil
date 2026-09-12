pub mod deepseek;
pub mod handlers;

use axum::{routing::post, Router};

use crate::state::AppState;

/// Capacidades de IA (DeepSeek): resumen de conversaciones de chat y
/// traducción de texto. Vive en Axum, no en PostgREST/PL-pgSQL, porque
/// necesita llamar a un servicio externo con una API key — la misma regla
/// que ya separa auth/emergencia/realtime del resto (ver
/// docs/base-de-datos-convenciones.md). Ninguna otra ruta existente se
/// modifica: este router solo agrega endpoints nuevos.
pub fn router() -> Router<AppState> {
    Router::new()
        .route("/chat/:id_conversacion/resumen", post(handlers::resumir_conversacion))
        .route("/chat/traducir", post(handlers::traducir_texto))
}
