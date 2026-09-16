//! Gestión de cuentas desde el panel, solo para administradores (migración
//! 0018). El administrador no ve expedientes: aquí solo hay datos de cuenta.
//! Cada acción queda en `app.auditoria_cuentas` con su motivo.

use std::net::SocketAddr;

use axum::{
    extract::{ConnectInfo, Path, State},
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{Postgres, Transaction};

use crate::{
    auth::{
        handlers::{es_violacion_unique, TRATAMIENTOS_VALIDOS},
        jwt::Claims,
        password,
    },
    error::AppError,
    state::AppState,
};

use super::{
    contrasena_temporal,
    error::ErrorPanel,
    handlers::{ip_de, password_operador_incorrecta, validar_codigo_reinicio, validar_motivo},
    sesion_panel::admin_en_sesion,
};

type Resultado<T> = Result<T, ErrorPanel>;

const ESPECIALIDADES: [&str; 10] = [
    "MEDICINA_GENERAL",
    "CARDIOLOGIA",
    "PEDIATRIA",
    "DERMATOLOGIA",
    "GINECOLOGIA",
    "PSIQUIATRIA",
    "GERIATRIA",
    "ENDOCRINOLOGIA",
    "NEUMOLOGIA",
    "NEUROLOGIA",
];

#[derive(Clone, Copy, Debug, PartialEq)]
enum TipoCuenta {
    Medico,
    Paciente,
}

impl TipoCuenta {
    fn nombre(self) -> &'static str {
        match self {
            TipoCuenta::Medico => "medico",
            TipoCuenta::Paciente => "paciente",
        }
    }

    fn tabla_y_columna(self) -> (&'static str, &'static str) {
        match self {
            TipoCuenta::Medico => ("app.medicos", "id_medico"),
            TipoCuenta::Paciente => ("app.pacientes", "id_paciente"),
        }
    }
}

fn cuenta_no_encontrada() -> ErrorPanel {
    ErrorPanel::rechazo(StatusCode::FORBIDDEN, "CUENTA_NO_ENCONTRADA", "Esa cuenta no existe o ya se dio de baja.")
}

fn invalido(codigo: &'static str, mensaje: &'static str) -> ErrorPanel {
    ErrorPanel::rechazo(StatusCode::UNPROCESSABLE_ENTITY, codigo, mensaje)
}

async fn json_de(state: &AppState, consulta: &str) -> Resultado<Json<Value>> {
    Ok(Json(sqlx::query_scalar(consulta).fetch_one(&state.db).await?))
}

async fn nombre_admin(state: &AppState, claims: &Claims) -> Resultado<String> {
    let nombre: Option<String> =
        sqlx::query_scalar("select nombre || ' ' || apellidos from app.administradores where id_admin = $1")
            .bind(&claims.sub)
            .fetch_optional(&state.db)
            .await?;
    Ok(nombre.ok_or(AppError::CuentaInactiva)?)
}

/// Nombre y estado guardado de una cuenta que no se ha dado de baja.
async fn datos_cuenta(state: &AppState, tipo: TipoCuenta, id: &str) -> Resultado<(String, String)> {
    let consulta = match tipo {
        TipoCuenta::Medico => {
            "select app.nombre_completo_medico(tratamiento, nombre, apellidos), estado_cuenta
             from app.medicos where id_medico = $1 and estado_cuenta <> 'eliminada'"
        }
        TipoCuenta::Paciente => {
            "select app.nombre_paciente_panel(id_paciente), estado_cuenta
             from app.pacientes where id_paciente = $1 and estado_cuenta <> 'eliminada'"
        }
    };
    let fila: Option<(String, String)> = sqlx::query_as(consulta).bind(id).fetch_optional(&state.db).await?;
    fila.ok_or_else(cuenta_no_encontrada)
}

struct Evento<'a> {
    tipo: &'static str,
    tipo_cuenta: TipoCuenta,
    id_cuenta: &'a str,
    nombre_cuenta: &'a str,
    claims: &'a Claims,
    nombre_actor: &'a str,
    motivo: Option<&'a str>,
    ip: Option<String>,
}

async fn registrar_evento(tx: &mut Transaction<'_, Postgres>, e: Evento<'_>) -> Resultado<String> {
    Ok(sqlx::query_scalar(
        "insert into app.auditoria_cuentas
           (tipo, tipo_cuenta, id_cuenta, nombre_cuenta, tipo_actor, id_actor, nombre_actor, motivo, ip_origen)
         values ($1, $2, $3, $4, 'admin', $5, $6, $7, $8)
         returning id_evento",
    )
    .bind(e.tipo)
    .bind(e.tipo_cuenta.nombre())
    .bind(e.id_cuenta)
    .bind(e.nombre_cuenta)
    .bind(&e.claims.sub)
    .bind(e.nombre_actor)
    .bind(e.motivo)
    .bind(e.ip)
    .fetch_one(&mut **tx)
    .await?)
}

async fn verificar_password_admin(state: &AppState, claims: &Claims, password_operador: Option<&str>) -> Resultado<()> {
    let hash: Option<String> = sqlx::query_scalar("select hash_contrasena from app.administradores where id_admin = $1")
        .bind(&claims.sub)
        .fetch_optional(&state.db)
        .await?;
    let hash = hash.ok_or(AppError::CuentaInactiva)?;
    if password::verificar(password_operador.unwrap_or(""), &hash, state.config.argon2_secret_key.as_bytes()) {
        Ok(())
    } else {
        tracing::warn!(id_admin = %claims.sub, "acción de cuenta con contraseña de operador incorrecta");
        Err(password_operador_incorrecta())
    }
}

fn sin_cache(cuerpo: Value) -> Response {
    ([(header::CACHE_CONTROL, "no-store")], Json(cuerpo)).into_response()
}

// ---------------------------------------------------------------------------
// Listas
// ---------------------------------------------------------------------------

/// `GET /api/v1/admin/cuentas/medicos`
pub async fn listar_medicos(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    admin_en_sesion(&headers, &state).await?;
    json_de(&state, "select app.panel_admin_medicos()").await
}

/// `GET /api/v1/admin/cuentas/pacientes` (solo datos de la cuenta)
pub async fn listar_pacientes(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    admin_en_sesion(&headers, &state).await?;
    json_de(&state, "select app.panel_admin_pacientes()").await
}

/// `GET /api/v1/admin/cuentas/solicitudes`: recuperaciones de contraseña pendientes.
pub async fn listar_solicitudes(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    admin_en_sesion(&headers, &state).await?;
    json_de(&state, "select app.panel_admin_solicitudes()").await
}

// ---------------------------------------------------------------------------
// Alta de médico
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
pub struct MedicoNuevo {
    correo: String,
    nombre: String,
    apellidos: String,
    tratamiento: String,
    cedula_profesional: String,
    especialidad: Option<String>,
    motivo: Option<String>,
}

/// Lo que se guarda de un alta, ya validado.
#[derive(Debug, PartialEq)]
struct AltaValida {
    correo: String,
    nombre: String,
    apellidos: String,
    tratamiento: String,
    cedula: String,
    especialidad: Option<String>,
}

fn validar_alta(body: &MedicoNuevo) -> Resultado<AltaValida> {
    let correo = body.correo.trim().to_lowercase();
    let partes: Vec<&str> = correo.split('@').collect();
    if correo.len() > 254 || partes.len() != 2 || partes[0].is_empty() || !partes[1].contains('.') {
        return Err(invalido("CORREO_NO_VALIDO", "Escribe un correo válido."));
    }
    let nombre = body.nombre.trim().to_string();
    let apellidos = body.apellidos.trim().to_string();
    if nombre.is_empty() || apellidos.is_empty() || nombre.chars().count() > 100 || apellidos.chars().count() > 100 {
        return Err(invalido("NOMBRE_REQUERIDO", "Escribe el nombre y los apellidos del médico."));
    }
    let tratamiento = body.tratamiento.trim().to_string();
    if !TRATAMIENTOS_VALIDOS.contains(&tratamiento.as_str()) {
        return Err(invalido("TRATAMIENTO_NO_VALIDO", "El tratamiento debe ser Dr., Dra. o Dr(a)."));
    }
    let cedula = body.cedula_profesional.trim().to_uppercase();
    if cedula.is_empty() || cedula.len() > 20 || !cedula.chars().all(|c| c.is_ascii_alphanumeric()) {
        return Err(invalido("CEDULA_NO_VALIDA", "Escribe la cédula profesional (solo letras y números)."));
    }
    let especialidad = match body.especialidad.as_deref().map(str::trim).filter(|e| !e.is_empty()) {
        None => None,
        Some(e) if ESPECIALIDADES.contains(&e) => Some(e.to_string()),
        Some(_) => return Err(invalido("ESPECIALIDAD_NO_VALIDA", "Elige una especialidad de la lista.")),
    };
    Ok(AltaValida { correo, nombre, apellidos, tratamiento, cedula, especialidad })
}

/// `POST /api/v1/admin/cuentas/medicos`. El alta la avala el administrador,
/// así que la cédula queda aprobada. Da una contraseña temporal que el médico
/// cambia al entrar al panel.
pub async fn crear_medico(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Json(body): Json<MedicoNuevo>,
) -> Resultado<Response> {
    let claims = admin_en_sesion(&headers, &state).await?;
    let alta = validar_alta(&body)?;
    let motivo = body.motivo.as_deref().map(str::trim).filter(|m| !m.is_empty()).unwrap_or("Alta desde el panel");
    let actor = nombre_admin(&state, &claims).await?;

    let temporal = contrasena_temporal::generar();
    let hash = password::hash(&temporal, state.config.argon2_secret_key.as_bytes()).map_err(AppError::Interno)?;

    let mut tx = state.db.begin().await?;
    let (id_medico, nombre_completo): (String, String) = sqlx::query_as(
        "insert into app.medicos
           (correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional, especialidad,
            estado_verificacion, debe_cambiar_contrasena, contrasena_temporal_expira_en)
         values ($1, $2, $3, $4, $5, $6, $7, 'APROBADO', true,
                 now() + make_interval(hours => app.config_entero('horas_validez_contrasena_temporal')))
         returning id_medico, app.nombre_completo_medico(tratamiento, nombre, apellidos)",
    )
    .bind(&alta.correo)
    .bind(&hash)
    .bind(&alta.nombre)
    .bind(&alta.apellidos)
    .bind(&alta.tratamiento)
    .bind(&alta.cedula)
    .bind(&alta.especialidad)
    .fetch_one(&mut *tx)
    .await
    .map_err(|e| {
        if es_violacion_unique(&e, "cedula_profesional") {
            ErrorPanel::from(AppError::CedulaYaRegistrada)
        } else if es_violacion_unique(&e, "correo") {
            ErrorPanel::from(AppError::CorreoYaRegistrado)
        } else {
            ErrorPanel::from(e)
        }
    })?;

    let evento_id = registrar_evento(
        &mut tx,
        Evento {
            tipo: "crear_doctor",
            tipo_cuenta: TipoCuenta::Medico,
            id_cuenta: &id_medico,
            nombre_cuenta: &nombre_completo,
            claims: &claims,
            nombre_actor: &actor,
            motivo: Some(motivo),
            ip: ip_de(&headers, conexion),
        },
    )
    .await?;
    tx.commit().await?;

    tracing::info!(id_admin = %claims.sub, %id_medico, "alta de médico desde el panel");
    Ok(sin_cache(json!({
        "ok": true,
        "evento_id": evento_id,
        "id_medico": id_medico,
        "password_temporal": temporal,
    })))
}

// ---------------------------------------------------------------------------
// Cédula
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
pub struct CambioVerificacion {
    estado: String,
    motivo: Option<String>,
}

/// `PATCH /api/v1/admin/cuentas/medicos/{id}/verificacion` con
/// `{"estado": "APROBADO" | "RECHAZADO", "motivo"?}`.
pub async fn verificar_cedula(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id_medico): Path<String>,
    Json(body): Json<CambioVerificacion>,
) -> Resultado<Json<Value>> {
    let claims = admin_en_sesion(&headers, &state).await?;
    let (estado, tipo_evento) = match body.estado.trim().to_uppercase().as_str() {
        "APROBADO" => ("APROBADO", "aprobar_cedula"),
        "RECHAZADO" => ("RECHAZADO", "rechazar_cedula"),
        _ => {
            return Err(ErrorPanel::rechazo(
                StatusCode::BAD_REQUEST,
                "ESTADO_NO_VALIDO",
                "El estado debe ser APROBADO o RECHAZADO.",
            ))
        }
    };
    let (nombre_cuenta, _) = datos_cuenta(&state, TipoCuenta::Medico, &id_medico).await?;
    let actor = nombre_admin(&state, &claims).await?;

    let mut tx = state.db.begin().await?;
    sqlx::query("update app.medicos set estado_verificacion = $2 where id_medico = $1")
        .bind(&id_medico)
        .bind(estado)
        .execute(&mut *tx)
        .await?;
    let motivo = body.motivo.as_deref().map(str::trim).filter(|m| !m.is_empty());
    registrar_evento(
        &mut tx,
        Evento {
            tipo: tipo_evento,
            tipo_cuenta: TipoCuenta::Medico,
            id_cuenta: &id_medico,
            nombre_cuenta: &nombre_cuenta,
            claims: &claims,
            nombre_actor: &actor,
            motivo,
            ip: ip_de(&headers, conexion),
        },
    )
    .await?;
    tx.commit().await?;

    Ok(Json(json!({ "ok": true, "id": id_medico, "estado_verificacion": estado })))
}

// ---------------------------------------------------------------------------
// Suspender / reactivar
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
pub struct CambioEstadoCuenta {
    estado: String,
    motivo: Option<String>,
}

async fn cambiar_estado(
    state: AppState,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    tipo: TipoCuenta,
    id: String,
    body: CambioEstadoCuenta,
) -> Resultado<Json<Value>> {
    let claims = admin_en_sesion(&headers, &state).await?;
    let (estado_guardado, tipo_evento) = match body.estado.trim().to_lowercase().as_str() {
        "suspendida" => ("bloqueada", "suspender_cuenta"),
        "activa" => ("activa", "activar_cuenta"),
        _ => {
            return Err(ErrorPanel::rechazo(
                StatusCode::BAD_REQUEST,
                "ESTADO_NO_VALIDO",
                "El estado debe ser activa o suspendida.",
            ))
        }
    };
    let motivo = validar_motivo(body.motivo.as_deref())?;
    let (nombre_cuenta, estado_actual) = datos_cuenta(&state, tipo, &id).await?;
    let (tabla, columna) = tipo.tabla_y_columna();

    if estado_actual != estado_guardado {
        let actor = nombre_admin(&state, &claims).await?;
        let mut tx = state.db.begin().await?;
        // Al suspender se cierran sus sesiones; al reactivar, los tokens de
        // antes de la suspensión siguen sin valer.
        sqlx::query(&format!(
            "update {tabla} set estado_cuenta = $2,
                    sesiones_validas_desde = case when $2 = 'bloqueada' then now() else sesiones_validas_desde end
              where {columna} = $1"
        ))
        .bind(&id)
        .bind(estado_guardado)
        .execute(&mut *tx)
        .await?;
        registrar_evento(
            &mut tx,
            Evento {
                tipo: tipo_evento,
                tipo_cuenta: tipo,
                id_cuenta: &id,
                nombre_cuenta: &nombre_cuenta,
                claims: &claims,
                nombre_actor: &actor,
                motivo: Some(&motivo),
                ip: ip_de(&headers, conexion),
            },
        )
        .await?;
        tx.commit().await?;
        tracing::info!(id_admin = %claims.sub, tipo = tipo.nombre(), %id, estado = estado_guardado, "estado de cuenta cambiado");
    }

    let estado_cuenta: String = sqlx::query_scalar(&format!(
        "select app.estado_cuenta_panel(estado_cuenta, debe_cambiar_contrasena) from {tabla} where {columna} = $1"
    ))
    .bind(&id)
    .fetch_one(&state.db)
    .await?;
    Ok(Json(json!({ "ok": true, "id": id, "estado_cuenta": estado_cuenta })))
}

/// `PATCH /api/v1/admin/cuentas/medicos/{id}/estado` con `{"estado": "activa"|"suspendida", "motivo"}`
pub async fn estado_medico(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id): Path<String>,
    Json(body): Json<CambioEstadoCuenta>,
) -> Resultado<Json<Value>> {
    cambiar_estado(state, headers, conexion, TipoCuenta::Medico, id, body).await
}

/// `PATCH /api/v1/admin/cuentas/pacientes/{id}/estado`
pub async fn estado_paciente(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id): Path<String>,
    Json(body): Json<CambioEstadoCuenta>,
) -> Resultado<Json<Value>> {
    cambiar_estado(state, headers, conexion, TipoCuenta::Paciente, id, body).await
}

// ---------------------------------------------------------------------------
// Restablecer contraseña
// ---------------------------------------------------------------------------

#[derive(Deserialize, Default)]
pub struct CuerpoRestablecer {
    motivo: Option<String>,
    codigo_confirmacion: Option<String>,
    password_operador: Option<String>,
}

async fn restablecer(
    state: AppState,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    tipo: TipoCuenta,
    id: String,
    body: CuerpoRestablecer,
) -> Resultado<Response> {
    let claims = admin_en_sesion(&headers, &state).await?;
    validar_codigo_reinicio(body.codigo_confirmacion.as_deref())?;
    let motivo = validar_motivo(body.motivo.as_deref())?;
    verificar_password_admin(&state, &claims, body.password_operador.as_deref()).await?;
    let (nombre_cuenta, _) = datos_cuenta(&state, tipo, &id).await?;
    let actor = nombre_admin(&state, &claims).await?;

    let temporal = contrasena_temporal::generar();
    let hash = password::hash(&temporal, state.config.argon2_secret_key.as_bytes()).map_err(AppError::Interno)?;
    let (tabla, columna) = tipo.tabla_y_columna();

    let mut tx = state.db.begin().await?;
    sqlx::query(&format!(
        "update {tabla}
            set hash_contrasena = $2,
                debe_cambiar_contrasena = true,
                contrasena_temporal_expira_en =
                  now() + make_interval(hours => app.config_entero('horas_validez_contrasena_temporal')),
                sesiones_validas_desde = now()
          where {columna} = $1"
    ))
    .bind(&id)
    .bind(&hash)
    .execute(&mut *tx)
    .await?;
    let evento_id = registrar_evento(
        &mut tx,
        Evento {
            tipo: "reset_cuenta",
            tipo_cuenta: tipo,
            id_cuenta: &id,
            nombre_cuenta: &nombre_cuenta,
            claims: &claims,
            nombre_actor: &actor,
            motivo: Some(&motivo),
            ip: ip_de(&headers, conexion),
        },
    )
    .await?;
    sqlx::query(
        "update app.solicitudes_recuperacion set atendida_en = now(), id_evento = $3
          where tipo_cuenta = $1 and id_cuenta = $2 and atendida_en is null",
    )
    .bind(tipo.nombre())
    .bind(&id)
    .bind(&evento_id)
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;

    tracing::info!(id_admin = %claims.sub, tipo = tipo.nombre(), %id, %evento_id, "contraseña restablecida desde el panel");
    Ok(sin_cache(json!({ "ok": true, "evento_id": evento_id, "password_temporal": temporal })))
}

/// `PATCH /api/v1/admin/cuentas/medicos/{id}/reset-contrasena` con
/// `{"motivo", "codigo_confirmacion": "REINICIO", "password_operador"}`
pub async fn restablecer_medico(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id): Path<String>,
    cuerpo: Option<Json<CuerpoRestablecer>>,
) -> Resultado<Response> {
    let body = cuerpo.map(|Json(c)| c).unwrap_or_default();
    restablecer(state, headers, conexion, TipoCuenta::Medico, id, body).await
}

/// `PATCH /api/v1/admin/cuentas/pacientes/{id}/reset-contrasena`
pub async fn restablecer_paciente(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id): Path<String>,
    cuerpo: Option<Json<CuerpoRestablecer>>,
) -> Resultado<Response> {
    let body = cuerpo.map(|Json(c)| c).unwrap_or_default();
    restablecer(state, headers, conexion, TipoCuenta::Paciente, id, body).await
}

#[cfg(test)]
mod tests {
    use super::*;

    fn alta(correo: &str, tratamiento: &str, cedula: &str, especialidad: Option<&str>) -> MedicoNuevo {
        MedicoNuevo {
            correo: correo.into(),
            nombre: " Elena ".into(),
            apellidos: "Ruiz".into(),
            tratamiento: tratamiento.into(),
            cedula_profesional: cedula.into(),
            especialidad: especialidad.map(Into::into),
            motivo: None,
        }
    }

    #[test]
    fn el_alta_normaliza_correo_nombre_y_cedula() {
        let v = validar_alta(&alta(" Dra.Ruiz@Clinica.MX ", "Dra.", "ab12345", Some("CARDIOLOGIA"))).unwrap();
        assert_eq!(v.correo, "dra.ruiz@clinica.mx");
        assert_eq!(v.nombre, "Elena");
        assert_eq!(v.cedula, "AB12345");
        assert_eq!(v.especialidad.as_deref(), Some("CARDIOLOGIA"));
        assert_eq!(validar_alta(&alta("a@b.mx", "Dr.", "1", Some(" "))).unwrap().especialidad, None);
    }

    #[test]
    fn el_alta_rechaza_datos_fuera_de_dominio() {
        for m in [
            alta("sin-arroba", "Dr.", "123", None),
            alta("a@b", "Dr.", "123", None),
            alta("a@b.mx", "Licenciado", "123", None),
            alta("a@b.mx", "Dr.", "12-34", None),
            alta("a@b.mx", "Dr.", "123", Some("ASTROLOGIA")),
        ] {
            assert!(validar_alta(&m).is_err());
        }
    }
}
