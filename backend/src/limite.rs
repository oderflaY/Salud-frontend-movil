//! Límite de intentos por IP para lo que se puede usar sin sesión.

use std::sync::Arc;

use governor::middleware::NoOpMiddleware;
use tower_governor::{governor::GovernorConfigBuilder, key_extractor::SmartIpKeyExtractor, GovernorLayer};

pub type LimitePorIp = GovernorLayer<SmartIpKeyExtractor, NoOpMiddleware>;

/// Hasta `rafaga` intentos seguidos y después uno cada `segundos_por_intento`
/// por IP real del cliente (`X-Forwarded-For` detrás de Caddy). Responde 429.
pub fn por_ip(segundos_por_intento: u64, rafaga: u32) -> LimitePorIp {
    let config = GovernorConfigBuilder::default()
        .per_second(segundos_por_intento)
        .burst_size(rafaga)
        .key_extractor(SmartIpKeyExtractor)
        .finish()
        .expect("configuración de límite de intentos inválida");
    GovernorLayer { config: Arc::new(config) }
}

/// Inicio de sesión, alta y recuperación de contraseña: diez intentos seguidos
/// y luego uno cada 6 s (10 por minuto). Holgado para varios pacientes tras el
/// wifi de la clínica (misma IP), inútil para probar contraseñas por fuerza bruta.
pub fn intentos_de_acceso() -> LimitePorIp {
    por_ip(6, 10)
}
