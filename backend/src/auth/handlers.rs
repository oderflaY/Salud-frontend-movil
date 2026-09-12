use axum::{extract::State, Json};

use crate::{error::AppError, state::AppState};

use super::{
    jwt,
    models::{
        CrearCuentaPacienteRequest, CrearCuentaProfesionalRequest, IniciarSesionRequest,
        SesionPaciente, SesionProfesional,
    },
    password,
};

const TRATAMIENTOS_VALIDOS: [&str; 3] = ["Dr.", "Dra.", "Dr(a)."];

fn es_violacion_unique(err: &sqlx::Error, columna_hint: &str) -> bool {
    match err {
        sqlx::Error::Database(db_err) if db_err.code().as_deref() == Some("23505") => db_err
            .constraint()
            .map(|c| c.contains(columna_hint))
            .unwrap_or(false),
        _ => false,
    }
}

#[derive(sqlx::FromRow)]
struct FilaPaciente {
    id_paciente: String,
    hash_contrasena: String,
    estado_cuenta: String,
    requiere_onboarding: bool,
}

pub async fn iniciar_sesion_paciente(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<Json<SesionPaciente>, AppError> {
    let fila = sqlx::query_as::<_, FilaPaciente>(
        "select id_paciente, hash_contrasena, estado_cuenta, requiere_onboarding
         from app.pacientes where correo = $1",
    )
    .bind(&body.correo)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CredencialesInvalidas)?;

    if fila.estado_cuenta == "bloqueada" {
        return Err(AppError::CuentaBloqueada);
    }

    let pepper = state.config.argon2_secret_key.as_bytes();
    if !password::verificar(&body.contrasena, &fila.hash_contrasena, pepper) {
        return Err(AppError::CredencialesInvalidas);
    }

    let token = jwt::emitir(
        &fila.id_paciente,
        "paciente",
        &state.config.jwt_secret,
        state.config.jwt_expiracion_horas,
    )
    .map_err(|e| AppError::Interno(e.to_string()))?;

    Ok(Json(SesionPaciente {
        id_paciente: fila.id_paciente,
        token,
        requiere_onboarding: fila.requiere_onboarding,
    }))
}

#[derive(sqlx::FromRow)]
struct FilaPacienteNuevo {
    id_paciente: String,
    requiere_onboarding: bool,
}

pub async fn crear_cuenta_paciente(
    State(state): State<AppState>,
    Json(body): Json<CrearCuentaPacienteRequest>,
) -> Result<Json<SesionPaciente>, AppError> {
    let pepper = state.config.argon2_secret_key.as_bytes();
    let hash = password::hash(&body.contrasena, pepper).map_err(AppError::Interno)?;

    let fila = sqlx::query_as::<_, FilaPacienteNuevo>(
        "insert into app.pacientes (correo, hash_contrasena) values ($1, $2)
         returning id_paciente, requiere_onboarding",
    )
    .bind(&body.correo)
    .bind(&hash)
    .fetch_one(&state.db)
    .await
    .map_err(|e| {
        if es_violacion_unique(&e, "correo") {
            AppError::CorreoYaRegistrado
        } else {
            AppError::from(e)
        }
    })?;
    let FilaPacienteNuevo {
        id_paciente,
        requiere_onboarding,
    } = fila;

    let token = jwt::emitir(
        &id_paciente,
        "paciente",
        &state.config.jwt_secret,
        state.config.jwt_expiracion_horas,
    )
    .map_err(|e| AppError::Interno(e.to_string()))?;

    Ok(Json(SesionPaciente {
        id_paciente,
        token,
        requiere_onboarding,
    }))
}

#[derive(sqlx::FromRow)]
struct FilaMedico {
    id_medico: String,
    hash_contrasena: String,
    estado_cuenta: String,
    nombre: String,
    apellidos: String,
    tratamiento: String,
    estado_verificacion: String,
}

pub async fn iniciar_sesion_profesional(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<Json<SesionProfesional>, AppError> {
    let fila = sqlx::query_as::<_, FilaMedico>(
        "select id_medico, hash_contrasena, estado_cuenta, nombre, apellidos,
                tratamiento, estado_verificacion
         from app.medicos where correo = $1",
    )
    .bind(&body.correo)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CredencialesInvalidas)?;

    if fila.estado_cuenta == "bloqueada" {
        return Err(AppError::CuentaBloqueada);
    }

    let pepper = state.config.argon2_secret_key.as_bytes();
    if !password::verificar(&body.contrasena, &fila.hash_contrasena, pepper) {
        return Err(AppError::CredencialesInvalidas);
    }

    let token = jwt::emitir(
        &fila.id_medico,
        "medico",
        &state.config.jwt_secret,
        state.config.jwt_expiracion_horas,
    )
    .map_err(|e| AppError::Interno(e.to_string()))?;

    Ok(Json(SesionProfesional {
        id_medico: fila.id_medico,
        token,
        nombre: fila.nombre,
        apellidos: fila.apellidos,
        tratamiento: fila.tratamiento,
        estado_verificacion: fila.estado_verificacion,
    }))
}

#[derive(sqlx::FromRow)]
struct FilaMedicoNuevo {
    id_medico: String,
    estado_verificacion: String,
}

pub async fn crear_cuenta_profesional(
    State(state): State<AppState>,
    Json(body): Json<CrearCuentaProfesionalRequest>,
) -> Result<Json<SesionProfesional>, AppError> {
    if !TRATAMIENTOS_VALIDOS.contains(&body.tratamiento.as_str()) {
        return Err(AppError::SolicitudInvalida);
    }

    let pepper = state.config.argon2_secret_key.as_bytes();
    let hash = password::hash(&body.contrasena, pepper).map_err(AppError::Interno)?;

    let fila = sqlx::query_as::<_, FilaMedicoNuevo>(
        "insert into app.medicos (correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
         values ($1, $2, $3, $4, $5, $6)
         returning id_medico, estado_verificacion",
    )
    .bind(&body.correo)
    .bind(&hash)
    .bind(&body.nombre)
    .bind(&body.apellidos)
    .bind(&body.tratamiento)
    .bind(&body.cedula_profesional)
    .fetch_one(&state.db)
    .await
    .map_err(|e| {
        if es_violacion_unique(&e, "cedula_profesional") {
            AppError::CedulaYaRegistrada
        } else if es_violacion_unique(&e, "correo") {
            AppError::CorreoYaRegistrado
        } else {
            AppError::from(e)
        }
    })?;
    let FilaMedicoNuevo {
        id_medico,
        estado_verificacion,
    } = fila;

    let token = jwt::emitir(
        &id_medico,
        "medico",
        &state.config.jwt_secret,
        state.config.jwt_expiracion_horas,
    )
    .map_err(|e| AppError::Interno(e.to_string()))?;

    Ok(Json(SesionProfesional {
        id_medico,
        token,
        nombre: body.nombre,
        apellidos: body.apellidos,
        tratamiento: body.tratamiento,
        estado_verificacion,
    }))
}
