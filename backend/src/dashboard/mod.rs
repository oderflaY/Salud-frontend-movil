pub mod contrasena_temporal;
pub mod handlers;

use std::time::Duration;

use axum::{
    http::{header, HeaderValue, Method},
    routing::{get, patch, post},
    Router,
};
use tower_http::cors::{AllowOrigin, CorsLayer};

use crate::state::AppState;

/// Dashboard web del médico (Salud-Frontend, React + Vite), bajo `/api/v1`.
///
/// Entra el médico con su misma cuenta de la app y ve solo a sus pacientes
/// con vínculo vigente. Contrato completo en `docs/dashboard-medico.md`.
pub fn router(origenes_permitidos: &[String]) -> Router<AppState> {
    Router::new()
        .route("/auth/sesion", post(handlers::iniciar_sesion))
        .route("/auth/me", get(handlers::sesion_actual))
        .route("/admin/dashboard/kpis", get(handlers::kpis))
        .route("/admin/pacientes", get(handlers::pacientes))
        .route(
            "/admin/usuarios/:id_paciente/reset-cuenta",
            patch(handlers::resetear_cuenta),
        )
        .route("/admin/alertas", get(handlers::alertas))
        .route(
            "/admin/alertas/:id_alerta/estado",
            patch(handlers::cambiar_estado_alerta),
        )
        .route("/admin/auditoria", get(handlers::auditoria))
        .layer(cors(origenes_permitidos))
}

/// El dashboard corre en otro origen (Vite en :5173, o su propio dominio), así
/// que el navegador pide permiso antes de mandar `Authorization`. Solo a los
/// orígenes de `CORS_ORIGENES`; sin cookies, porque el token viaja en la
/// cabecera.
pub fn cors(origenes_permitidos: &[String]) -> CorsLayer {
    let origen = if origenes_permitidos.iter().any(|o| o == "*") {
        AllowOrigin::any()
    } else {
        AllowOrigin::list(
            origenes_permitidos
                .iter()
                .filter_map(|o| HeaderValue::from_str(o).ok()),
        )
    };
    CorsLayer::new()
        .allow_origin(origen)
        .allow_methods([Method::GET, Method::POST, Method::PATCH, Method::OPTIONS])
        .allow_headers([header::AUTHORIZATION, header::CONTENT_TYPE])
        .max_age(Duration::from_secs(600))
}
