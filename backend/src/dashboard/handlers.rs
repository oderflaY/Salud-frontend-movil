use std::net::SocketAddr;

use axum::{
    extract::{ConnectInfo, Path, Query, State},
    http::{header, HeaderMap},
    response::IntoResponse,
    Json,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::{
    auth::{
        jwt::{self, Claims},
        models::IniciarSesionRequest,
        password, sesion,
    },
    error::AppError,
    state::AppState,
};

use super::contrasena_temporal;

const ESTADOS_ALERTA: [&str; 4] = ["NUEVA", "EN_REVISION", "ATENDIDA", "DESCARTADA"];
const PRIORIDADES_ALERTA: [&str; 3] = ["ALTA", "MEDIA", "BAJA"];
const MAXIMO_CARACTERES_MOTIVO: usize = 500;
const MAXIMO_CARACTERES_NOTA: usize = 1000;

/// El médico que usa el dashboard: firma válida, rol médico, sesión vigente
/// (ni reseteada ni de una cuenta bloqueada o dada de baja).
async fn medico_en_sesion(headers: &HeaderMap, state: &AppState) -> Result<Claims, AppError> {
    let claims = sesion::autenticar(headers, state).await?;
    if claims.role != "medico" {
        return Err(AppError::AccesoDenegado);
    }
    Ok(claims)
}

/// Las alertas se derivan de datos que cambian solos (el diario, las tomas):
/// antes de leer cualquier cosa que las cuente, se ponen al día.
async fn sincronizar_alertas(state: &AppState, id_medico: &str) -> Result<(), AppError> {
    sqlx::query("select app.sincronizar_alertas($1)")
        .bind(id_medico)
        .execute(&state.db)
        .await?;
    Ok(())
}

async fn json_de(state: &AppState, consulta: &str, id_medico: &str) -> Result<Json<Value>, AppError> {
    let valor: Value = sqlx::query_scalar(consulta)
        .bind(id_medico)
        .fetch_one(&state.db)
        .await?;
    Ok(Json(valor))
}

fn iso_utc(instante: DateTime<Utc>) -> String {
    instante.format("%Y-%m-%dT%H:%M:%SZ").to_string()
}

/// Detrás de Caddy, `ConnectInfo` solo ve la IP del proxy; Caddy pone la del
/// cliente en X-Forwarded-For (igual que en `crate::emergencia`).
fn ip_de(headers: &HeaderMap, conexion: Option<ConnectInfo<SocketAddr>>) -> Option<String> {
    headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next())
        .map(|ip| ip.trim().to_string())
        .filter(|ip| !ip.is_empty())
        .or_else(|| conexion.map(|ConnectInfo(addr)| addr.ip().to_string()))
}

// ---------------------------------------------------------------------------
// Sesión
// ---------------------------------------------------------------------------

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SesionDashboard {
    id_medico: String,
    rol: &'static str,
    correo: String,
    nombre: String,
    apellidos: String,
    tratamiento: String,
    nombre_completo: String,
    cedula_profesional: String,
    especialidad: Option<String>,
    estado_verificacion: String,
    expira_en: String,
}

#[derive(sqlx::FromRow)]
struct FilaPerfil {
    id_medico: String,
    correo: String,
    nombre: String,
    apellidos: String,
    tratamiento: String,
    cedula_profesional: String,
    especialidad: Option<String>,
    estado_verificacion: String,
}

async fn perfil(state: &AppState, id_medico: &str, exp: usize) -> Result<SesionDashboard, AppError> {
    let fila = sqlx::query_as::<_, FilaPerfil>(
        "select id_medico, correo::text as correo, nombre, apellidos, tratamiento,
                cedula_profesional, especialidad, estado_verificacion
         from app.medicos where id_medico = $1",
    )
    .bind(id_medico)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CuentaInactiva)?;

    let expira_en = DateTime::<Utc>::from_timestamp(exp as i64, 0)
        .map(iso_utc)
        .unwrap_or_default();

    Ok(SesionDashboard {
        nombre_completo: format!("{} {} {}", fila.tratamiento, fila.nombre, fila.apellidos),
        id_medico: fila.id_medico,
        rol: "medico",
        correo: fila.correo,
        nombre: fila.nombre,
        apellidos: fila.apellidos,
        tratamiento: fila.tratamiento,
        cedula_profesional: fila.cedula_profesional,
        especialidad: fila.especialidad,
        estado_verificacion: fila.estado_verificacion,
        expira_en,
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RespuestaInicioSesion {
    token: String,
    expira_en: String,
    usuario: SesionDashboard,
}

/// `POST /api/v1/auth/sesion`: la misma cuenta del médico que usa la app.
/// El dashboard guarda `token` en `localStorage('auth_token')`.
pub async fn iniciar_sesion(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Result<Json<RespuestaInicioSesion>, AppError> {
    let (id_medico, hash, estado_cuenta): (String, String, String) = sqlx::query_as(
        "select id_medico, hash_contrasena, estado_cuenta from app.medicos where correo = $1",
    )
    .bind(&body.correo)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::CredencialesInvalidas)?;

    if estado_cuenta == "bloqueada" {
        return Err(AppError::CuentaBloqueada);
    }
    if !password::verificar(&body.contrasena, &hash, state.config.argon2_secret_key.as_bytes()) {
        return Err(AppError::CredencialesInvalidas);
    }

    let token = jwt::emitir(&id_medico, "medico", &state.config.jwt_secret, state.config.jwt_expiracion_horas)
        .map_err(|e| AppError::Interno(e.to_string()))?;
    let claims = jwt::verificar(&token, &state.config.jwt_secret).map_err(|e| AppError::Interno(e.to_string()))?;
    let usuario = perfil(&state, &id_medico, claims.exp).await?;

    Ok(Json(RespuestaInicioSesion {
        token,
        expira_en: usuario.expira_en.clone(),
        usuario,
    }))
}

/// `GET /api/v1/auth/me`
pub async fn sesion_actual(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<SesionDashboard>, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    Ok(Json(perfil(&state, &claims.sub, claims.exp).await?))
}

// ---------------------------------------------------------------------------
// Lecturas
// ---------------------------------------------------------------------------

/// `GET /api/v1/admin/dashboard/kpis`
pub async fn kpis(State(state): State<AppState>, headers: HeaderMap) -> Result<Json<Value>, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    sincronizar_alertas(&state, &claims.sub).await?;
    json_de(&state, "select app.dashboard_kpis($1)", &claims.sub).await
}

/// `GET /api/v1/admin/pacientes`: solo los que tienen vínculo vigente con él.
pub async fn pacientes(State(state): State<AppState>, headers: HeaderMap) -> Result<Json<Value>, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    sincronizar_alertas(&state, &claims.sub).await?;
    json_de(&state, "select app.dashboard_pacientes($1)", &claims.sub).await
}

/// `GET /api/v1/admin/auditoria`
pub async fn auditoria(State(state): State<AppState>, headers: HeaderMap) -> Result<Json<Value>, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    json_de(&state, "select app.dashboard_auditoria($1)", &claims.sub).await
}

// ---------------------------------------------------------------------------
// Alertas
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
pub struct FiltroAlertas {
    estado: Option<String>,
    prioridad: Option<String>,
}

/// Acepta el valor en cualquier mayúscula/minúscula; devuelve el canónico.
fn valor_permitido(valor: Option<&str>, permitidos: &[&str]) -> Result<Option<String>, AppError> {
    match valor.map(str::trim).filter(|v| !v.is_empty()) {
        None => Ok(None),
        Some(v) => {
            let v = v.to_uppercase();
            if permitidos.contains(&v.as_str()) {
                Ok(Some(v))
            } else {
                Err(AppError::SolicitudInvalida)
            }
        }
    }
}

/// `GET /api/v1/admin/alertas?estado=NUEVA&prioridad=ALTA` (filtros opcionales)
pub async fn alertas(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(filtro): Query<FiltroAlertas>,
) -> Result<Json<Value>, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    let estado = valor_permitido(filtro.estado.as_deref(), &ESTADOS_ALERTA)?;
    let prioridad = valor_permitido(filtro.prioridad.as_deref(), &PRIORIDADES_ALERTA)?;
    sincronizar_alertas(&state, &claims.sub).await?;

    let valor: Value = sqlx::query_scalar("select app.dashboard_alertas($1, $2, $3)")
        .bind(&claims.sub)
        .bind(estado)
        .bind(prioridad)
        .fetch_one(&state.db)
        .await?;
    Ok(Json(valor))
}

#[derive(Deserialize)]
pub struct CambioEstadoAlerta {
    estado: String,
    nota: Option<String>,
}

/// `PATCH /api/v1/admin/alertas/{idAlerta}/estado` con `{"estado", "nota"?}`.
/// Devuelve la alerta ya actualizada.
pub async fn cambiar_estado_alerta(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id_alerta): Path<String>,
    Json(body): Json<CambioEstadoAlerta>,
) -> Result<Json<Value>, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    let estado = valor_permitido(Some(&body.estado), &ESTADOS_ALERTA)?.ok_or(AppError::SolicitudInvalida)?;
    let nota = body.nota.map(|n| n.trim().to_string());
    if nota.as_deref().is_some_and(|n| n.chars().count() > MAXIMO_CARACTERES_NOTA) {
        return Err(AppError::SolicitudInvalida);
    }

    // Una nota ausente conserva la anterior; una vacía la borra.
    let actualizada: Option<String> = sqlx::query_scalar(
        "update app.alertas
            set estado = $3, nota = case when $4::text is null then nota else nullif($4, '') end
          where id_alerta = $1 and id_medico = $2 and app.vinculo_vigente($2, id_paciente)
          returning id_alerta",
    )
    .bind(&id_alerta)
    .bind(&claims.sub)
    .bind(&estado)
    .bind(&nota)
    .fetch_optional(&state.db)
    .await?;

    let id_alerta = actualizada.ok_or(AppError::AlertaNoEncontrada)?;
    let valor: Value = sqlx::query_scalar("select app.alerta_a_jsonb($1)")
        .bind(&id_alerta)
        .fetch_one(&state.db)
        .await?;
    Ok(Json(valor))
}

// ---------------------------------------------------------------------------
// Reset de cuenta
// ---------------------------------------------------------------------------

#[derive(Deserialize, Default)]
pub struct CuerpoReset {
    motivo: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RespuestaReset {
    id_paciente: String,
    /// Se muestra UNA vez. No se guarda en ningún lado: si se pierde, se
    /// resetea otra vez.
    contrasena_temporal: String,
    expira_en: String,
    sesiones_cerradas: bool,
    id_auditoria: String,
    reseteado_en: String,
    instrucciones: String,
}

/// `PATCH /api/v1/admin/usuarios/{id_paciente}/reset-cuenta` con `{"motivo"?}`.
///
/// Decisión del dueño del producto (2026-09-14): contraseña temporal que el
/// médico entrega al paciente. Cierra sus sesiones abiertas y la app le obliga
/// a elegir una nueva al entrar. No toca el expediente.
///
/// Solo un médico con la cédula aprobada y con vínculo vigente (de cualquier
/// nivel: el paciente eligió a ese médico). Queda en la auditoría, que ven
/// también los demás médicos del paciente.
pub async fn resetear_cuenta(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id_paciente): Path<String>,
    cuerpo: Option<Json<CuerpoReset>>,
) -> Result<impl IntoResponse, AppError> {
    let claims = medico_en_sesion(&headers, &state).await?;
    let motivo = cuerpo
        .and_then(|Json(c)| c.motivo)
        .map(|m| m.trim().to_string())
        .filter(|m| !m.is_empty());
    if motivo.as_deref().is_some_and(|m| m.chars().count() > MAXIMO_CARACTERES_MOTIVO) {
        return Err(AppError::SolicitudInvalida);
    }

    let verificacion: String = sqlx::query_scalar("select estado_verificacion from app.medicos where id_medico = $1")
        .bind(&claims.sub)
        .fetch_one(&state.db)
        .await?;
    if verificacion != "APROBADO" {
        return Err(AppError::MedicoNoVerificado);
    }

    let vinculado: bool = sqlx::query_scalar("select app.vinculo_vigente($1, $2)")
        .bind(&claims.sub)
        .bind(&id_paciente)
        .fetch_one(&state.db)
        .await?;
    if !vinculado {
        return Err(AppError::PacienteNoEncontrado);
    }

    let temporal = contrasena_temporal::generar();
    let hash = password::hash(&temporal, state.config.argon2_secret_key.as_bytes()).map_err(AppError::Interno)?;

    let mut tx = state.db.begin().await?;
    let expira_en: DateTime<Utc> = sqlx::query_scalar(
        "update app.pacientes
            set hash_contrasena = $2,
                debe_cambiar_contrasena = true,
                contrasena_temporal_expira_en =
                  now() + make_interval(hours => app.config_entero('horas_validez_contrasena_temporal')),
                sesiones_validas_desde = now()
          where id_paciente = $1 and estado_cuenta <> 'eliminada'
          returning contrasena_temporal_expira_en",
    )
    .bind(&id_paciente)
    .bind(&hash)
    .fetch_optional(&mut *tx)
    .await?
    .ok_or(AppError::PacienteNoEncontrado)?;

    let (id_auditoria, reseteado_en): (String, DateTime<Utc>) = sqlx::query_as(
        "insert into app.auditoria_reset_cuenta (id_paciente, id_medico, motivo, ip_origen)
         values ($1, $2, $3, $4)
         returning id_auditoria, creado_en",
    )
    .bind(&id_paciente)
    .bind(&claims.sub)
    .bind(&motivo)
    .bind(ip_de(&headers, conexion))
    .fetch_one(&mut *tx)
    .await?;
    tx.commit().await?;

    tracing::info!(id_medico = %claims.sub, %id_paciente, %id_auditoria, "reset de cuenta de paciente");

    let horas = (expira_en - reseteado_en).num_hours().max(1);
    let respuesta = RespuestaReset {
        id_paciente,
        contrasena_temporal: temporal,
        expira_en: iso_utc(expira_en),
        sesiones_cerradas: true,
        id_auditoria,
        reseteado_en: iso_utc(reseteado_en),
        instrucciones: format!(
            "Entrega esta contraseña al paciente. Al entrar a la app con su correo y esta contraseña, \
             la app le pedirá elegir una nueva. Vence en {horas} horas. Sus sesiones abiertas ya se cerraron."
        ),
    };
    // La contraseña no debe quedar en ninguna caché del navegador ni de un proxy.
    Ok(([(header::CACHE_CONTROL, "no-store")], Json(respuesta)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn los_filtros_aceptan_minusculas_y_devuelven_el_valor_canonico() {
        assert_eq!(valor_permitido(Some("en_revision"), &ESTADOS_ALERTA).unwrap().as_deref(), Some("EN_REVISION"));
        assert_eq!(valor_permitido(Some("  "), &ESTADOS_ALERTA).unwrap(), None);
        assert_eq!(valor_permitido(None, &PRIORIDADES_ALERTA).unwrap(), None);
    }

    #[test]
    fn un_estado_desconocido_es_solicitud_invalida() {
        assert!(matches!(
            valor_permitido(Some("BORRADA"), &ESTADOS_ALERTA),
            Err(AppError::SolicitudInvalida)
        ));
    }

    #[test]
    fn la_ip_sale_de_x_forwarded_for_antes_que_de_la_conexion() {
        let mut headers = HeaderMap::new();
        headers.insert("x-forwarded-for", "203.0.113.7, 10.0.0.2".parse().unwrap());
        let conexion = Some(ConnectInfo("172.18.0.5:5000".parse().unwrap()));
        assert_eq!(ip_de(&headers, conexion).as_deref(), Some("203.0.113.7"));

        let conexion = Some(ConnectInfo("172.18.0.5:5000".parse().unwrap()));
        assert_eq!(ip_de(&HeaderMap::new(), conexion).as_deref(), Some("172.18.0.5"));
        assert_eq!(ip_de(&HeaderMap::new(), None), None);
    }

    #[test]
    fn las_fechas_salen_en_utc_con_z() {
        let instante = DateTime::<Utc>::from_timestamp(1_789_000_000, 0).unwrap();
        assert_eq!(iso_utc(instante), "2026-09-10T00:26:40Z");
    }
}
