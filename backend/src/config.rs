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
    /// Ollama propio para traducir el chat (por ejemplo `translategemma`).
    /// Si está puesta, la traducción va aquí y no a DeepSeek: el texto del
    /// paciente no sale hacia un servicio de terceros.
    pub ollama_url: Option<String>,
    pub ollama_modelo_traduccion: String,
    /// API key de Google Cloud Translation (v2, REST por key — sin OAuth).
    /// Tercera opción de traducción: un servicio de nube oficial, para quien
    /// no quiere mantener un Ollama propio ni usar DeepSeek. Orden de
    /// prioridad en `ia::handlers::traducir_texto`: Ollama, luego Google,
    /// luego DeepSeek — el local siempre gana porque no depende de internet
    /// ni manda el texto del paciente a un tercero.
    pub google_translate_api_key: Option<String>,
    /// Orígenes web que pueden llamar a `/api/v1` desde el navegador (el
    /// dashboard del médico). La app móvil no pasa por CORS. `*` abre a
    /// cualquiera: solo para pruebas, nunca en producción.
    pub cors_origenes: Vec<String>,
}

/// Vite en desarrollo (`npm run dev`) y en vista previa (`npm run preview`).
pub const CORS_ORIGENES_POR_DEFECTO: &str =
    "http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173";

pub fn separar_origenes(valor: &str) -> Vec<String> {
    valor
        .split(',')
        .map(|o| o.trim().trim_end_matches('/').to_string())
        .filter(|o| !o.is_empty())
        .collect()
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
            ollama_url: env::var("OLLAMA_URL").ok().filter(|v| !v.trim().is_empty()),
            ollama_modelo_traduccion: env::var("OLLAMA_MODELO_TRADUCCION")
                .unwrap_or_else(|_| "translategemma:12b".to_string()),
            google_translate_api_key: env::var("GOOGLE_TRANSLATE_API_KEY").ok().filter(|v| !v.trim().is_empty()),
            cors_origenes: separar_origenes(
                &env::var("CORS_ORIGENES")
                    .ok()
                    .filter(|v| !v.trim().is_empty())
                    .unwrap_or_else(|| CORS_ORIGENES_POR_DEFECTO.to_string()),
            ),
        }
    }
}

fn require_env(name: &str) -> String {
    env::var(name).unwrap_or_else(|_| panic!("falta la variable de entorno requerida: {name}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn los_origenes_se_separan_por_coma_y_pierden_la_barra_final() {
        assert_eq!(
            separar_origenes(" https://panel.salud.mx/ , http://localhost:5173,,"),
            vec!["https://panel.salud.mx", "http://localhost:5173"]
        );
    }
}
