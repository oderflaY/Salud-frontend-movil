use std::collections::HashSet;

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Query, State,
    },
    response::{IntoResponse, Response},
};
use jsonwebtoken::{decode, DecodingKey, Validation};
use serde::{Deserialize, Serialize};

use crate::{auth::jwt::Claims, state::AppState};

#[derive(Deserialize)]
pub struct ParametrosConexion {
    token: String,
}

/// Mensajes de control que el cliente manda por el socket (no hay HTTP
/// aparte para suscribirse: todo viaja por la misma conexión ya abierta).
/// Variantes "newtype" a propósito: serializan como `{"suscribir": "canal"}`,
/// no `{"suscribir": {"canal": "..."}}`.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
enum MensajeCliente {
    Suscribir(String),
    Desuscribir(String),
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase", tag = "tipo")]
enum MensajeServidor {
    Suscrito { canal: String },
    Error { mensaje: String },
}

pub async fn manejar_conexion(
    State(state): State<AppState>,
    Query(params): Query<ParametrosConexion>,
    ws: WebSocketUpgrade,
) -> Response {
    let claims = match decode::<Claims>(
        &params.token,
        &DecodingKey::from_secret(state.config.jwt_secret.as_bytes()),
        &Validation::default(),
    ) {
        Ok(datos) => datos.claims,
        Err(_) => return axum::http::StatusCode::UNAUTHORIZED.into_response(),
    };

    ws.on_upgrade(move |socket| atender_socket(socket, state, claims))
}

async fn atender_socket(mut socket: WebSocket, state: AppState, claims: Claims) {
    let mut receptor = state.hub.suscribirse();
    let mut canales_autorizados: HashSet<String> = HashSet::new();

    loop {
        tokio::select! {
            evento = receptor.recv() => {
                match evento {
                    Ok(evento) => {
                        if canales_autorizados.contains(&evento.canal) {
                            if enviar_json(&mut socket, &evento).await.is_err() {
                                break;
                            }
                        }
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => {
                        // Un cliente lento se saltó eventos; sigue con los que vengan.
                        continue;
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
                }
            }
            mensaje = socket.recv() => {
                match mensaje {
                    Some(Ok(Message::Text(texto))) => {
                        procesar_mensaje_cliente(&texto, &state, &claims, &mut canales_autorizados, &mut socket).await;
                    }
                    Some(Ok(Message::Close(_))) | None => break,
                    Some(Ok(_)) => {} // ping/pong/binary: sin acción
                    Some(Err(_)) => break,
                }
            }
        }
    }
}

async fn procesar_mensaje_cliente(
    texto: &str,
    state: &AppState,
    claims: &Claims,
    canales_autorizados: &mut HashSet<String>,
    socket: &mut WebSocket,
) {
    let Ok(mensaje) = serde_json::from_str::<MensajeCliente>(texto) else {
        let _ = enviar_json(
            socket,
            &MensajeServidor::Error { mensaje: "SOLICITUD_INVALIDA".to_string() },
        )
        .await;
        return;
    };

    match mensaje {
        MensajeCliente::Suscribir(canal) => {
            if canal_autorizado(&state.db, &canal, &claims.sub, &claims.role).await {
                canales_autorizados.insert(canal.clone());
                let _ = enviar_json(socket, &MensajeServidor::Suscrito { canal }).await;
            } else {
                let _ = enviar_json(
                    socket,
                    &MensajeServidor::Error { mensaje: "NO_AUTORIZADO".to_string() },
                )
                .await;
            }
        }
        MensajeCliente::Desuscribir(canal) => {
            canales_autorizados.remove(&canal);
        }
    }
}

/// Autorización explícita en Rust, no vía las funciones `app.es_dueno_paciente`
/// / `app.tiene_acceso_medico` de Postgres: esas leen
/// `current_setting('request.jwt.claims')`, un GUC que solo PostgREST inyecta
/// por request — la conexión de Axum a la base es la del rol dueño y no lo
/// tiene, así que la validación se hace aquí con consultas explícitas.
async fn canal_autorizado(db: &sqlx::PgPool, canal: &str, sub: &str, role: &str) -> bool {
    let Some((tipo, id)) = canal.split_once(':') else {
        return false;
    };

    match tipo {
        "agenda" => role == "medico" && id == sub,
        "diario" => {
            if role == "paciente" && id == sub {
                return true;
            }
            if role == "medico" {
                return sqlx::query_scalar::<_, bool>(
                    "select exists(select 1 from app.control_accesos_medico where id_paciente = $1 and id_medico = $2)",
                )
                .bind(id)
                .bind(sub)
                .fetch_one(db)
                .await
                .unwrap_or(false);
            }
            false
        }
        "chat" => sqlx::query_scalar::<_, bool>(
            "select exists(select 1 from app.conversaciones where id_conversacion = $1 and (id_paciente = $2 or id_medico = $2))",
        )
        .bind(id)
        .bind(sub)
        .fetch_one(db)
        .await
        .unwrap_or(false),
        _ => false,
    }
}

async fn enviar_json<T: Serialize>(socket: &mut WebSocket, valor: &T) -> Result<(), axum::Error> {
    let texto = serde_json::to_string(valor).unwrap_or_default();
    socket.send(Message::Text(texto)).await
}
