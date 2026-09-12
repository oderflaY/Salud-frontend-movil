use serde::{Deserialize, Serialize};
use tokio::sync::broadcast;

/// El mismo envelope que ya arma `app.emitir_evento` en Postgres — se
/// deserializa tal cual llega por NOTIFY y se reenvía sin tocarlo a los
/// clientes autorizados para ese canal.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Evento {
    pub canal: String,
    pub evento: String,
    pub payload: serde_json::Value,
}

/// Un solo `broadcast` compartido por todas las conexiones — cada conexión
/// decide localmente qué eventos reenviar a SU cliente según a qué canales
/// se suscribió (ver `ws.rs`). Para el tamaño de esta app es más simple que
/// mantener un mapa canal -> lista de subscriptores con locks.
#[derive(Clone)]
pub struct Hub {
    tx: broadcast::Sender<Evento>,
}

impl Hub {
    pub fn nuevo() -> Self {
        let (tx, _rx) = broadcast::channel(1024);
        Hub { tx }
    }

    pub fn emitir(&self, evento: Evento) {
        // Sin receptores conectados, `send` regresa Err — no es un fallo real.
        let _ = self.tx.send(evento);
    }

    pub fn suscribirse(&self) -> broadcast::Receiver<Evento> {
        self.tx.subscribe()
    }
}
