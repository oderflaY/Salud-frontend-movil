use crate::{config::Config, realtime::hub::Hub};
use sqlx::PgPool;
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub db: PgPool,
    pub config: Arc<Config>,
    pub hub: Hub,
    /// Cliente HTTP compartido (pool de conexiones) para llamar a la API de
    /// DeepSeek desde `crate::ia` — un solo `reqwest::Client` por proceso,
    /// nunca uno por request.
    pub http: reqwest::Client,
}
