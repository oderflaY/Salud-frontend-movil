use axum::{
    extract::State,
    http::{HeaderMap, StatusCode},
    response::Html,
    Json,
};

use crate::{error::AppError, state::AppState};

use super::{
    jwt,
    models::{
        CambiarContrasenaRequest, CrearCuentaPacienteRequest, CrearCuentaProfesionalRequest,
        IniciarSesionRequest, SesionPaciente, SesionProfesional,
    },
    password,
    sesion::{self, EstadoSesion},
};

pub(crate) const TRATAMIENTOS_VALIDOS: [&str; 3] = ["Dr.", "Dra.", "Dr(a)."];

/// La misma regla que la app (`ValidadorLogin.MINIMO_CARACTERES_CONTRASENA`).
/// El tope evita que alguien mande megas a Argon2.
pub const MINIMO_CARACTERES_CONTRASENA: usize = 8;
const MAXIMO_CARACTERES_CONTRASENA: usize = 128;

pub fn validar_contrasena_nueva(contrasena: &str) -> Result<(), AppError> {
    let largo = contrasena.chars().count();
    if (MINIMO_CARACTERES_CONTRASENA..=MAXIMO_CARACTERES_CONTRASENA).contains(&largo) {
        Ok(())
    } else {
        Err(AppError::ContrasenaNoValida)
    }
}

pub(crate) fn es_violacion_unique(err: &sqlx::Error, columna_hint: &str) -> bool {
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
    debe_cambiar_contrasena: bool,
    temporal_vencida: bool,
}

pub async fn iniciar_sesion_paciente(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<Json<SesionPaciente>, AppError> {
    let fila = sqlx::query_as::<_, FilaPaciente>(
        "select id_paciente, hash_contrasena, estado_cuenta, requiere_onboarding,
                debe_cambiar_contrasena,
                coalesce(contrasena_temporal_expira_en < now(), false) as temporal_vencida
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

    // Despues de verificar, no antes: sin la contrasena correcta nadie se
    // entera de que la cuenta tiene una temporal pendiente.
    if fila.debe_cambiar_contrasena && fila.temporal_vencida {
        return Err(AppError::ContrasenaTemporalVencida);
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
        requiere_cambio_contrasena: fila.debe_cambiar_contrasena,
    }))
}

#[derive(sqlx::FromRow)]
struct FilaCambioContrasena {
    hash_contrasena: String,
    requiere_onboarding: bool,
    temporal_vencida: bool,
}

/// Cambio de contrasena del paciente: el obligatorio tras un reset del medico
/// (dashboard) y el voluntario desde Ajustes.
///
/// Cierra las demas sesiones de la cuenta (otro telefono, o quien haya usado
/// la temporal) y devuelve un token nuevo para que la app siga sin pedir
/// acceso otra vez.
pub async fn cambiar_contrasena_paciente(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(body): Json<CambiarContrasenaRequest>,
) -> Result<Json<SesionPaciente>, AppError> {
    let claims = jwt::autenticar(&headers, &state.config.jwt_secret)?;
    if claims.role != "paciente" {
        return Err(AppError::AccesoDenegado);
    }
    // Esta es la unica ruta que acepta una sesion en cambio obligatorio.
    let obligatorio = sesion::estado(&state.db, &claims).await? == EstadoSesion::CambioDeContrasenaRequerido;

    validar_contrasena_nueva(&body.contrasena_nueva)?;

    let fila = sqlx::query_as::<_, FilaCambioContrasena>(
        "select hash_contrasena, requiere_onboarding,
                coalesce(contrasena_temporal_expira_en < now(), false) as temporal_vencida
         from app.pacientes where id_paciente = $1",
    )
    .bind(&claims.sub)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CuentaInactiva)?;

    if obligatorio && fila.temporal_vencida {
        return Err(AppError::ContrasenaTemporalVencida);
    }

    let pepper = state.config.argon2_secret_key.as_bytes();
    // Fuera del cambio obligatorio, un token robado no debe bastar para
    // quedarse con la cuenta: hace falta la contrasena actual.
    match body.contrasena_actual.as_deref() {
        Some(actual) if !password::verificar(actual, &fila.hash_contrasena, pepper) => {
            return Err(AppError::CredencialesInvalidas);
        }
        None if !obligatorio => return Err(AppError::SolicitudInvalida),
        _ => {}
    }
    if password::verificar(&body.contrasena_nueva, &fila.hash_contrasena, pepper) {
        return Err(AppError::ContrasenaNoValida);
    }

    let hash = password::hash(&body.contrasena_nueva, pepper).map_err(AppError::Interno)?;
    sqlx::query(
        "update app.pacientes
            set hash_contrasena = $2,
                debe_cambiar_contrasena = false,
                contrasena_temporal_expira_en = null,
                sesiones_validas_desde = now()
          where id_paciente = $1",
    )
    .bind(&claims.sub)
    .bind(&hash)
    .execute(&state.db)
    .await?;

    let token = jwt::emitir(
        &claims.sub,
        "paciente",
        &state.config.jwt_secret,
        state.config.jwt_expiracion_horas,
    )
    .map_err(|e| AppError::Interno(e.to_string()))?;
    tracing::info!(id_paciente = %claims.sub, obligatorio, "cambio de contrasena de paciente");

    Ok(Json(SesionPaciente {
        id_paciente: claims.sub,
        token,
        requiere_onboarding: fila.requiere_onboarding,
        requiere_cambio_contrasena: false,
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
    validar_contrasena_nueva(&body.contrasena)?;
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
        requiere_cambio_contrasena: false,
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
    debe_cambiar_contrasena: bool,
    temporal_vencida: bool,
}

pub async fn iniciar_sesion_profesional(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<Json<SesionProfesional>, AppError> {
    let fila = sqlx::query_as::<_, FilaMedico>(
        "select id_medico, hash_contrasena, estado_cuenta, nombre, apellidos,
                tratamiento, estado_verificacion, debe_cambiar_contrasena,
                coalesce(contrasena_temporal_expira_en < now(), false) as temporal_vencida
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
    // La app del médico no obliga a cambiar la temporal (eso lo hace el panel
    // web), pero una temporal vencida no entra en ningún lado.
    if fila.debe_cambiar_contrasena && fila.temporal_vencida {
        return Err(AppError::ContrasenaTemporalVencida);
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
    validar_contrasena_nueva(&body.contrasena)?;

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

// ---------------------------------------------------------------------------
// Baja de cuenta (requisito de Google Play)
// ---------------------------------------------------------------------------
//
// Se pide correo y contrasena otra vez, no el token de sesion: la misma ruta
// sirve a la app y a la pagina web publica /auth/eliminar-cuenta, donde no hay
// sesion. Una cuenta bloqueada tambien puede darse de baja: bloquear el acceso
// no puede servir para impedir que alguien pida borrar sus datos.
//
// Que se borra y que se conserva lo decide SQL (0012_baja_de_cuentas.sql), no
// este handler: aqui solo se comprueba quien es.

pub async fn dar_de_baja_paciente(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<StatusCode, AppError> {
    let (id_paciente, hash): (String, String) = sqlx::query_as(
        "select id_paciente, hash_contrasena from app.pacientes where correo = $1",
    )
    .bind(&body.correo)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CredencialesInvalidas)?;

    if !password::verificar(&body.contrasena, &hash, state.config.argon2_secret_key.as_bytes()) {
        return Err(AppError::CredencialesInvalidas);
    }

    sqlx::query("select app.dar_de_baja_paciente($1)")
        .bind(&id_paciente)
        .execute(&state.db)
        .await?;
    tracing::info!(%id_paciente, "baja de cuenta de paciente");
    Ok(StatusCode::NO_CONTENT)
}

pub async fn dar_de_baja_profesional(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<StatusCode, AppError> {
    let (id_medico, hash): (String, String) = sqlx::query_as(
        "select id_medico, hash_contrasena from app.medicos where correo = $1",
    )
    .bind(&body.correo)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CredencialesInvalidas)?;

    if !password::verificar(&body.contrasena, &hash, state.config.argon2_secret_key.as_bytes()) {
        return Err(AppError::CredencialesInvalidas);
    }

    sqlx::query("select app.dar_de_baja_medico($1)")
        .bind(&id_medico)
        .execute(&state.db)
        .await?;
    tracing::info!(%id_medico, "baja de cuenta de profesional");
    Ok(StatusCode::NO_CONTENT)
}

/// Pagina publica que Google Play exige para pedir la baja sin instalar la app.
/// La sirve Axum y no Caddy para no tocar el despliegue: `/auth/*` ya llega aqui.
pub async fn pagina_eliminar_cuenta() -> Html<&'static str> {
    Html(include_str!("eliminar_cuenta.html"))
}
