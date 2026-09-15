use axum::{
    extract::{Path, State},
    http::HeaderMap,
    Json,
};
use serde::{Deserialize, Serialize};

use crate::{auth::sesion, error::AppError, state::AppState};

use super::deepseek::{ClienteDeepSeek, PuntoResumen};


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
    let claims = sesion::autenticar(&headers, &state).await?;
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
    sesion::autenticar(&headers, &state).await?;

    if body.texto.trim().is_empty() || body.idioma_destino.trim().is_empty() {
        return Err(AppError::SolicitudInvalida);
    }

    let cliente = cliente_deepseek(&state)?;
    let traduccion = cliente.traducir(&body.texto, &body.idioma_destino).await?;
    Ok(Json(RespuestaTraduccion { traduccion }))
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RespuestaResumenClinico {
    pub puntos: Vec<PuntoResumen>,
    pub mensaje_original: String,
}

/// `POST /chat/mensajes/{idMensaje}/resumen-ia` — puntos clinicos de un
/// mensaje largo del paciente, para la tarjeta de resumen del chat del medico.
/// Solo para quien participa en esa conversacion. Un mensaje inexistente y uno
/// ajeno responden igual, para no confirmar que un id existe.
pub async fn resumir_mensaje(
    State(state): State<AppState>,
    Path(id_mensaje): Path<String>,
    headers: HeaderMap,
) -> Result<Json<RespuestaResumenClinico>, AppError> {
    let claims = sesion::autenticar(&headers, &state).await?;

    let texto: String = sqlx::query_scalar(
        "select m.texto from app.mensajes m
         join app.conversaciones c on c.id_conversacion = m.id_conversacion
         where m.id_mensaje = $1 and (c.id_paciente = $2 or c.id_medico = $2)",
    )
    .bind(&id_mensaje)
    .bind(&claims.sub)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::NoAutorizado)?;

    let cliente = cliente_deepseek(&state)?;
    let puntos = cliente.resumir_mensaje_clinico(&texto).await?;
    Ok(Json(RespuestaResumenClinico { puntos, mensaje_original: texto }))
}
