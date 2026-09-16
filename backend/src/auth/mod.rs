pub mod handlers;
pub mod jwt;
pub mod models;
pub mod password;
pub mod sesion;

use axum::{
    routing::{get, post},
    Router,
};

use crate::{limite, state::AppState};

/// Módulos 1 y 2 del documento de contratos: los dos portales de
/// autenticación. Vive en Axum porque necesita el secreto de firma del JWT y
/// el pepper de Argon2 — nada de esto pasa por PostgREST.
pub fn router() -> Router<AppState> {
    // Lo que se puede intentar sin sesión (y por lo tanto a ciegas) lleva
    // límite por IP: probar contraseñas o crear cuentas en masa.
    let sin_sesion = Router::new()
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
        .layer(limite::intentos_de_acceso());

    Router::new()
        .merge(sin_sesion)
        .route(
            "/auth/pacientes/contrasena",
            post(handlers::cambiar_contrasena_paciente),
        )
        .route("/auth/pacientes/baja", post(handlers::dar_de_baja_paciente))
        .route(
            "/auth/profesionales/baja",
            post(handlers::dar_de_baja_profesional),
        )
        .route("/auth/eliminar-cuenta", get(handlers::pagina_eliminar_cuenta))
}
