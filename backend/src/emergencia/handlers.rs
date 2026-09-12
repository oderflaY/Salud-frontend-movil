use axum::{
    extract::{ConnectInfo, Path, State},
    http::HeaderMap,
    Json,
};
use std::net::SocketAddr;

use crate::{error::AppError, state::AppState};

use super::models::{AlergiaReducida, DatosPersonalesReducidos, PerfilEmergenciaReducido, PerfilSupervivencia};

#[derive(sqlx::FromRow)]
struct FilaTarjeta {
    estado: String,
    id_paciente: String,
    nombre: Option<String>,
    apellidos: Option<String>,
    fecha_nacimiento: Option<chrono::NaiveDate>,
    tipo_sangre: Option<String>,
    donador_organos: Option<bool>,
    condiciones_criticas: Option<Vec<String>>,
    medicacion_rescate: Option<Vec<String>>,
}

#[derive(sqlx::FromRow)]
struct FilaAlergia {
    alergeno: String,
    severidad: Option<String>,
    reaccion: Option<String>,
}

pub async fn consultar_por_tarjeta(
    State(state): State<AppState>,
    Path(id_tarjeta_rfid): Path<String>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
) -> Result<Json<PerfilSupervivencia>, AppError> {
    // Detrás de Caddy, `ConnectInfo` solo ve la IP del proxy — Caddy
    // reescribe X-Forwarded-For con la IP real del cliente en cada
    // `reverse_proxy`, así que se prefiere ese header cuando está presente.
    let ip_origen = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next())
        .map(|v| v.trim().to_string())
        .unwrap_or_else(|| addr.ip().to_string());

    let fila = sqlx::query_as::<_, FilaTarjeta>(
        "select d.estado, d.id_paciente, dp.nombre, dp.apellidos, dp.fecha_nacimiento,
                per.tipo_sangre, per.donador_organos, per.condiciones_criticas, per.medicacion_rescate
         from app.dispositivos_rfid d
         left join app.datos_personales_paciente dp on dp.id_paciente = d.id_paciente
         left join app.perfil_emergencia_reducido per on per.id_paciente = d.id_paciente
         where d.id_tarjeta_rfid = $1",
    )
    .bind(&id_tarjeta_rfid)
    .fetch_optional(&state.db)
    .await?;

    let (resultado_auditoria, respuesta) = match &fila {
        None => ("desconocida", Err(AppError::TarjetaDesconocida)),
        Some(f) if f.estado == "revocada" => ("revocada", Err(AppError::TarjetaRevocada)),
        Some(f) => {
            let alergias = sqlx::query_as::<_, FilaAlergia>(
                "select alergeno, severidad, reaccion from app.alergias where id_paciente = $1",
            )
            .bind(&f.id_paciente)
            .fetch_all(&state.db)
            .await?;

            (
                "encontrada",
                Ok(Json(PerfilSupervivencia {
                    id_tarjeta_rfid: id_tarjeta_rfid.clone(),
                    datos_personales: DatosPersonalesReducidos {
                        nombre: f.nombre.clone().unwrap_or_default(),
                        apellidos: f.apellidos.clone().unwrap_or_default(),
                        fecha_nacimiento: f.fecha_nacimiento,
                    },
                    perfil_emergencia_reducido: PerfilEmergenciaReducido {
                        tipo_sangre: f.tipo_sangre.clone(),
                        donador_organos: f.donador_organos.unwrap_or(false),
                        alergias: alergias
                            .into_iter()
                            .map(|a| AlergiaReducida {
                                alergeno: a.alergeno,
                                severidad: a.severidad,
                                reaccion: a.reaccion,
                            })
                            .collect(),
                        condiciones_criticas: f.condiciones_criticas.clone().unwrap_or_default(),
                        medicacion_rescate: f.medicacion_rescate.clone().unwrap_or_default(),
                    },
                })),
            )
        }
    };

    // La auditoría se registra siempre (tarjeta encontrada, desconocida o
    // revocada) y nunca debe tumbar la respuesta al llamador si falla.
    if let Err(e) = sqlx::query(
        "insert into app.auditoria_rfid (id_tarjeta_rfid, resultado, ip_origen) values ($1, $2, $3)",
    )
    .bind(&id_tarjeta_rfid)
    .bind(resultado_auditoria)
    .bind(&ip_origen)
    .execute(&state.db)
    .await
    {
        tracing::error!(error = %e, "no se pudo registrar auditoría de consulta RFID");
    }

    respuesta
}
