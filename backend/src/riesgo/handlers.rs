use axum::{
    extract::{Path, Query, State},
    http::HeaderMap,
    Json,
};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::{auth::sesion, error::AppError, ia::deepseek::ClienteDeepSeek, state::AppState};

use super::contexto::contexto_recuperado;

/// Largo máximo del texto a analizar: una entrada de diario o un mensaje, no un libro.
const LARGO_MAXIMO: usize = 5_000;

#[derive(Debug, Deserialize)]
pub struct SolicitudAnalisis {
    pub texto: String,
    /// Si se pide la explicación en lenguaje natural (llama a DeepSeek).
    #[serde(default)]
    pub explicar: bool,
}

#[derive(Debug, Deserialize)]
pub struct ParametrosExplicar {
    #[serde(default)]
    pub explicar: bool,
}

#[derive(Debug, Serialize)]
pub struct RespuestaAnalisis {
    pub analisis: Value,
    /// `null` si no se pidió, si la IA no está configurada o si falló: el
    /// análisis por reglas nunca depende de un servicio externo.
    pub explicacion_ia: Option<String>,
}

/// `POST /riesgo/analizar` — analiza un texto con el mismo motor que se aplica
/// al guardar el diario y los mensajes. Para el paciente (antes de guardar) y
/// para el médico.
pub async fn analizar(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(cuerpo): Json<SolicitudAnalisis>,
) -> Result<Json<RespuestaAnalisis>, AppError> {
    sesion::autenticar(&headers, &state).await?;
    let texto = cuerpo.texto.trim();
    if texto.is_empty() || texto.chars().count() > LARGO_MAXIMO {
        return Err(AppError::SolicitudInvalida);
    }

    let analisis: Value = sqlx::query_scalar("select app.analizar_texto_riesgo($1)")
        .bind(texto)
        .fetch_one(&state.db)
        .await?;
    let explicacion_ia = if cuerpo.explicar { explicar(&state, texto, &analisis).await } else { None };
    Ok(Json(RespuestaAnalisis { analisis, explicacion_ia }))
}

/// `GET /riesgo/diario/{idEntrada}?explicar=true` — el análisis guardado de una
/// entrada del diario. Solo para su paciente o un médico con vínculo vigente;
/// una entrada ajena y una inexistente responden igual.
pub async fn analisis_de_entrada(
    State(state): State<AppState>,
    Path(id_entrada): Path<String>,
    Query(parametros): Query<ParametrosExplicar>,
    headers: HeaderMap,
) -> Result<Json<RespuestaAnalisis>, AppError> {
    let claims = sesion::autenticar(&headers, &state).await?;

    let fila: Option<(String, Option<Value>)> = sqlx::query_as(
        "select e.texto, e.analisis_riesgo
         from app.entradas_diario e
         where e.id_entrada = $1
           and (($3 = 'paciente' and e.id_paciente = $2)
             or ($3 = 'medico' and app.vinculo_vigente($2, e.id_paciente)))",
    )
    .bind(&id_entrada)
    .bind(&claims.sub)
    .bind(&claims.role)
    .fetch_optional(&state.db)
    .await?;
    let (texto, analisis) = fila.ok_or(AppError::NoAutorizado)?;
    let analisis = analisis.unwrap_or(Value::Null);

    let explicacion_ia = if parametros.explicar && !analisis.is_null() {
        explicar(&state, &texto, &analisis).await
    } else {
        None
    };
    Ok(Json(RespuestaAnalisis { analisis, explicacion_ia }))
}

async fn explicar(state: &AppState, texto: &str, analisis: &Value) -> Option<String> {
    let api_key = state.config.deepseek_api_key.clone()?;
    let cliente = ClienteDeepSeek::nuevo(
        state.http.clone(),
        state.config.deepseek_base_url.clone(),
        api_key,
        state.config.deepseek_modelo.clone(),
    );
    match cliente.explicar_riesgo(texto, &contexto_recuperado(analisis)).await {
        Ok(explicacion) => Some(explicacion),
        Err(error) => {
            tracing::warn!(?error, "no se pudo generar la explicación de riesgo");
            None
        }
    }
}
