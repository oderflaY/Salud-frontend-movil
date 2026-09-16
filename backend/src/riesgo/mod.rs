pub mod contexto;
pub mod handlers;

use axum::{
    routing::{get, post},
    Router,
};

use crate::state::AppState;

/// Análisis de riesgo del diario y del chat (migración 0019).
///
/// La detección (palabras clave, negaciones, signos vitales) vive en Postgres
/// para que se aplique a TODO lo que se inserta, entre por donde entre. Aquí
/// solo está lo que necesita Axum: pedirle a DeepSeek que explique el
/// resultado usando únicamente lo recuperado de la base de conocimiento (RAG).
pub fn router() -> Router<AppState> {
    Router::new()
        .route("/riesgo/analizar", post(handlers::analizar))
        .route("/riesgo/diario/:id_entrada", get(handlers::analisis_de_entrada))
}
