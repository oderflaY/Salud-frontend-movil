use axum::{
    extract::{Path, State},
    http::HeaderMap,
    Json,
};
use serde::{Deserialize, Serialize};

use crate::{auth::jwt, error::AppError, state::AppState};

use super::deepseek::ClienteDeepSeek;

/// Misma razón que `realtime::ws::canal_autorizado`: la conexión de Axum a
/// Postgres es la del rol dueño y no pasa por PostgREST, así que no existe el
/// GUC `request.jwt.claims` que las funciones RPC leen — el JWT se valida a
/// mano aquí, a partir del header `Authorization: Bearer <token>`.
fn autenticar(headers: &HeaderMap, jwt_secret: &str) -> Result<jwt::Claims, AppError> {
    let valor = headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .ok_or(AppError::NoAutorizado)?;
    let token = valor.strip_prefix("Bearer ").ok_or(AppError::NoAutorizado)?;
    jwt::verificar(token, jwt_secret).map_err(|_| AppError::NoAutorizado)
}

fn cliente_deepseek(state: &AppState) -> Result<ClienteDeepSeek, AppError> {
    let api_key = state
        .config
        .deepseek_api_key
        .clone()
        .ok_or(AppError::IaNoConfigurada)?;

    Ok(ClienteDeepSeek::nuevo(
        state.http.clone(),
        state.config.deepseek_base_url.clone(),
        api_key,
        state.config.deepseek_modelo.clone(),
    ))
}

#[derive(sqlx::FromRow)]
struct FilaMensaje {
    autor: String,
    texto: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RespuestaResumen {
    pub resumen: String,
}

/// `POST /chat/{idConversacion}/resumen` — resume el historial completo de
/// una conversación del módulo 8 (chat). No reemplaza `obtenerHistorial`
/// (RPC de PostgREST): es una capacidad nueva, aditiva, que consume esos
/// mismos mensajes desde Postgres.
pub async fn resumir_conversacion(
    State(state): State<AppState>,
    Path(id_conversacion): Path<String>,
    headers: HeaderMap,
) -> Result<Json<RespuestaResumen>, AppError> {
    let claims = autenticar(&headers, &state.config.jwt_secret)?;
    let cliente = cliente_deepseek(&state)?;

    let autorizado = sqlx::query_scalar::<_, bool>(
        "select exists(
            select 1 from app.conversaciones
            where id_conversacion = $1 and (id_paciente = $2 or id_medico = $2)
        )",
    )
    .bind(&id_conversacion)
    .bind(&claims.sub)
    .fetch_one(&state.db)
    .await?;

    if !autorizado {
        return Err(AppError::NoAutorizado);
    }

    let mensajes = sqlx::query_as::<_, FilaMensaje>(
        "select autor, texto from app.mensajes where id_conversacion = $1 order by instante",
    )
    .bind(&id_conversacion)
    .fetch_all(&state.db)
    .await?;

    if mensajes.is_empty() {
        // Nada que resumir todavía: no tiene sentido gastar una llamada a
        // DeepSeek ni es un error, es un resumen vacío legítimo.
        return Ok(Json(RespuestaResumen { resumen: String::new() }));
    }

    let texto_conversacion = mensajes
        .iter()
        .map(|m| format!("{}: {}", m.autor, m.texto))
        .collect::<Vec<_>>()
        .join("\n");

    let resumen = cliente.resumir_conversacion(&texto_conversacion).await?;
    Ok(Json(RespuestaResumen { resumen }))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SolicitudTraduccion {
    pub texto: String,
    pub idioma_destino: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RespuestaTraduccion {
    pub traduccion: String,
}

/// `POST /chat/traducir` — traduce un texto suelto (p. ej. un mensaje del
/// chat antes de mostrarlo). No está atado a una conversación en particular
/// ni valida participación: cualquier usuario autenticado puede traducir
/// texto arbitrario, igual que traduciría cualquier texto que ya puede leer
/// en su propio idioma.
pub async fn traducir_texto(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(body): Json<SolicitudTraduccion>,
) -> Result<Json<RespuestaTraduccion>, AppError> {
    autenticar(&headers, &state.config.jwt_secret)?;

    if body.texto.trim().is_empty() || body.idioma_destino.trim().is_empty() {
        return Err(AppError::SolicitudInvalida);
    }

    let cliente = cliente_deepseek(&state)?;
    let traduccion = cliente.traducir(&body.texto, &body.idioma_destino).await?;
    Ok(Json(RespuestaTraduccion { traduccion }))
}
