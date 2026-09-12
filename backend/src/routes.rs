use axum::{routing::get, Router};
use tower_http::trace::TraceLayer;

use crate::{auth, emergencia, ia, realtime, state::AppState};

pub fn construir(state: AppState) -> Router {
    Router::new()
        .route("/healthz", get(|| async { "ok" }))
        .merge(auth::router())
        .merge(emergencia::router())
        .merge(realtime::router())
        .merge(ia::router())
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}
