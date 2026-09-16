//! Rutas del panel web (`/api/v1`), con la forma exacta del contrato
//! "Salud+ Panel Web": módulo admin en snake_case, chat en camelCase.
//! El JSON clínico lo arma PostgreSQL (`app.panel_*`, migración 0017).

use std::net::SocketAddr;

use axum::{
    body::Body,
    extract::{ConnectInfo, Path, Query, State},
    http::{header, HeaderMap, HeaderValue, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::{
    adjuntos::handlers::{nombre_seguro, tipo_mime_servible},
    auth::{
        handlers::validar_contrasena_nueva,
        jwt::{self, Claims},
        models::IniciarSesionRequest,
        password,
    },
    error::AppError,
    state::AppState,
};

use super::{
    contrasena_temporal, enlace_adjunto,
    error::ErrorPanel,
    sesion_panel::{cuenta_en_sesion, medico_en_sesion, panel_en_sesion, ROL_ADMIN, ROL_MEDICO},
};

type Resultado<T> = Result<T, ErrorPanel>;

const MAXIMO_CARACTERES_MOTIVO: usize = 500;
const MAXIMO_CARACTERES_MENSAJE: usize = 4000;
const CODIGO_CONFIRMACION_RESET: &str = "REINICIO";

/// Las alertas salen de datos que cambian solos (diario, tomas): antes de
/// leer cualquier cosa que las cuente, se ponen al día.
async fn sincronizar_alertas(state: &AppState, id_medico: &str) -> Resultado<()> {
    sqlx::query("select app.sincronizar_alertas($1)")
        .bind(id_medico)
        .execute(&state.db)
        .await?;
    Ok(())
}

async fn json_de(state: &AppState, consulta: &str, parametro: &str) -> Resultado<Value> {
    Ok(sqlx::query_scalar(consulta).bind(parametro).fetch_one(&state.db).await?)
}

/// Detrás de Caddy, `ConnectInfo` solo ve la IP del proxy; Caddy pone la del
/// cliente en X-Forwarded-For.
pub(super) fn ip_de(headers: &HeaderMap, conexion: Option<ConnectInfo<SocketAddr>>) -> Option<String> {
    headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next())
        .map(|ip| ip.trim().to_string())
        .filter(|ip| !ip.is_empty())
        .or_else(|| conexion.map(|ConnectInfo(addr)| addr.ip().to_string()))
}

/// Motivo obligatorio de una acción sobre una cuenta: queda en la auditoría.
pub(super) fn validar_motivo(motivo: Option<&str>) -> Resultado<String> {
    let motivo = motivo.unwrap_or("").trim().to_string();
    if motivo.is_empty() {
        return Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "MOTIVO_REQUERIDO",
            "Escribe el motivo: queda en la auditoría.",
        ));
    }
    if motivo.chars().count() > MAXIMO_CARACTERES_MOTIVO {
        return Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "MOTIVO_DEMASIADO_LARGO",
            "El motivo no puede pasar de 500 caracteres.",
        ));
    }
    Ok(motivo)
}

/// La doble confirmación de un reinicio: escribir REINICIO.
pub(super) fn validar_codigo_reinicio(codigo: Option<&str>) -> Resultado<()> {
    if codigo.unwrap_or("").trim().to_uppercase() == CODIGO_CONFIRMACION_RESET {
        Ok(())
    } else {
        Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "CODIGO_CONFIRMACION_INCORRECTO",
            "Escribe REINICIO para confirmar el reinicio de la cuenta.",
        ))
    }
}

pub(super) fn password_operador_incorrecta() -> ErrorPanel {
    // 422 y no 401: un 401 hace que el panel cierre la sesión de quien opera.
    ErrorPanel::rechazo(
        StatusCode::UNPROCESSABLE_ENTITY,
        "PASSWORD_OPERADOR_INCORRECTA",
        "Tu contraseña no es correcta. No se hizo ningún cambio.",
    )
}

fn especialidad_legible(codigo: &str) -> &str {
    match codigo {
        "MEDICINA_GENERAL" => "Medicina general",
        "CARDIOLOGIA" => "Cardiología",
        "PEDIATRIA" => "Pediatría",
        "DERMATOLOGIA" => "Dermatología",
        "GINECOLOGIA" => "Ginecología",
        "PSIQUIATRIA" => "Psiquiatría",
        "GERIATRIA" => "Geriatría",
        "ENDOCRINOLOGIA" => "Endocrinología",
        "NEUMOLOGIA" => "Neumología",
        "NEUROLOGIA" => "Neurología",
        otro => otro,
    }
}

fn iniciales(nombre: &str, apellidos: &str) -> String {
    [nombre, apellidos]
        .iter()
        .filter_map(|parte| parte.trim().chars().next())
        .flat_map(char::to_uppercase)
        .collect()
}

// ---------------------------------------------------------------------------
// Autenticación
// ---------------------------------------------------------------------------

#[derive(Serialize)]
pub struct UsuarioLogin {
    id_usuario: String,
    correo: String,
    nombre_completo: String,
    rol: &'static str,
}

#[derive(Serialize)]
pub struct RespuestaLogin {
    token: String,
    usuario: UsuarioLogin,
    /// Entró con una contraseña temporal: el panel pide elegir una nueva
    /// antes de mostrar cualquier otra cosa.
    requiere_cambio_contrasena: bool,
}

/// Una cuenta que puede entrar al panel, sea médico o administrador.
struct CuentaPanel {
    id: String,
    correo: String,
    nombre: String,
    apellidos: String,
    hash: String,
    estado_cuenta: String,
    debe_cambiar: bool,
    temporal_vencida: bool,
    rol_jwt: &'static str,
}

type FilaCuenta = (String, String, String, String, String, String, bool, bool);

async fn buscar_cuenta_por_correo(state: &AppState, correo: &str) -> Resultado<Option<CuentaPanel>> {
    const COLUMNAS: &str = "correo::text, nombre, apellidos, hash_contrasena, estado_cuenta,
         debe_cambiar_contrasena, coalesce(contrasena_temporal_expira_en < now(), false)";
    for (tabla, id, rol) in [("app.medicos", "id_medico", ROL_MEDICO), ("app.administradores", "id_admin", ROL_ADMIN)] {
        let fila: Option<FilaCuenta> =
            sqlx::query_as(&format!("select {id}, {COLUMNAS} from {tabla} where correo = $1"))
                .bind(correo)
                .fetch_optional(&state.db)
                .await?;
        if let Some((id, correo, nombre, apellidos, hash, estado_cuenta, debe_cambiar, temporal_vencida)) = fila {
            return Ok(Some(CuentaPanel {
                id,
                correo,
                nombre,
                apellidos,
                hash,
                estado_cuenta,
                debe_cambiar,
                temporal_vencida,
                rol_jwt: rol,
            }));
        }
    }
    Ok(None)
}

async fn buscar_cuenta_por_id(state: &AppState, claims: &Claims) -> Resultado<CuentaPanel> {
    let (tabla, columna) = if claims.role == ROL_ADMIN {
        ("app.administradores", "id_admin")
    } else {
        ("app.medicos", "id_medico")
    };
    let fila: Option<FilaCuenta> = sqlx::query_as(&format!(
        "select {columna}, correo::text, nombre, apellidos, hash_contrasena, estado_cuenta,
                debe_cambiar_contrasena, coalesce(contrasena_temporal_expira_en < now(), false)
         from {tabla} where {columna} = $1"
    ))
    .bind(&claims.sub)
    .fetch_optional(&state.db)
    .await?;
    let (id, correo, nombre, apellidos, hash, estado_cuenta, debe_cambiar, temporal_vencida) =
        fila.ok_or(AppError::CuentaInactiva)?;
    Ok(CuentaPanel {
        id,
        correo,
        nombre,
        apellidos,
        hash,
        estado_cuenta,
        debe_cambiar,
        temporal_vencida,
        rol_jwt: if claims.role == ROL_ADMIN { ROL_ADMIN } else { ROL_MEDICO },
    })
}

fn respuesta_login(state: &AppState, cuenta: &CuentaPanel, requiere_cambio: bool) -> Resultado<RespuestaLogin> {
    let token = jwt::emitir(&cuenta.id, cuenta.rol_jwt, &state.config.jwt_secret, state.config.jwt_expiracion_horas)
        .map_err(|e| AppError::Interno(e.to_string()))?;
    Ok(RespuestaLogin {
        token,
        usuario: UsuarioLogin {
            id_usuario: cuenta.id.clone(),
            correo: cuenta.correo.clone(),
            nombre_completo: format!("{} {}", cuenta.nombre, cuenta.apellidos),
            rol: if cuenta.rol_jwt == ROL_ADMIN { "administrator" } else { "doctor" },
        },
        requiere_cambio_contrasena: requiere_cambio,
    })
}

/// `POST /api/v1/auth/login`: médicos (su misma cuenta de la app) y
/// administradores. El JWT trae `exp`, que el panel decodifica.
pub async fn iniciar_sesion(
    State(state): State<AppState>,
    Json(body): Json<IniciarSesionRequest>,
) -> Resultado<Json<RespuestaLogin>> {
    let cuenta = buscar_cuenta_por_correo(&state, body.correo.trim())
        .await?
        .ok_or(AppError::CredencialesInvalidas)?;

    if !password::verificar(&body.contrasena, &cuenta.hash, state.config.argon2_secret_key.as_bytes()) {
        return Err(AppError::CredencialesInvalidas.into());
    }
    match cuenta.estado_cuenta.as_str() {
        "activa" => {}
        "bloqueada" => return Err(AppError::CuentaBloqueada.into()),
        _ => return Err(AppError::CredencialesInvalidas.into()),
    }
    if cuenta.debe_cambiar && cuenta.temporal_vencida {
        return Err(AppError::ContrasenaTemporalVencida.into());
    }

    Ok(Json(respuesta_login(&state, &cuenta, cuenta.debe_cambiar)?))
}

#[derive(Serialize)]
pub struct PerfilPanel {
    id: String,
    nombre: String,
    apellidos: String,
    correo: String,
    cedula_profesional: Option<String>,
    especialidad: Option<String>,
    rol: &'static str,
    avatar_iniciales: String,
    requiere_cambio_contrasena: bool,
}

/// `GET /api/v1/auth/me`
pub async fn sesion_actual(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<PerfilPanel>> {
    let claims = cuenta_en_sesion(&headers, &state).await?;
    let cuenta = buscar_cuenta_por_id(&state, &claims).await?;

    let (cedula_profesional, especialidad) = if claims.role == ROL_MEDICO {
        let (cedula, especialidad): (String, Option<String>) =
            sqlx::query_as("select cedula_profesional, especialidad from app.medicos where id_medico = $1")
                .bind(&claims.sub)
                .fetch_one(&state.db)
                .await?;
        (Some(cedula), especialidad.as_deref().map(|e| especialidad_legible(e).to_string()))
    } else {
        (None, None)
    };

    Ok(Json(PerfilPanel {
        avatar_iniciales: iniciales(&cuenta.nombre, &cuenta.apellidos),
        rol: if claims.role == ROL_ADMIN { "admin" } else { "medico" },
        requiere_cambio_contrasena: cuenta.debe_cambiar,
        id: cuenta.id,
        nombre: cuenta.nombre,
        apellidos: cuenta.apellidos,
        correo: cuenta.correo,
        cedula_profesional,
        especialidad,
    }))
}

#[derive(Deserialize)]
pub struct CambioContrasena {
    contrasena_actual: String,
    contrasena_nueva: String,
}

/// `POST /api/v1/auth/cambiar-contrasena`: obligatorio después de un
/// reinicio, y disponible siempre. Cierra las demás sesiones de la cuenta
/// (también la de la app móvil) y devuelve un token nuevo.
pub async fn cambiar_contrasena(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(body): Json<CambioContrasena>,
) -> Resultado<Json<RespuestaLogin>> {
    let claims = cuenta_en_sesion(&headers, &state).await?;
    let cuenta = buscar_cuenta_por_id(&state, &claims).await?;
    let pepper = state.config.argon2_secret_key.as_bytes();

    if !password::verificar(&body.contrasena_actual, &cuenta.hash, pepper) {
        return Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "CONTRASENA_ACTUAL_INCORRECTA",
            "Tu contraseña actual no es correcta.",
        ));
    }
    if cuenta.debe_cambiar && cuenta.temporal_vencida {
        return Err(AppError::ContrasenaTemporalVencida.into());
    }
    validar_contrasena_nueva(&body.contrasena_nueva)?;
    if body.contrasena_nueva == body.contrasena_actual {
        return Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "CONTRASENA_REPETIDA",
            "La contraseña nueva debe ser distinta de la actual.",
        ));
    }

    let hash = password::hash(&body.contrasena_nueva, pepper).map_err(AppError::Interno)?;
    let (tabla, columna) = if claims.role == ROL_ADMIN {
        ("app.administradores", "id_admin")
    } else {
        ("app.medicos", "id_medico")
    };
    sqlx::query(&format!(
        "update {tabla}
            set hash_contrasena = $2, debe_cambiar_contrasena = false,
                contrasena_temporal_expira_en = null, sesiones_validas_desde = now()
          where {columna} = $1"
    ))
    .bind(&claims.sub)
    .bind(&hash)
    .execute(&state.db)
    .await?;

    tracing::info!(id = %claims.sub, rol = %claims.role, "cambio de contraseña desde el panel");
    Ok(Json(respuesta_login(&state, &cuenta, false)?))
}

#[derive(Deserialize)]
pub struct SolicitudRecuperacion {
    correo: String,
}

/// `POST /api/v1/auth/recuperar-contrasena`, sin sesión.
///
/// El sistema no envía correos: la solicitud llega a la bandeja del
/// administrador, que da una contraseña temporal. Responde igual exista o no
/// la cuenta, para no revelar qué correos están registrados.
pub async fn solicitar_recuperacion(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Json(body): Json<SolicitudRecuperacion>,
) -> Resultado<Json<Value>> {
    let correo = body.correo.trim();
    if !correo.is_empty() && correo.len() <= 254 {
        let cuenta: Option<(String, String)> = sqlx::query_as(
            "select 'medico', id_medico from app.medicos where correo = $1 and estado_cuenta <> 'eliminada'
             union all
             select 'paciente', id_paciente from app.pacientes where correo = $1 and estado_cuenta <> 'eliminada'
             limit 1",
        )
        .bind(correo)
        .fetch_optional(&state.db)
        .await?;

        if let Some((tipo_cuenta, id_cuenta)) = cuenta {
            sqlx::query(
                "insert into app.solicitudes_recuperacion (tipo_cuenta, id_cuenta, ip_origen)
                 values ($1, $2, $3)
                 on conflict (tipo_cuenta, id_cuenta) where atendida_en is null do nothing",
            )
            .bind(&tipo_cuenta)
            .bind(&id_cuenta)
            .bind(ip_de(&headers, conexion))
            .execute(&state.db)
            .await?;
            tracing::info!(%tipo_cuenta, %id_cuenta, "solicitud de recuperación de contraseña");
        }
    }

    Ok(Json(json!({
        "ok": true,
        "mensaje": "Si el correo corresponde a una cuenta, el administrador de la clínica recibirá tu solicitud \
                    y te dará una contraseña temporal. Comunícate con la clínica para recibirla."
    })))
}

// ---------------------------------------------------------------------------
// Lecturas del panel
// ---------------------------------------------------------------------------

/// `GET /api/v1/admin/dashboard/kpis`
pub async fn kpis(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    sincronizar_alertas(&state, &claims.sub).await?;
    Ok(Json(json_de(&state, "select app.panel_kpis($1)", &claims.sub).await?))
}

/// `GET /api/v1/admin/pacientes`: solo los que tienen vínculo vigente con él.
pub async fn pacientes(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    Ok(Json(json_de(&state, "select app.panel_pacientes($1)", &claims.sub).await?))
}

/// `GET /api/v1/admin/alertas`
pub async fn alertas(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    sincronizar_alertas(&state, &claims.sub).await?;
    Ok(Json(json_de(&state, "select app.panel_alertas($1)", &claims.sub).await?))
}

/// `GET /api/v1/admin/auditoria`: el administrador ve toda la bitácora; el
/// médico, lo hecho sobre sus pacientes y sobre su propia cuenta.
pub async fn auditoria(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    let claims = panel_en_sesion(&headers, &state).await?;
    if claims.role == ROL_ADMIN {
        return Ok(Json(sqlx::query_scalar("select app.panel_admin_auditoria()").fetch_one(&state.db).await?));
    }
    Ok(Json(json_de(&state, "select app.panel_auditoria($1)", &claims.sub).await?))
}

// ---------------------------------------------------------------------------
// Alertas
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
pub struct CambioAlerta {
    status: String,
}

/// `pendiente | en_revision | resuelto` del panel al estado guardado.
fn estado_guardado(estado_atencion: &str) -> Option<&'static str> {
    match estado_atencion.trim().to_lowercase().as_str() {
        "pendiente" => Some("NUEVA"),
        "en_revision" => Some("EN_REVISION"),
        "resuelto" | "resuelta" => Some("ATENDIDA"),
        _ => None,
    }
}

/// `PATCH /api/v1/alerts/{id}` (y `/admin/alertas/{id}`) con `{"status"}`.
pub async fn cambiar_estado_alerta(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id_alerta): Path<String>,
    Json(body): Json<CambioAlerta>,
) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    let estado = estado_guardado(&body.status).ok_or_else(|| {
        ErrorPanel::rechazo(
            StatusCode::BAD_REQUEST,
            "ESTADO_NO_VALIDO",
            "El estado debe ser pendiente, en_revision o resuelto.",
        )
    })?;

    let estado_atencion: String = sqlx::query_scalar(
        "update app.alertas set estado = $3
          where id_alerta = $1 and id_medico = $2 and app.vinculo_vigente($2, id_paciente)
          returning app.estado_atencion(estado)",
    )
    .bind(&id_alerta)
    .bind(&claims.sub)
    .bind(estado)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::AlertaNoEncontrada)?;

    Ok(Json(json!({ "id": id_alerta, "estado_atencion": estado_atencion })))
}

// ---------------------------------------------------------------------------
// Reinicio de cuenta
// ---------------------------------------------------------------------------

#[derive(Deserialize, Default)]
pub struct CuerpoReset {
    id_paciente: Option<String>,
    motivo: Option<String>,
    codigo_confirmacion: Option<String>,
    password_operador: Option<String>,
}

/// `PATCH /api/v1/admin/usuarios/{id_paciente}/reset-cuenta`
///
/// Doble confirmación revalidada aquí: el código `REINICIO` y la contraseña
/// del propio médico. Da una contraseña temporal que el médico entrega al
/// paciente, cierra sus sesiones abiertas y la app le obliga a elegir una
/// nueva al entrar. No toca el expediente. Queda en la auditoría (NOM-004).
///
/// Solo un médico con la cédula aprobada y con vínculo vigente.
pub async fn resetear_cuenta(
    State(state): State<AppState>,
    headers: HeaderMap,
    conexion: Option<ConnectInfo<SocketAddr>>,
    Path(id_paciente): Path<String>,
    cuerpo: Option<Json<CuerpoReset>>,
) -> Resultado<Response> {
    let claims = medico_en_sesion(&headers, &state).await?;
    let cuerpo = cuerpo.map(|Json(c)| c).unwrap_or_default();

    if cuerpo.id_paciente.as_deref().is_some_and(|id| id != id_paciente) {
        return Err(ErrorPanel::rechazo(
            StatusCode::BAD_REQUEST,
            "PACIENTE_NO_COINCIDE",
            "El paciente del formulario no coincide con el de la ruta.",
        ));
    }
    validar_codigo_reinicio(cuerpo.codigo_confirmacion.as_deref())?;
    let motivo = validar_motivo(cuerpo.motivo.as_deref())?;

    let (hash_operador, verificacion): (String, String) =
        sqlx::query_as("select hash_contrasena, estado_verificacion from app.medicos where id_medico = $1")
            .bind(&claims.sub)
            .fetch_optional(&state.db)
            .await?
            .ok_or(AppError::CuentaInactiva)?;

    let password_operador = cuerpo.password_operador.unwrap_or_default();
    if !password::verificar(&password_operador, &hash_operador, state.config.argon2_secret_key.as_bytes()) {
        tracing::warn!(id_medico = %claims.sub, %id_paciente, "reinicio de cuenta con contraseña de operador incorrecta");
        return Err(password_operador_incorrecta());
    }
    if verificacion != "APROBADO" {
        return Err(AppError::MedicoNoVerificado.into());
    }

    let vinculado: bool = sqlx::query_scalar("select app.vinculo_vigente($1, $2)")
        .bind(&claims.sub)
        .bind(&id_paciente)
        .fetch_one(&state.db)
        .await?;
    if !vinculado {
        return Err(AppError::PacienteNoEncontrado.into());
    }

    let temporal = contrasena_temporal::generar();
    let hash = password::hash(&temporal, state.config.argon2_secret_key.as_bytes()).map_err(AppError::Interno)?;

    let mut tx = state.db.begin().await?;
    let actualizado: Option<String> = sqlx::query_scalar(
        "update app.pacientes
            set hash_contrasena = $2,
                debe_cambiar_contrasena = true,
                contrasena_temporal_expira_en =
                  now() + make_interval(hours => app.config_entero('horas_validez_contrasena_temporal')),
                sesiones_validas_desde = now()
          where id_paciente = $1 and estado_cuenta <> 'eliminada'
          returning id_paciente",
    )
    .bind(&id_paciente)
    .bind(&hash)
    .fetch_optional(&mut *tx)
    .await?;
    if actualizado.is_none() {
        return Err(AppError::PacienteNoEncontrado.into());
    }

    let evento_id: String = sqlx::query_scalar(
        "insert into app.auditoria_reset_cuenta (id_paciente, id_medico, motivo, ip_origen)
         values ($1, $2, $3, $4)
         returning id_auditoria",
    )
    .bind(&id_paciente)
    .bind(&claims.sub)
    .bind(&motivo)
    .bind(ip_de(&headers, conexion))
    .fetch_one(&mut *tx)
    .await?;
    sqlx::query(
        "update app.solicitudes_recuperacion set atendida_en = now()
          where tipo_cuenta = 'paciente' and id_cuenta = $1 and atendida_en is null",
    )
    .bind(&id_paciente)
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;

    tracing::info!(id_medico = %claims.sub, %id_paciente, %evento_id, "reinicio de cuenta de paciente");

    // La contraseña no debe quedar en ninguna caché del navegador ni de un proxy.
    Ok((
        [(header::CACHE_CONTROL, "no-store")],
        Json(json!({ "ok": true, "evento_id": evento_id, "password_temporal": temporal })),
    )
        .into_response())
}

// ---------------------------------------------------------------------------
// Chat (camelCase)
// ---------------------------------------------------------------------------

fn conversacion_ajena() -> ErrorPanel {
    ErrorPanel::rechazo(
        StatusCode::FORBIDDEN,
        "CONVERSACION_NO_ENCONTRADA",
        "Esa conversación no existe o no es con un paciente tuyo.",
    )
}

async fn exigir_conversacion(state: &AppState, id_medico: &str, id_conversacion: &str) -> Resultado<()> {
    let permitida: bool = sqlx::query_scalar("select app.panel_conversacion_permitida($1, $2)")
        .bind(id_medico)
        .bind(id_conversacion)
        .fetch_one(&state.db)
        .await?;
    if permitida {
        Ok(())
    } else {
        Err(conversacion_ajena())
    }
}

/// `http://host:puerto/api/v1`, como lo ve el navegador (detrás de Caddy, el
/// Host se conserva y el esquema llega en X-Forwarded-Proto).
fn base_publica(headers: &HeaderMap) -> String {
    let host = headers
        .get(header::HOST)
        .and_then(|v| v.to_str().ok())
        .filter(|h| !h.is_empty() && h.chars().all(|c| c.is_ascii_alphanumeric() || ".-:[]".contains(c)));
    let esquema = match headers.get("x-forwarded-proto").and_then(|v| v.to_str().ok()) {
        Some("https") => "https",
        _ => "http",
    };
    match host {
        Some(host) => format!("{esquema}://{host}/api/v1"),
        None => "/api/v1".to_string(),
    }
}

/// Cambia `adjunto.idAdjunto` (interno) por `adjunto.urlPreview` firmado.
fn publicar_adjunto(mensaje: &mut Value, base: &str, secreto: &str) {
    if let Some(adjunto) = mensaje.get_mut("adjunto").and_then(Value::as_object_mut) {
        if let Some(Value::String(id)) = adjunto.remove("idAdjunto") {
            let firma = enlace_adjunto::firmar(&id, secreto);
            adjunto.insert("urlPreview".into(), Value::String(format!("{base}/adjuntos/{id}?firma={firma}")));
        }
    }
}

/// `GET /api/v1/admin/conversaciones`
pub async fn conversaciones(State(state): State<AppState>, headers: HeaderMap) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    Ok(Json(json_de(&state, "select app.panel_conversaciones($1)", &claims.sub).await?))
}

/// `GET /api/v1/admin/conversaciones/{id}/mensajes` (los últimos 500, en orden).
pub async fn mensajes(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id_conversacion): Path<String>,
) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    exigir_conversacion(&state, &claims.sub, &id_conversacion).await?;

    let mut lista = json_de(&state, "select app.panel_mensajes($1)", &id_conversacion).await?;
    let base = base_publica(&headers);
    if let Some(mensajes) = lista.as_array_mut() {
        for mensaje in mensajes {
            publicar_adjunto(mensaje, &base, &state.config.jwt_secret);
        }
    }
    Ok(Json(lista))
}

#[derive(Deserialize)]
pub struct MensajeNuevo {
    texto: String,
}

/// `POST /api/v1/admin/conversaciones/{id}/mensajes` con `{"texto"}`. Le
/// llega a la app del paciente en vivo, igual que un mensaje desde la app
/// del médico.
pub async fn enviar_mensaje(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id_conversacion): Path<String>,
    Json(body): Json<MensajeNuevo>,
) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    let texto = body.texto.trim();
    if texto.is_empty() {
        return Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "MENSAJE_VACIO",
            "Escribe un mensaje antes de enviarlo.",
        ));
    }
    if texto.chars().count() > MAXIMO_CARACTERES_MENSAJE {
        return Err(ErrorPanel::rechazo(
            StatusCode::UNPROCESSABLE_ENTITY,
            "MENSAJE_DEMASIADO_LARGO",
            "El mensaje no puede pasar de 4000 caracteres.",
        ));
    }
    exigir_conversacion(&state, &claims.sub, &id_conversacion).await?;

    let id_mensaje: String = sqlx::query_scalar("select app.panel_enviar_mensaje($1, $2)")
        .bind(&id_conversacion)
        .bind(texto)
        .fetch_one(&state.db)
        .await?;
    let mut mensaje = json_de(&state, "select app.panel_mensaje($1)", &id_mensaje).await?;
    publicar_adjunto(&mut mensaje, &base_publica(&headers), &state.config.jwt_secret);
    Ok(Json(mensaje))
}

/// `PATCH /api/v1/admin/conversaciones/{id}/leer`
pub async fn marcar_leida(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id_conversacion): Path<String>,
) -> Resultado<Json<Value>> {
    let claims = medico_en_sesion(&headers, &state).await?;
    exigir_conversacion(&state, &claims.sub, &id_conversacion).await?;
    sqlx::query("update app.conversaciones set leido_por_medico_hasta = now() where id_conversacion = $1")
        .bind(&id_conversacion)
        .execute(&state.db)
        .await?;
    Ok(Json(json!({ "ok": true })))
}

#[derive(Deserialize)]
pub struct FirmaAdjunto {
    firma: Option<String>,
}

/// `GET /api/v1/adjuntos/{id}?firma=...`: el `urlPreview` de un adjunto. Sin
/// `Authorization` (lo pide un `<img>`); la firma vale una hora y solo para
/// ese archivo.
pub async fn ver_adjunto(
    State(state): State<AppState>,
    Path(id_adjunto): Path<String>,
    Query(consulta): Query<FirmaAdjunto>,
) -> Resultado<Response> {
    let firma = consulta.firma.unwrap_or_default();
    if !enlace_adjunto::es_valido(&firma, &id_adjunto, &state.config.jwt_secret) {
        return Err(ErrorPanel::rechazo(
            StatusCode::FORBIDDEN,
            "ENLACE_NO_VALIDO",
            "El enlace del archivo no es válido o ya venció. Recarga la conversación.",
        ));
    }

    let (nombre, tipo_mime, contenido): (String, String, Vec<u8>) =
        sqlx::query_as("select nombre, tipo_mime, contenido from app.adjuntos where id_adjunto = $1")
            .bind(&id_adjunto)
            .fetch_optional(&state.db)
            .await?
            .ok_or(AppError::AdjuntoNoEncontrado)?;

    let mut respuesta = Response::new(Body::from(contenido));
    let cabeceras = respuesta.headers_mut();
    cabeceras.insert(
        header::CONTENT_TYPE,
        HeaderValue::from_str(tipo_mime_servible(&tipo_mime))
            .unwrap_or(HeaderValue::from_static("application/octet-stream")),
    );
    if let Ok(valor) = HeaderValue::from_str(&format!("inline; filename=\"{}\"", nombre_seguro(&nombre))) {
        cabeceras.insert(header::CONTENT_DISPOSITION, valor);
    }
    cabeceras.insert(header::X_CONTENT_TYPE_OPTIONS, HeaderValue::from_static("nosniff"));
    cabeceras.insert(header::CONTENT_SECURITY_POLICY, HeaderValue::from_static("default-src 'none'; sandbox"));
    // Datos clínicos: ningún proxy ni navegador intermedio debe guardar copia.
    cabeceras.insert(header::CACHE_CONTROL, HeaderValue::from_static("private, no-store"));
    Ok(respuesta)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn los_estados_del_panel_se_guardan_como_los_de_la_bandeja() {
        assert_eq!(estado_guardado("pendiente"), Some("NUEVA"));
        assert_eq!(estado_guardado(" EN_REVISION "), Some("EN_REVISION"));
        assert_eq!(estado_guardado("resuelto"), Some("ATENDIDA"));
        assert_eq!(estado_guardado("borrada"), None);
    }

    #[test]
    fn iniciales_y_especialidad_para_el_encabezado() {
        assert_eq!(iniciales("Carlos", "Silva Rodríguez"), "CS");
        assert_eq!(iniciales("élena", ""), "É");
        assert_eq!(especialidad_legible("CARDIOLOGIA"), "Cardiología");
        assert_eq!(especialidad_legible("OTRA"), "OTRA");
    }

    #[test]
    fn la_ip_sale_de_x_forwarded_for_antes_que_de_la_conexion() {
        let mut headers = HeaderMap::new();
        headers.insert("x-forwarded-for", "203.0.113.7, 10.0.0.2".parse().unwrap());
        let conexion = Some(ConnectInfo("172.18.0.5:5000".parse().unwrap()));
        assert_eq!(ip_de(&headers, conexion).as_deref(), Some("203.0.113.7"));
        assert_eq!(ip_de(&HeaderMap::new(), None), None);
    }

    #[test]
    fn la_url_publica_respeta_el_host_y_el_esquema_del_proxy() {
        let mut headers = HeaderMap::new();
        headers.insert(header::HOST, "localhost:8081".parse().unwrap());
        assert_eq!(base_publica(&headers), "http://localhost:8081/api/v1");
        headers.insert("x-forwarded-proto", "https".parse().unwrap());
        assert_eq!(base_publica(&headers), "https://localhost:8081/api/v1");
        headers.insert(header::HOST, "evil\"host".parse().unwrap());
        assert_eq!(base_publica(&headers), "/api/v1");
    }

    #[test]
    fn el_adjunto_sale_con_url_firmada_y_sin_su_id_interno() {
        let mut mensaje = json!({ "id": "msg_1", "adjunto": { "idAdjunto": "adj_9", "nombre": "receta.jpg", "tipo": "foto" } });
        publicar_adjunto(&mut mensaje, "http://localhost:8081/api/v1", "secreto-de-prueba-suficientemente-largo");
        let adjunto = &mensaje["adjunto"];
        assert!(adjunto.get("idAdjunto").is_none());
        let url = adjunto["urlPreview"].as_str().unwrap();
        assert!(url.starts_with("http://localhost:8081/api/v1/adjuntos/adj_9?firma="));

        let mut sin_adjunto = json!({ "id": "msg_2" });
        publicar_adjunto(&mut sin_adjunto, "x", "y");
        assert!(sin_adjunto.get("adjunto").is_none());
    }
}
