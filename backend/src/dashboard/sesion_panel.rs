use axum::http::{HeaderMap, StatusCode};

use crate::{
    auth::{jwt::Claims, sesion},
    error::AppError,
    state::AppState,
};

use super::error::ErrorPanel;

pub const ROL_MEDICO: &str = "medico";
pub const ROL_ADMIN: &str = "admin";

/// Médico o administrador con sesión vigente, aunque todavía tenga que cambiar
/// su contraseña temporal. Solo `/auth/me` y el cambio de contraseña se
/// conforman con esto.
pub async fn cuenta_en_sesion(headers: &HeaderMap, state: &AppState) -> Result<Claims, ErrorPanel> {
    let claims = sesion::autenticar(headers, state).await?;
    if claims.role != ROL_MEDICO && claims.role != ROL_ADMIN {
        return Err(ErrorPanel::rechazo(
            StatusCode::FORBIDDEN,
            "ACCESO_DENEGADO",
            "Este panel es solo para médicos y administradores.",
        ));
    }
    Ok(claims)
}

pub async fn debe_cambiar_contrasena(state: &AppState, claims: &Claims) -> Result<bool, ErrorPanel> {
    let consulta = if claims.role == ROL_ADMIN {
        "select debe_cambiar_contrasena from app.administradores where id_admin = $1"
    } else {
        "select debe_cambiar_contrasena from app.medicos where id_medico = $1"
    };
    let debe: Option<bool> = sqlx::query_scalar(consulta)
        .bind(&claims.sub)
        .fetch_optional(&state.db)
        .await?;
    Ok(debe.ok_or(AppError::CuentaInactiva)?)
}

/// Cualquiera de los dos roles, ya con contraseña propia.
pub async fn panel_en_sesion(headers: &HeaderMap, state: &AppState) -> Result<Claims, ErrorPanel> {
    let claims = cuenta_en_sesion(headers, state).await?;
    if debe_cambiar_contrasena(state, &claims).await? {
        return Err(AppError::CambioDeContrasenaRequerido.into());
    }
    Ok(claims)
}

async fn con_rol(headers: &HeaderMap, state: &AppState, rol: &str) -> Result<Claims, ErrorPanel> {
    let claims = panel_en_sesion(headers, state).await?;
    if claims.role == rol {
        return Ok(claims);
    }
    Err(if rol == ROL_ADMIN {
        ErrorPanel::rechazo(StatusCode::FORBIDDEN, "SOLO_ADMINISTRADORES", "Esta sección es solo para administradores.")
    } else {
        ErrorPanel::rechazo(
            StatusCode::FORBIDDEN,
            "SOLO_MEDICOS",
            "Esta sección es solo para médicos: el administrador no ve expedientes clínicos.",
        )
    })
}

pub async fn medico_en_sesion(headers: &HeaderMap, state: &AppState) -> Result<Claims, ErrorPanel> {
    con_rol(headers, state, ROL_MEDICO).await
}

pub async fn admin_en_sesion(headers: &HeaderMap, state: &AppState) -> Result<Claims, ErrorPanel> {
    con_rol(headers, state, ROL_ADMIN).await
}
