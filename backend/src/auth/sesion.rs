use axum::http::HeaderMap;
use sqlx::PgPool;

use crate::{error::AppError, state::AppState};

use super::jwt::{self, Claims};

/// Lo que `app.estado_sesion` (0016) deja pasar. Los demás estados son errores.
#[derive(Debug, PartialEq, Eq)]
pub enum EstadoSesion {
    Vigente,
    /// Entró con la contraseña temporal del médico y aún no elige una nueva.
    CambioDeContrasenaRequerido,
}

/// Una firma válida no basta: la cuenta pudo resetearse, bloquearse o darse
/// de baja después de emitir el token. PostgREST hace la misma pregunta en
/// su db-pre-request, con la misma función SQL.
pub async fn estado(db: &PgPool, claims: &Claims) -> Result<EstadoSesion, AppError> {
    let estado: Option<String> = sqlx::query_scalar("select app.estado_sesion($1, $2, $3)")
        .bind(&claims.role)
        .bind(&claims.sub)
        .bind(claims.iat as i64)
        .fetch_one(db)
        .await?;
    match estado.as_deref() {
        Some("VIGENTE") => Ok(EstadoSesion::Vigente),
        Some("CAMBIO_CONTRASENA_REQUERIDO") => Ok(EstadoSesion::CambioDeContrasenaRequerido),
        Some("SESION_REVOCADA") => Err(AppError::SesionRevocada),
        _ => Err(AppError::CuentaInactiva),
    }
}

/// Quién llama, con la sesión vigente. Es lo que usa cualquier endpoint de
/// Axum con sesión; `jwt::autenticar` solo comprueba la firma.
pub async fn autenticar(headers: &HeaderMap, state: &AppState) -> Result<Claims, AppError> {
    let claims = jwt::autenticar(headers, &state.config.jwt_secret)?;
    match estado(&state.db, &claims).await? {
        EstadoSesion::Vigente => Ok(claims),
        EstadoSesion::CambioDeContrasenaRequerido => Err(AppError::CambioDeContrasenaRequerido),
    }
}
