use sqlx::postgres::PgListener;

use super::hub::{Evento, Hub};

/// Mantiene una única conexión `LISTEN realtime_events` (la misma que
/// `app.emitir_evento` usa con `pg_notify`) y reenvía cada notificación al
/// hub en memoria. Si la conexión se cae, se reintenta — un reconnect no
/// debe tumbar el proceso del backend.
pub async fn escuchar(database_url: String, hub: Hub) {
    loop {
        match PgListener::connect(&database_url).await {
            Ok(mut listener) => {
                if let Err(e) = listener.listen("realtime_events").await {
                    tracing::error!(error = %e, "no se pudo suscribir al canal realtime_events");
                    tokio::time::sleep(std::time::Duration::from_secs(3)).await;
                    continue;
                }
                tracing::info!("escuchando notificaciones de Postgres en realtime_events");

                loop {
                    match listener.recv().await {
                        Ok(notificacion) => match serde_json::from_str::<Evento>(notificacion.payload()) {
                            Ok(evento) => hub.emitir(evento),
                            Err(e) => tracing::error!(error = %e, payload = notificacion.payload(), "notificación con payload inesperado"),
                        },
                        Err(e) => {
                            tracing::error!(error = %e, "se perdió la conexión LISTEN, reconectando");
                            break;
                        }
                    }
                }
            }
            Err(e) => {
                tracing::error!(error = %e, "no se pudo conectar para LISTEN, reintentando en 3s");
            }
        }
        tokio::time::sleep(std::time::Duration::from_secs(3)).await;
    }
}
