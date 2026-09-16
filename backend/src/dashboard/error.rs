use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;

use crate::error::AppError;

/// Errores del panel web. Difieren del resto de la API en dos cosas que exige
/// el frontend (contrato "Salud+ Panel Web", sección 5):
///
/// - `message` es una frase para mostrar tal cual; el motivo tipado va aparte
///   en `codigo`.
/// - Nunca 404 por un recurso que no es del médico: el panel toma cualquier
///   404 como "endpoint no implementado" y enseña datos de ejemplo sin avisar
///   (en un reset, una contraseña falsa). Esos casos responden 403.
#[derive(Debug)]
pub enum ErrorPanel {
    App(AppError),
    Rechazo {
        status: StatusCode,
        codigo: &'static str,
        mensaje: &'static str,
    },
}

impl ErrorPanel {
    pub fn rechazo(status: StatusCode, codigo: &'static str, mensaje: &'static str) -> Self {
        ErrorPanel::Rechazo { status, codigo, mensaje }
    }
}

impl From<AppError> for ErrorPanel {
    fn from(e: AppError) -> Self {
        ErrorPanel::App(e)
    }
}

impl From<sqlx::Error> for ErrorPanel {
    fn from(e: sqlx::Error) -> Self {
        ErrorPanel::App(AppError::from(e))
    }
}

fn mensaje_legible(e: &AppError) -> &'static str {
    match e {
        AppError::CredencialesInvalidas => "Correo o contraseña incorrectos.",
        AppError::CuentaBloqueada => "Tu cuenta está bloqueada. Contacta a la clínica.",
        AppError::NoAutorizado => "Tu sesión no es válida o ya venció. Vuelve a iniciar sesión.",
        AppError::SesionRevocada => "Tu sesión se cerró. Vuelve a iniciar sesión.",
        AppError::CuentaInactiva => "Tu cuenta ya no está activa.",
        AppError::AccesoDenegado => "No tienes permiso para esta sección.",
        AppError::CorreoYaRegistrado => "Ya existe una cuenta con ese correo.",
        AppError::CedulaYaRegistrada => "Esa cédula profesional ya está registrada.",
        AppError::CambioDeContrasenaRequerido => "Debes cambiar tu contraseña temporal antes de continuar.",
        AppError::ContrasenaTemporalVencida => "Tu contraseña temporal venció. Pide al administrador que restablezca tu cuenta otra vez.",
        AppError::ContrasenaNoValida => "La contraseña nueva debe tener entre 8 y 128 caracteres.",
        AppError::MedicoNoVerificado => "Tu cédula profesional aún no está aprobada: no puedes reiniciar cuentas.",
        AppError::PacienteNoEncontrado => "Ese paciente no está vinculado contigo.",
        AppError::AlertaNoEncontrada => "Esa alerta no existe o no es tuya.",
        AppError::AdjuntoNoEncontrado => "El archivo ya no existe.",
        AppError::SolicitudInvalida => "La solicitud no es válida.",
        _ => "No se pudo completar la solicitud. Intenta de nuevo.",
    }
}

impl IntoResponse for ErrorPanel {
    fn into_response(self) -> Response {
        let (status, codigo, mensaje) = match &self {
            ErrorPanel::Rechazo { status, codigo, mensaje } => (*status, *codigo, *mensaje),
            ErrorPanel::App(e) => {
                if let AppError::Interno(detalle) = e {
                    tracing::error!(error = %detalle, "error interno no expuesto al cliente");
                }
                let status = match e {
                    AppError::PacienteNoEncontrado | AppError::AlertaNoEncontrada => StatusCode::FORBIDDEN,
                    otro => otro.status(),
                };
                (status, e.motivo(), mensaje_legible(e))
            }
        };
        (status, Json(json!({ "message": mensaje, "codigo": codigo }))).into_response()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn un_paciente_ajeno_es_403_y_no_404() {
        let r = ErrorPanel::from(AppError::PacienteNoEncontrado).into_response();
        assert_eq!(r.status(), StatusCode::FORBIDDEN);
        let r = ErrorPanel::from(AppError::AlertaNoEncontrada).into_response();
        assert_eq!(r.status(), StatusCode::FORBIDDEN);
    }

    #[test]
    fn la_sesion_vencida_sigue_siendo_401() {
        for e in [AppError::NoAutorizado, AppError::SesionRevocada, AppError::CuentaInactiva] {
            assert_eq!(ErrorPanel::from(e).into_response().status(), StatusCode::UNAUTHORIZED);
        }
    }
}
