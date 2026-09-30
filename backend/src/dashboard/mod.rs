pub mod contrasena_temporal;
pub mod cuentas;
pub mod enlace_adjunto;
pub mod error;
pub mod handlers;
pub mod sesion_panel;

use std::time::Duration;

use axum::{
    extract::Request,
    http::{header, HeaderName, HeaderValue, Method},
    middleware::{self, Next},
    response::Response,
    routing::{get, patch, post},
    Router,
};
use tower_http::cors::{AllowOrigin, CorsLayer};
use tracing::Instrument;

use crate::{limite, state::AppState};

const X_REQUEST_ID: HeaderName = HeaderName::from_static("x-request-id");

/// Panel web del médico (Salud+ Panel Web, React + Vite), bajo `/api/v1`.
///
/// Rutas y formas exactas del contrato del frontend; ver
/// `docs/DM_Panel_Web.md`. Entra el médico con su cuenta de la app y ve
/// solo a sus pacientes con vínculo vigente; el administrador gestiona
/// cuentas sin ver expedientes.
pub fn router(origenes_permitidos: &[String]) -> Router<AppState> {
    // Antes de CORS: un 429 también necesita sus cabeceras para que el
    // navegador deje al panel leerlo.
    let sin_sesion = Router::new()
        .route("/auth/login", post(handlers::iniciar_sesion))
        .route("/auth/recuperar-contrasena", post(handlers::solicitar_recuperacion))
        .layer(limite::intentos_de_acceso());

    Router::new()
        .merge(sin_sesion)
        .route("/auth/me", get(handlers::sesion_actual))
        .route("/auth/cambiar-contrasena", post(handlers::cambiar_contrasena))
        .route("/admin/dashboard/kpis", get(handlers::kpis))
        .route("/admin/pacientes", get(handlers::pacientes))
        .route("/admin/alertas", get(handlers::alertas))
        // El frontend usa hoy /alerts/{id}; /admin/alertas/{id} queda lista
        // para cuando lo cambie.
        .route("/alerts/:id_alerta", patch(handlers::cambiar_estado_alerta))
        .route("/admin/alertas/:id_alerta", patch(handlers::cambiar_estado_alerta))
        .route("/admin/auditoria", get(handlers::auditoria))
        .route("/admin/usuarios/:id_paciente/reset-cuenta", patch(handlers::resetear_cuenta))
        .route("/admin/conversaciones", get(handlers::conversaciones))
        .route(
            "/admin/conversaciones/:id_conversacion/mensajes",
            get(handlers::mensajes).post(handlers::enviar_mensaje),
        )
        .route("/admin/conversaciones/:id_conversacion/leer", patch(handlers::marcar_leida))
        .route("/adjuntos/:id_adjunto", get(handlers::ver_adjunto))
        // El mismo traductor que usa la app. Vive también bajo /api/v1 porque
        // el panel corre en un navegador: fuera de aquí no hay CORS y la
        // llamada se bloquearía antes de salir.
        .route("/chat/traducir", post(crate::ia::handlers::traducir_texto))
        // Gestión de cuentas: solo administradores.
        .route("/admin/cuentas/medicos", get(cuentas::listar_medicos).post(cuentas::crear_medico))
        .route("/admin/cuentas/medicos/:id/verificacion", patch(cuentas::verificar_cedula))
        .route("/admin/cuentas/medicos/:id/estado", patch(cuentas::estado_medico))
        .route("/admin/cuentas/medicos/:id/reset-contrasena", patch(cuentas::restablecer_medico))
        .route("/admin/cuentas/pacientes", get(cuentas::listar_pacientes))
        .route("/admin/cuentas/pacientes/:id/estado", patch(cuentas::estado_paciente))
        .route("/admin/cuentas/pacientes/:id/reset-contrasena", patch(cuentas::restablecer_paciente))
        .route("/admin/cuentas/solicitudes", get(cuentas::listar_solicitudes))
        .layer(middleware::from_fn(con_id_de_peticion))
        .layer(cors(origenes_permitidos))
}

/// El panel manda un `X-Request-ID` (UUID v4) por llamada: queda en cada línea
/// de log de esa petición y vuelve en la respuesta para correlacionar.
async fn con_id_de_peticion(peticion: Request, siguiente: Next) -> Response {
    let id = peticion
        .headers()
        .get(&X_REQUEST_ID)
        .and_then(|v| v.to_str().ok())
        .filter(|v| !v.is_empty() && v.len() <= 64 && v.chars().all(|c| c.is_ascii_alphanumeric() || c == '-'))
        .map(str::to_owned);

    let metodo = peticion.method().clone();
    let ruta = peticion.uri().path().to_owned();
    let inicio = std::time::Instant::now();
    let tramo = tracing::info_span!("panel", request_id = id.as_deref().unwrap_or("-"));
    let mut respuesta = siguiente.run(peticion).instrument(tramo.clone()).await;
    tramo.in_scope(|| {
        tracing::info!(%metodo, %ruta, status = respuesta.status().as_u16(), ms = inicio.elapsed().as_millis() as u64, "panel");
    });
    if let Some(valor) = id.and_then(|id| HeaderValue::from_str(&id).ok()) {
        respuesta.headers_mut().insert(X_REQUEST_ID, valor);
    }
    respuesta
}

/// El panel corre en otro origen (Vite en :5173, o su propio dominio): el
/// navegador pide permiso antes de mandar `Authorization` y `X-Request-ID`.
/// Solo a los orígenes de `CORS_ORIGENES`; sin cookies, porque el token viaja
/// en la cabecera.
///
/// `red-local` en `CORS_ORIGENES` acepta además el panel abierto desde
/// cualquier equipo de la red local (`http://192.168.x.x:5173`, etc.). Es para
/// desarrollo, cuando la IP de la computadora cambia; en producción va solo el
/// dominio del panel.
pub fn cors(origenes_permitidos: &[String]) -> CorsLayer {
    let origen = if origenes_permitidos.iter().any(|o| o == "*") {
        AllowOrigin::any()
    } else if origenes_permitidos.iter().any(|o| o == RED_LOCAL) {
        let exactos: Vec<String> = origenes_permitidos.iter().filter(|o| *o != RED_LOCAL).cloned().collect();
        AllowOrigin::predicate(move |origen: &HeaderValue, _| {
            origen
                .to_str()
                .map(|o| exactos.iter().any(|e| e == o) || es_origen_de_red_local(o))
                .unwrap_or(false)
        })
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
        .allow_headers([header::AUTHORIZATION, header::CONTENT_TYPE, X_REQUEST_ID])
        .expose_headers([X_REQUEST_ID])
        .max_age(Duration::from_secs(600))
}

const RED_LOCAL: &str = "red-local";

/// `http://` hacia `localhost`, `127.0.0.1` o una IPv4 privada (10/8,
/// 172.16/12, 192.168/16), en cualquier puerto. Nunca `https://` ajeno ni
/// nombres de dominio.
fn es_origen_de_red_local(origen: &str) -> bool {
    let Some(resto) = origen.strip_prefix("http://") else {
        return false;
    };
    let host = match resto.rsplit_once(':') {
        Some((h, puerto)) if !puerto.is_empty() && puerto.chars().all(|c| c.is_ascii_digit()) => h,
        _ => resto,
    };
    if host == "localhost" || host == "127.0.0.1" {
        return true;
    }
    let octetos: Vec<u8> = host.split('.').filter_map(|p| p.parse().ok()).collect();
    if octetos.len() != 4 || host.split('.').count() != 4 {
        return false;
    }
    matches!(octetos.as_slice(), [10, ..] | [192, 168, ..]) || (octetos[0] == 172 && (16..=31).contains(&octetos[1]))
}

#[cfg(test)]
mod tests {
    use super::es_origen_de_red_local;

    #[test]
    fn la_red_local_incluye_ips_privadas_y_localhost() {
        for o in ["http://localhost:5173", "http://127.0.0.1:4173", "http://192.168.18.110:5173", "http://10.0.0.5:5173", "http://172.20.1.2:5173", "http://192.168.1.9"] {
            assert!(es_origen_de_red_local(o), "{o}");
        }
    }

    #[test]
    fn la_red_local_no_incluye_sitios_publicos_ni_https_ajeno() {
        for o in ["http://sitio-ajeno.example", "https://192.168.18.110:5173", "http://8.8.8.8:5173", "http://172.32.0.1:5173", "http://192.168.18.110.evil.com:5173", "http://192.168.1:5173"] {
            assert!(!es_origen_de_red_local(o), "{o}");
        }
    }
}
