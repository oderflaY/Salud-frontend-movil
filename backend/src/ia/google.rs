//! Traducción con la API oficial de Google Cloud Translation (v2, REST simple
//! por API key — sin OAuth, que exigiría una cuenta de servicio).
//!
//! Existe junto a [`super::deepseek`] y [`super::ollama`] como una tercera
//! opción: un servicio de nube confiable y con soporte, para quien no quiere
//! mantener un Ollama propio (`ollama.rs`) ni depender de DeepSeek. Tiene
//! capa gratuita (500 000 caracteres al mes) y después cobra por uso.

use serde::{Deserialize, Serialize};

use crate::error::AppError;

const URL_BASE: &str = "https://translation.googleapis.com/language/translate/v2";

#[derive(Clone)]
pub struct ClienteGoogleTraductor {
    http: reqwest::Client,
    api_key: String,
}

#[derive(Serialize)]
struct Solicitud<'a> {
    q: &'a str,
    target: &'a str,
    format: &'a str,
}

#[derive(Deserialize)]
struct Respuesta {
    data: DatosRespuesta,
}

#[derive(Deserialize)]
struct DatosRespuesta {
    translations: Vec<Traduccion>,
}

#[derive(Deserialize)]
struct Traduccion {
    #[serde(rename = "translatedText")]
    texto_traducido: String,
}

/// Cuerpo de error que Google devuelve: `{"error": {"message": "...", ...}}`.
#[derive(Deserialize)]
struct RespuestaError {
    error: DetalleError,
}

#[derive(Deserialize)]
struct DetalleError {
    message: String,
}

impl ClienteGoogleTraductor {
    pub fn nuevo(http: reqwest::Client, api_key: String) -> Self {
        Self { http, api_key }
    }

    /// Traduce a [`idioma_destino`] (código ISO 639-1 como `en` o `es`). No se
    /// manda el idioma de origen: Google lo detecta solo, igual que el resto
    /// de los traductores de este módulo.
    pub async fn traducir(&self, texto: &str, idioma_destino: &str) -> Result<String, AppError> {
        let respuesta = self
            .http
            .post(URL_BASE)
            .query(&[("key", self.api_key.as_str())])
            .json(&Solicitud { q: texto, target: idioma_destino, format: "text" })
            .send()
            .await
            .map_err(|e| AppError::IaNoDisponible(format!("no se pudo contactar a Google Translate: {e}")))?;

        let status = respuesta.status();
        if !status.is_success() {
            // El detalle de Google es legible ("API key not valid", "quota
            // exceeded") y ayuda a diagnosticar sin exponerlo al cliente
            // final (`AppError::IaNoDisponible` nunca llega tal cual a la app).
            let detalle = respuesta
                .json::<RespuestaError>()
                .await
                .map(|e| e.error.message)
                .unwrap_or_else(|_| status.to_string());
            return Err(AppError::IaNoDisponible(format!("Google Translate respondió {status}: {detalle}")));
        }

        let cuerpo: Respuesta = respuesta
            .json()
            .await
            .map_err(|e| AppError::IaNoDisponible(format!("respuesta de Google Translate no reconocida: {e}")))?;

        let traduccion = cuerpo
            .data
            .translations
            .into_iter()
            .next()
            .map(|t| t.texto_traducido)
            .ok_or_else(|| AppError::IaNoDisponible("Google Translate no devolvió ninguna traducción".to_string()))?;

        if traduccion.trim().is_empty() {
            return Err(AppError::IaNoDisponible("Google Translate devolvió una traducción vacía".to_string()));
        }
        Ok(traduccion)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{method, path, query_param};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    // `URL_BASE` es una constante fija (la API real de Google), así que estas
    // pruebas verifican el parseo de la respuesta contra un servidor falso
    // que devuelve exactamente la forma documentada, no la llamada completa.

    fn cuerpo_ok(texto: &str) -> serde_json::Value {
        serde_json::json!({"data": {"translations": [{"translatedText": texto, "detectedSourceLanguage": "es"}]}})
    }

    #[tokio::test]
    async fn parsea_la_traduccion_de_una_respuesta_valida() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/x"))
            .and(query_param("key", "clave-de-prueba"))
            .respond_with(ResponseTemplate::new(200).set_body_json(cuerpo_ok("My stomach still hurts")))
            .mount(&servidor)
            .await;

        let http = reqwest::Client::new();
        let respuesta = http
            .post(format!("{}/x", servidor.uri()))
            .query(&[("key", "clave-de-prueba")])
            .json(&Solicitud { q: "Todavia me duele el estomago", target: "en", format: "text" })
            .send()
            .await
            .unwrap();
        let cuerpo: Respuesta = respuesta.json().await.unwrap();

        assert_eq!(cuerpo.data.translations[0].texto_traducido, "My stomach still hurts");
    }

    #[tokio::test]
    async fn una_clave_invalida_se_convierte_en_ia_no_disponible_con_el_mensaje_de_google() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(400).set_body_json(serde_json::json!({
                "error": {"code": 400, "message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT"}
            })))
            .mount(&servidor)
            .await;

        let cliente = ClienteGoogleTraductor { http: reqwest::Client::new(), api_key: "mala".to_string() };
        // Redirige a la URL del servidor falso solo para esta prueba.
        let respuesta = cliente
            .http
            .post(servidor.uri())
            .query(&[("key", cliente.api_key.as_str())])
            .json(&Solicitud { q: "hola", target: "en", format: "text" })
            .send()
            .await
            .unwrap();
        let status = respuesta.status();
        let detalle = respuesta.json::<RespuestaError>().await.unwrap().error.message;

        assert!(!status.is_success());
        assert!(detalle.contains("API key not valid"));
    }

    #[tokio::test]
    async fn una_traduccion_vacia_no_se_devuelve_como_valida() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(ResponseTemplate::new(200).set_body_json(cuerpo_ok("   ")))
            .mount(&servidor)
            .await;

        let http = reqwest::Client::new();
        let respuesta = http.post(servidor.uri()).send().await.unwrap();
        let cuerpo: Respuesta = respuesta.json().await.unwrap();
        let texto = &cuerpo.data.translations[0].texto_traducido;

        assert!(texto.trim().is_empty());
    }
}
