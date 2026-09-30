//! Traducción con un Ollama propio (`POST {url}/api/chat`, formato de Ollama).
//!
//! Existe aparte de [`super::deepseek`] porque resuelve otro problema: el
//! modelo corre en una máquina del propio equipo (por ejemplo
//! `translategemma`), sin API key ni datos saliendo hacia un tercero. Si
//! `OLLAMA_URL` está puesta, la traducción del chat va por aquí; si no, sigue
//! yendo a DeepSeek, y sin ninguno de los dos el endpoint responde
//! `IA_NO_CONFIGURADA`.

use std::time::Duration;

use serde::{Deserialize, Serialize};

use crate::error::AppError;

/// Un modelo local traduce en unos 10 s, y más si estaba dormido: el tope
/// general del cliente HTTP (30 s) se queda corto para esto.
const ESPERA_MAXIMA: Duration = Duration::from_secs(90);

#[derive(Clone)]
pub struct ClienteOllama {
    http: reqwest::Client,
    base_url: String,
    modelo: String,
}

#[derive(Serialize)]
struct Mensaje<'a> {
    role: &'a str,
    content: String,
}

#[derive(Serialize)]
struct SolicitudChat<'a> {
    model: &'a str,
    messages: Vec<Mensaje<'a>>,
    /// Sin streaming: la respuesta llega completa en un solo JSON.
    stream: bool,
}

#[derive(Deserialize)]
struct RespuestaChat {
    message: MensajeRespuesta,
}

#[derive(Deserialize)]
struct MensajeRespuesta {
    content: String,
}

/// Nombre del idioma en inglés, que es lo que entienden estos modelos. Un
/// código desconocido viaja tal cual en vez de fallar.
pub fn nombre_de_idioma(codigo: &str) -> &str {
    match codigo.trim().to_lowercase().split(['-', '_']).next().unwrap_or("") {
        "es" => "Spanish",
        "en" => "English",
        "pt" => "Portuguese",
        "fr" => "French",
        "de" => "German",
        "it" => "Italian",
        "nah" => "Nahuatl",
        _ => "",
    }
}

impl ClienteOllama {
    pub fn nuevo(http: reqwest::Client, base_url: String, modelo: String) -> Self {
        Self { http, base_url: base_url.trim_end_matches('/').to_string(), modelo }
    }

    /// Traduce a [`idioma_destino`] (código como `en` o `es`). No se le dice
    /// el idioma de origen: el modelo lo reconoce, y darlo por supuesto haría
    /// que un mensaje escrito en otro idioma se tradujera mal.
    pub async fn traducir(&self, texto: &str, idioma_destino: &str) -> Result<String, AppError> {
        let destino = nombre_de_idioma(idioma_destino);
        let destino = if destino.is_empty() { idioma_destino } else { destino };
        let sistema = format!(
            "You are a professional translator working on messages between a patient and their doctor. \
             Translate the user's text into {destino}, keeping the meaning exact and the tone natural. \
             Keep numbers, doses, units and emoji as they are. Produce only the translation, \
             without any explanation, note or quotation marks."
        );

        let cuerpo = SolicitudChat {
            model: &self.modelo,
            messages: vec![
                Mensaje { role: "system", content: sistema },
                Mensaje { role: "user", content: texto.to_string() },
            ],
            stream: false,
        };

        // Un segundo intento cuando la petición ni siquiera llega: con el
        // modelo dormido o el túnel recién levantado, la primera conexión
        // falla y la siguiente funciona.
        let mut ultimo_error = None;
        let mut respuesta = None;
        for _ in 0..2 {
            match self
                .http
                .post(format!("{}/api/chat", self.base_url))
                // Los túneles de ngrok interponen una página de aviso al navegador
                // si no viene esta cabecera; con ella responden el JSON directo.
                .header("ngrok-skip-browser-warning", "true")
                .timeout(ESPERA_MAXIMA)
                .json(&cuerpo)
                .send()
                .await
            {
                Ok(r) => {
                    respuesta = Some(r);
                    break;
                }
                Err(e) => ultimo_error = Some(e),
            }
        }
        let respuesta = match respuesta {
            Some(r) => r,
            None => {
                let e = ultimo_error.expect("hubo al menos un intento");
                return Err(AppError::IaNoDisponible(format!("no se pudo contactar a Ollama: {e}")));
            }
        };

        let status = respuesta.status();
        if !status.is_success() {
            let detalle = respuesta.text().await.unwrap_or_default();
            return Err(AppError::IaNoDisponible(format!("Ollama respondió {status}: {detalle}")));
        }

        let cuerpo: RespuestaChat = respuesta
            .json()
            .await
            .map_err(|e| AppError::IaNoDisponible(format!("respuesta de Ollama no reconocida: {e}")))?;

        let traduccion = cuerpo.message.content.trim().to_string();
        if traduccion.is_empty() {
            return Err(AppError::IaNoDisponible("Ollama devolvió una traducción vacía".to_string()));
        }
        Ok(traduccion)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{body_string_contains, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    fn cliente(base_url: String) -> ClienteOllama {
        ClienteOllama::nuevo(reqwest::Client::new(), base_url, "translategemma:12b".to_string())
    }

    #[tokio::test]
    async fn traduce_pidiendo_el_idioma_por_su_nombre_en_ingles() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/api/chat"))
            .and(body_string_contains("into English"))
            .and(body_string_contains("Todavia me duele"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "message": { "content": " My stomach still hurts \n" }
            })))
            .expect(1)
            .mount(&servidor)
            .await;

        let traduccion = cliente(servidor.uri()).traducir("Todavia me duele", "en").await.unwrap();

        assert_eq!(traduccion, "My stomach still hurts");
    }

    #[tokio::test]
    async fn una_barra_final_en_la_url_no_duplica_la_ruta() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/api/chat"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "message": { "content": "hola" }
            })))
            .mount(&servidor)
            .await;

        let cliente = ClienteOllama::nuevo(reqwest::Client::new(), format!("{}/", servidor.uri()), "m".into());

        assert_eq!(cliente.traducir("hello", "es").await.unwrap(), "hola");
    }

    #[tokio::test]
    async fn una_traduccion_vacia_no_se_devuelve_como_valida() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/api/chat"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "message": { "content": "   " }
            })))
            .mount(&servidor)
            .await;

        assert!(matches!(
            cliente(servidor.uri()).traducir("hola", "en").await,
            Err(AppError::IaNoDisponible(_))
        ));
    }

    #[tokio::test]
    async fn si_la_conexion_falla_lo_intenta_una_segunda_vez() {
        // Sin servidor en ese puerto: las dos llamadas fallan y el error es claro.
        let cliente = ClienteOllama::nuevo(reqwest::Client::new(), "http://127.0.0.1:1".into(), "m".into());

        let error = cliente.traducir("hola", "en").await.unwrap_err();

        assert!(matches!(error, AppError::IaNoDisponible(ref d) if d.contains("no se pudo contactar")));
    }

    #[tokio::test]
    async fn un_error_del_servidor_es_ia_no_disponible() {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/api/chat"))
            .respond_with(ResponseTemplate::new(500).set_body_string("sin modelo"))
            .mount(&servidor)
            .await;

        assert!(matches!(
            cliente(servidor.uri()).traducir("hola", "en").await,
            Err(AppError::IaNoDisponible(_))
        ));
    }

    #[test]
    fn los_codigos_de_idioma_se_traducen_a_su_nombre() {
        assert_eq!(nombre_de_idioma("es-MX"), "Spanish");
        assert_eq!(nombre_de_idioma("EN"), "English");
        assert_eq!(nombre_de_idioma("xx"), "");
    }
}
