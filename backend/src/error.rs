use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Serialize;

/// Un motivo tipado por variante, igual que el resto del sistema (ver
/// docs/errores.md): el cliente siempre recibe `{"message": "MOTIVO"}`,
/// nunca prosa — el campo se llama `message` (no `motivo`) para que
/// coincida con el shape nativo que PostgREST ya usa en sus propios errores
/// de RPC (`{"code","message","details","hint"}`); pelear contra esa
/// convención hubiera dejado dos formas de error distintas según qué
/// servicio respondiera. Cualquier endpoint nuevo en Axum agrega su
/// variante aquí en vez de construir una respuesta de error a mano.
#[derive(Debug)]
pub enum AppError {
    CredencialesInvalidas,
    CuentaBloqueada,
    CorreoYaRegistrado,
    CedulaYaRegistrada,
    TarjetaDesconocida,
    TarjetaRevocada,
    ReservaExpirada,
    FranjaOcupada,
    NoAutorizado,
    /// Validación en el borde del sistema (entrada de red), no una regla del
    /// documento de contratos — el cliente ya conocido nunca manda estos
    /// valores fuera de dominio, pero el backend no debe responder 500 ante
    /// un body malformado de un llamador distinto.
    SolicitudInvalida,
    /// El módulo de IA (`crate::ia`) no tiene `DEEPSEEK_API_KEY` puesta — no es
    /// un error del cliente, es una capacidad todavía no habilitada.
    IaNoConfigurada,
    /// La llamada a la API de DeepSeek falló o devolvió algo irreconocible.
    /// El detalle (posible cuerpo de error de un tercero) se registra con
    /// `tracing` pero nunca se expone al cliente, igual que `Interno`.
    IaNoDisponible(String),
    /// Nunca se expone el detalle al cliente (podría filtrar información
    /// interna); se registra con `tracing` y el cliente solo ve 500 genérico.
    Interno(String),
}

#[derive(Serialize)]
struct ErrorBody {
    message: String,
}

impl AppError {
    fn motivo(&self) -> &'static str {
        match self {
            AppError::CredencialesInvalidas => "CREDENCIALES_INVALIDAS",
            AppError::CuentaBloqueada => "CUENTA_BLOQUEADA",
            AppError::CorreoYaRegistrado => "CORREO_YA_REGISTRADO",
            AppError::CedulaYaRegistrada => "CEDULA_YA_REGISTRADA",
            AppError::TarjetaDesconocida => "TARJETA_DESCONOCIDA",
            AppError::TarjetaRevocada => "TARJETA_REVOCADA",
            AppError::ReservaExpirada => "RESERVA_EXPIRADA",
            AppError::FranjaOcupada => "FRANJA_OCUPADA",
            AppError::NoAutorizado => "NO_AUTORIZADO",
            AppError::SolicitudInvalida => "SOLICITUD_INVALIDA",
            AppError::IaNoConfigurada => "IA_NO_CONFIGURADA",
            AppError::IaNoDisponible(_) => "IA_NO_DISPONIBLE",
            AppError::Interno(_) => "ERROR_INTERNO",
        }
    }

    fn status(&self) -> StatusCode {
        match self {
            AppError::CredencialesInvalidas => StatusCode::UNAUTHORIZED,
            AppError::CuentaBloqueada => StatusCode::FORBIDDEN,
            AppError::CorreoYaRegistrado => StatusCode::CONFLICT,
            AppError::CedulaYaRegistrada => StatusCode::CONFLICT,
            AppError::TarjetaDesconocida => StatusCode::NOT_FOUND,
            AppError::TarjetaRevocada => StatusCode::FORBIDDEN,
            AppError::ReservaExpirada => StatusCode::GONE,
            AppError::FranjaOcupada => StatusCode::CONFLICT,
            AppError::NoAutorizado => StatusCode::UNAUTHORIZED,
            AppError::SolicitudInvalida => StatusCode::BAD_REQUEST,
            AppError::IaNoConfigurada => StatusCode::SERVICE_UNAVAILABLE,
            AppError::IaNoDisponible(_) => StatusCode::BAD_GATEWAY,
            AppError::Interno(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        if let AppError::Interno(detalle) | AppError::IaNoDisponible(detalle) = &self {
            tracing::error!(error = %detalle, "error interno no expuesto al cliente");
        }
        let body = ErrorBody {
            message: self.motivo().to_string(),
        };
        (self.status(), Json(body)).into_response()
    }
}

impl From<sqlx::Error> for AppError {
    fn from(e: sqlx::Error) -> Self {
        AppError::Interno(e.to_string())
    }
}
