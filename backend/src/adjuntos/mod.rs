pub mod handlers;

use axum::{
    extract::DefaultBodyLimit,
    routing::{get, post},
    Router,
};

use crate::state::AppState;

/// Un archivo del chat: fotos de receta, resultados de laboratorio, un
/// documento escaneado. 10 MB alcanza para una foto de telefono o un PDF de
/// varias paginas, y deja fuera videos, que no son para este canal.
pub const LIMITE_ARCHIVO: usize = 10 * 1024 * 1024;

/// Adjuntos del chat (frontend/docs/CONTRATOS_BACKEND.md, seccion 13). Viven
/// en Axum y no en PostgREST porque hay que leer el JWT, limitar el tamano y
/// servir el binario con cabeceras seguras. Cuelgan de `/chat/*` porque Caddy
/// ya manda esa ruta aqui: no hace falta tocar el despliegue.
pub fn router() -> Router<AppState> {
    Router::new()
        .route("/chat/:id_conversacion/adjuntos", post(handlers::subir_adjunto))
        .route(
            "/chat/:id_conversacion/adjuntos/:id_adjunto",
            get(handlers::descargar_adjunto),
        )
        // Holgura para las cabeceras del multipart y el campo `tipo`; el
        // limite real del archivo se comprueba en el handler.
        .layer(DefaultBodyLimit::max(LIMITE_ARCHIVO + 64 * 1024))
}
