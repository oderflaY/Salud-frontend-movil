use std::env;

/// Toda la configuración sensible entra por variable de entorno y se valida
/// una sola vez al arrancar — si falta algo crítico (el JWT secret, la
/// cadena de conexión), el proceso no debe arrancar "a medias" con un valor
/// por defecto silencioso.
#[derive(Clone)]
pub struct Config {
    pub database_url: String,
    pub jwt_secret: String,
    pub jwt_expiracion_horas: i64,
    pub argon2_secret_key: String,
    pub backend_port: u16,
    /// A propósito `Option`, no `require_env`: el módulo de IA (resumen de
    /// chat, traducción) es una capacidad opcional que se activa poniendo la
    /// variable de entorno — su ausencia no debe impedir que el resto del
    /// backend arranque. Los handlers de `crate::ia` devuelven
    /// `IA_NO_CONFIGURADA` (503) mientras no esté puesta.
    pub deepseek_api_key: Option<String>,
    pub deepseek_base_url: String,
    pub deepseek_modelo: String,
}

impl Config {
    pub fn from_env() -> Self {
        Config {
            database_url: require_env("DATABASE_URL"),
            jwt_secret: require_env("JWT_SECRET"),
            jwt_expiracion_horas: require_env("JWT_EXPIRACION_HORAS")
                .parse()
                .expect("JWT_EXPIRACION_HORAS debe ser un entero"),
            argon2_secret_key: require_env("ARGON2_SECRET_KEY"),
            backend_port: env::var("BACKEND_PORT")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(8080),
            deepseek_api_key: env::var("DEEPSEEK_API_KEY").ok().filter(|v| !v.is_empty()),
            deepseek_base_url: env::var("DEEPSEEK_BASE_URL")
                .unwrap_or_else(|_| "https://api.deepseek.com".to_string()),
            deepseek_modelo: env::var("DEEPSEEK_MODELO").unwrap_or_else(|_| "deepseek-chat".to_string()),
        }
    }
}

fn require_env(name: &str) -> String {
    env::var(name).unwrap_or_else(|_| panic!("falta la variable de entorno requerida: {name}"))
}
