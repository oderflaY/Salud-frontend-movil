pub mod handlers;
pub mod jwt;
pub mod models;
pub mod password;

use axum::{routing::post, Router};

use crate::state::AppState;

/// Módulos 1 y 2 del documento de contratos: los dos portales de
/// autenticación. Vive en Axum porque necesita el secreto de firma del JWT y
/// el pepper de Argon2 — nada de esto pasa por PostgREST.
pub fn router() -> Router<AppState> {
    Router::new()
        .route(
            "/auth/pacientes/sesion",
            post(handlers::iniciar_sesion_paciente),
        )
        .route("/auth/pacientes", post(handlers::crear_cuenta_paciente))
        .route(
            "/auth/profesionales/sesion",
            post(handlers::iniciar_sesion_profesional),
        )
        .route(
            "/auth/profesionales",
            post(handlers::crear_cuenta_profesional),
        )
}
