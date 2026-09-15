//! Cliente mínimo del endpoint de chat completions de DeepSeek (compatible
//! con el formato de OpenAI: `POST {base_url}/chat/completions`). Nada aquí
//! es específico de "resumen" o "traducción" — ambas operaciones son el
//! mismo tipo de llamada con un prompt de sistema distinto, así que se
//! comparte una sola función privada (`completar`).
//!
//! El `base_url` es configurable (`DEEPSEEK_BASE_URL`) a propósito: permite
//! apuntar las pruebas a un servidor HTTP falso (`wiremock`, ver los tests al
//! final de este archivo) sin tocar el código, y sin depender de tener una
//! API key real para verificar que la integración funciona.

use serde::{Deserialize, Serialize};

use crate::error::AppError;

#[derive(Clone)]
pub struct ClienteDeepSeek {
    http: reqwest::Client,
    base_url: String,
    api_key: String,
    modelo: String,
}

#[derive(Serialize)]
struct Mensaje {
    role: &'static str,
    content: String,
}

#[derive(Serialize)]
struct SolicitudChat {
    model: String,
    messages: Vec<Mensaje>,
    temperature: f32,
}

#[derive(Deserialize)]
struct RespuestaChat {
    choices: Vec<Eleccion>,
}

#[derive(Deserialize)]
struct Eleccion {
    message: MensajeRespuesta,
}

#[derive(Deserialize)]
struct MensajeRespuesta {
    content: String,
}

/// Un dato clinico extraido de un mensaje del paciente ("Sintomas" ->
/// "dolor de cabeza y mareo"). Misma forma que `PuntoResumenIa` del cliente.
#[derive(Debug, Serialize, Deserialize, PartialEq)]
pub struct PuntoResumen {
    pub etiqueta: String,
    pub valor: String,
}

#[derive(Deserialize)]
struct PuntosResumen {
    puntos: Vec<PuntoResumen>,
}

/// Los modelos a veces envuelven el JSON en un bloque ```json ... ``` aunque
/// se les pida no hacerlo. Se quita antes de parsear en vez de fallar.
fn sin_cercas_de_codigo(texto: &str) -> &str {
    let t = texto.trim();
    let t = t.strip_prefix("```json").or_else(|| t.strip_prefix("```")).unwrap_or(t);
    t.strip_suffix("```").unwrap_or(t).trim()
}

impl ClienteDeepSeek {
    pub fn nuevo(http: reqwest::Client, base_url: String, api_key: String, modelo: String) -> Self {
        Self { http, base_url, api_key, modelo }
    }

    async fn completar(&self, prompt_sistema: &str, contenido_usuario: String) -> Result<String, AppError> {
        let cuerpo = SolicitudChat {
            model: self.modelo.clone(),
            messages: vec![
                Mensaje { role: "system", content: prompt_sistema.to_string() },
                Mensaje { role: "user", content: contenido_usuario },
            ],
            // Baja a propósito: resumir/traducir son tareas de fidelidad al
            // original, no de creatividad.
            temperature: 0.2,
        };

        let respuesta = self
            .http
            .post(format!("{}/chat/completions", self.base_url))
            .bearer_auth(&self.api_key)
            .json(&cuerpo)
            .send()
            .await
            .map_err(|e| AppError::IaNoDisponible(format!("no se pudo contactar a DeepSeek: {e}")))?;

        let status = respuesta.status();
        if !status.is_success() {
            let detalle = respuesta.text().await.unwrap_or_default();
            return Err(AppError::IaNoDisponible(format!(
                "DeepSeek respondió {status}: {detalle}"
            )));
        }

        let cuerpo: RespuestaChat = respuesta
            .json()
            .await
            .map_err(|e| AppError::IaNoDisponible(format!("respuesta de DeepSeek no reconocida: {e}")))?;

        cuerpo
            .choices
            .into_iter()
            .next()
            .map(|eleccion| eleccion.message.content)
            .ok_or_else(|| AppError::IaNoDisponible("DeepSeek no devolvió ninguna respuesta".to_string()))
    }

    pub async fn resumir_conversacion(&self, texto_conversacion: &str) -> Result<String, AppError> {
        self.completar(
            "Eres un asistente que resume conversaciones entre un paciente y un médico. \
             Responde en español, en un máximo de 3 frases, describiendo solo lo que ya se \
             dijo. No agregues diagnósticos, consejos médicos, ni información que no esté en \
             la conversación.",
            texto_conversacion.to_string(),
        )
        .await
    }

    /// Puntos clinicos de UN mensaje largo del paciente, para que el medico lo
    /// lea de un vistazo (el original sigue a un toque de distancia en la app).
    /// Extrae, no interpreta: nada que el paciente no haya dicho.
    pub async fn resumir_mensaje_clinico(&self, texto: &str) -> Result<Vec<PuntoResumen>, AppError> {
        let contenido = self
            .completar(
                "Extraes datos clinicos del mensaje que un paciente le escribe a su medico. \
                 Responde UNICAMENTE con JSON, sin texto adicional, con esta forma exacta: \
                 {\"puntos\": [{\"etiqueta\": \"...\", \"valor\": \"...\"}]}. \
                 Usa solo estas etiquetas, y solo las que apliquen: \"Motivo\", \"Sintomas\", \
                 \"Desde cuando\", \"Intensidad\", \"Medicacion mencionada\", \"Senales de alarma\". \
                 Maximo 5 puntos, en espanol, cada valor en una frase corta. Incluye solo lo que \
                 el paciente dijo: no agregues diagnosticos, consejos ni suposiciones.",
                texto.to_string(),
            )
            .await?;

        let resumen: PuntosResumen = serde_json::from_str(sin_cercas_de_codigo(&contenido))
            .map_err(|e| AppError::IaNoDisponible(format!("DeepSeek no devolvio el JSON pedido: {e}")))?;
        if resumen.puntos.is_empty() {
            return Err(AppError::IaNoDisponible("DeepSeek no extrajo ningun punto".to_string()));
        }
        Ok(resumen.puntos)
    }

    pub async fn traducir(&self, texto: &str, idioma_destino: &str) -> Result<String, AppError> {
        self.completar(
            &format!(
                "Traduce el siguiente texto al idioma '{idioma_destino}'. Responde \
                 únicamente con la traducción, sin explicaciones ni comillas adicionales."
            ),
            texto.to_string(),
        )
        .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{header, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    fn cliente_de_prueba(base_url: String) -> ClienteDeepSeek {
        ClienteDeepSeek::nuevo(
            reqwest::Client::new(),
            base_url,
            "clave-de-prueba".to_string(),
            "deepseek-chat".to_string(),
        )
    }

    #[tokio::test]
    async fn resumir_conversacion_manda_el_prompt_correcto_y_devuelve_el_contenido() {
        let servidor = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .and(header("authorization", "Bearer clave-de-prueba"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "choices": [{ "message": { "content": "El paciente reportó dolor de cabeza." } }]
            })))
            .expect(1)
            .mount(&servidor)
            .await;

        let cliente = cliente_de_prueba(servidor.uri());
        let resumen = cliente
            .resumir_conversacion("PACIENTE: me duele la cabeza\nMEDICO: desde cuándo?")
            .await
            .unwrap();

        assert_eq!(resumen, "El paciente reportó dolor de cabeza.");
    }

    #[tokio::test]
    async fn traducir_devuelve_el_contenido_de_la_respuesta() {
        let servidor = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "choices": [{ "message": { "content": "Hello, how are you?" } }]
            })))
            .mount(&servidor)
            .await;

        let cliente = cliente_de_prueba(servidor.uri());
        let traduccion = cliente.traducir("Hola, ¿cómo estás?", "en").await.unwrap();

        assert_eq!(traduccion, "Hello, how are you?");
    }

    #[tokio::test]
    async fn un_status_de_error_de_deepseek_se_convierte_en_ia_no_disponible() {
        let servidor = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .respond_with(ResponseTemplate::new(401).set_body_string("clave inválida"))
            .mount(&servidor)
            .await;

        let cliente = cliente_de_prueba(servidor.uri());
        let resultado = cliente.traducir("hola", "en").await;

        assert!(matches!(resultado, Err(AppError::IaNoDisponible(_))));
    }

    #[tokio::test]
    async fn una_respuesta_sin_choices_se_convierte_en_ia_no_disponible() {
        let servidor = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({ "choices": [] })))
            .mount(&servidor)
            .await;

        let cliente = cliente_de_prueba(servidor.uri());
        let resultado = cliente.traducir("hola", "en").await;

        assert!(matches!(resultado, Err(AppError::IaNoDisponible(_))));
    }

    async fn servidor_que_responde(contenido: &str) -> MockServer {
        let servidor = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "choices": [{ "message": { "content": contenido } }]
            })))
            .mount(&servidor)
            .await;
        servidor
    }

    #[tokio::test]
    async fn resumir_mensaje_devuelve_los_puntos_extraidos() {
        let servidor = servidor_que_responde(
            r#"{"puntos":[{"etiqueta":"Sintomas","valor":"Tobillos hinchados"},{"etiqueta":"Desde cuando","valor":"Cuatro dias"}]}"#,
        )
        .await;

        let puntos = cliente_de_prueba(servidor.uri())
            .resumir_mensaje_clinico("se me hincharon los tobillos hace cuatro dias")
            .await
            .unwrap();

        assert_eq!(puntos.len(), 2);
        assert_eq!(puntos[0], PuntoResumen { etiqueta: "Sintomas".into(), valor: "Tobillos hinchados".into() });
    }

    #[tokio::test]
    async fn resumir_mensaje_tolera_el_json_envuelto_en_bloque_de_codigo() {
        let servidor = servidor_que_responde(
            "```json\n{\"puntos\":[{\"etiqueta\":\"Motivo\",\"valor\":\"Control de glucosa\"}]}\n```",
        )
        .await;

        let puntos = cliente_de_prueba(servidor.uri()).resumir_mensaje_clinico("x").await.unwrap();

        assert_eq!(puntos[0].valor, "Control de glucosa");
    }

    #[tokio::test]
    async fn resumir_mensaje_sin_json_valido_es_ia_no_disponible() {
        let servidor = servidor_que_responde("El paciente tiene dolor.").await;

        let resultado = cliente_de_prueba(servidor.uri()).resumir_mensaje_clinico("x").await;

        assert!(matches!(resultado, Err(AppError::IaNoDisponible(_))));
    }
}
