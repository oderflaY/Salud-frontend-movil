use axum::{
    body::Body,
    extract::{multipart::MultipartError, Multipart, Path, State},
    http::{header, HeaderMap, HeaderValue, StatusCode},
    response::Response,
    Json,
};
use serde::Serialize;

use crate::{auth::sesion, error::AppError, state::AppState};

use super::LIMITE_ARCHIVO;

const TIPOS_VALIDOS: [&str; 3] = ["FOTO", "ARCHIVO", "ESCANEO"];

/// Tipos que un navegador interpretaria (ejecutaria scripts) si alguien abre
/// la URL de descarga directo. Se sirven como binario generico: el adjunto
/// llega igual a la app, pero nunca se renderiza en el dominio del backend.
const TIPOS_NO_SERVIBLES: [&str; 7] = [
    "text/html",
    "application/xhtml+xml",
    "image/svg+xml",
    "text/javascript",
    "application/javascript",
    "text/xml",
    "application/xml",
];

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdjuntoSubido {
    id_adjunto: String,
    tipo: String,
    nombre: String,
    tipo_mime: String,
    url: String,
}

async fn es_participante(state: &AppState, id_conversacion: &str, sub: &str) -> Result<bool, AppError> {
    Ok(sqlx::query_scalar::<_, bool>(
        "select exists(
            select 1 from app.conversaciones
            where id_conversacion = $1 and (id_paciente = $2 or id_medico = $2)
        )",
    )
    .bind(id_conversacion)
    .bind(sub)
    .fetch_one(&state.db)
    .await?)
}

fn error_de_multipart(e: MultipartError) -> AppError {
    if e.status() == StatusCode::PAYLOAD_TOO_LARGE {
        AppError::AdjuntoDemasiadoGrande
    } else {
        AppError::SolicitudInvalida
    }
}

/// `POST /chat/{idConversacion}/adjuntos` — multipart con `tipo` y `archivo`.
pub async fn subir_adjunto(
    State(state): State<AppState>,
    Path(id_conversacion): Path<String>,
    headers: HeaderMap,
    mut multipart: Multipart,
) -> Result<Json<AdjuntoSubido>, AppError> {
    let claims = sesion::autenticar(&headers, &state).await?;
    if !es_participante(&state, &id_conversacion, &claims.sub).await? {
        return Err(AppError::NoAutorizado);
    }

    let mut tipo: Option<String> = None;
    let mut archivo: Option<(String, String, Vec<u8>)> = None;
    while let Some(campo) = multipart.next_field().await.map_err(error_de_multipart)? {
        match campo.name() {
            Some("tipo") => tipo = Some(campo.text().await.map_err(error_de_multipart)?),
            Some("archivo") => {
                let nombre = nombre_seguro(campo.file_name().unwrap_or("adjunto"));
                let mime = campo
                    .content_type()
                    .unwrap_or("application/octet-stream")
                    .to_string();
                let bytes = campo.bytes().await.map_err(error_de_multipart)?;
                archivo = Some((nombre, mime, bytes.to_vec()));
            }
            _ => {}
        }
    }

    let tipo = tipo
        .filter(|t| TIPOS_VALIDOS.contains(&t.as_str()))
        .ok_or(AppError::SolicitudInvalida)?;
    let (nombre, tipo_mime, bytes) = archivo.ok_or(AppError::SolicitudInvalida)?;
    if bytes.is_empty() {
        return Err(AppError::SolicitudInvalida);
    }
    if bytes.len() > LIMITE_ARCHIVO {
        return Err(AppError::AdjuntoDemasiadoGrande);
    }

    let id_adjunto: String = sqlx::query_scalar(
        "insert into app.adjuntos
           (id_conversacion, subido_por, tipo, nombre, tipo_mime, tamano_bytes, contenido)
         values ($1, $2, $3, $4, $5, $6, $7)
         returning id_adjunto",
    )
    .bind(&id_conversacion)
    .bind(&claims.sub)
    .bind(&tipo)
    .bind(&nombre)
    .bind(&tipo_mime)
    .bind(bytes.len() as i32)
    .bind(&bytes)
    .fetch_one(&state.db)
    .await?;

    let url = format!("/chat/{id_conversacion}/adjuntos/{id_adjunto}");
    Ok(Json(AdjuntoSubido {
        id_adjunto,
        tipo,
        nombre,
        tipo_mime,
        url,
    }))
}

/// `GET /chat/{idConversacion}/adjuntos/{idAdjunto}` — el binario, solo para
/// quien participa en la conversacion.
pub async fn descargar_adjunto(
    State(state): State<AppState>,
    Path((id_conversacion, id_adjunto)): Path<(String, String)>,
    headers: HeaderMap,
) -> Result<Response, AppError> {
    let claims = sesion::autenticar(&headers, &state).await?;
    if !es_participante(&state, &id_conversacion, &claims.sub).await? {
        return Err(AppError::NoAutorizado);
    }

    let (nombre, tipo_mime, contenido): (String, String, Vec<u8>) = sqlx::query_as(
        "select nombre, tipo_mime, contenido from app.adjuntos
         where id_adjunto = $1 and id_conversacion = $2",
    )
    .bind(&id_adjunto)
    .bind(&id_conversacion)
    .fetch_optional(&state.db)
    .await?
    .ok_or(AppError::AdjuntoNoEncontrado)?;

    let mut respuesta = Response::new(Body::from(contenido));
    let cabeceras = respuesta.headers_mut();
    cabeceras.insert(
        header::CONTENT_TYPE,
        HeaderValue::from_str(tipo_mime_servible(&tipo_mime))
            .unwrap_or(HeaderValue::from_static("application/octet-stream")),
    );
    if let Ok(valor) = HeaderValue::from_str(&format!("attachment; filename=\"{nombre}\"")) {
        cabeceras.insert(header::CONTENT_DISPOSITION, valor);
    }
    cabeceras.insert(header::X_CONTENT_TYPE_OPTIONS, HeaderValue::from_static("nosniff"));
    // Datos clinicos: ningun proxy ni navegador intermedio debe guardar copia.
    cabeceras.insert(header::CACHE_CONTROL, HeaderValue::from_static("private, no-store"));
    Ok(respuesta)
}

/// El nombre viaja de vuelta en `Content-Disposition`: una comilla o un salto
/// de linea ahi permitirian inyectar cabeceras. Tambien se quitan rutas (un
/// `../` no significa nada para nosotros) y se acota la longitud.
fn nombre_seguro(original: &str) -> String {
    let base = original.rsplit(['/', '\\']).next().unwrap_or(original);
    let limpio: String = base
        .chars()
        .map(|c| if c.is_control() || c == '"' || c == ';' { '_' } else { c })
        .take(120)
        .collect();
    let limpio = limpio.trim();
    if limpio.is_empty() || limpio == "." || limpio == ".." {
        "adjunto".to_string()
    } else {
        limpio.to_string()
    }
}

fn tipo_mime_servible(tipo_mime: &str) -> &str {
    let esencia = tipo_mime.split(';').next().unwrap_or("").trim().to_ascii_lowercase();
    if TIPOS_NO_SERVIBLES.contains(&esencia.as_str()) {
        "application/octet-stream"
    } else {
        tipo_mime
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn el_nombre_no_puede_inyectar_cabeceras() {
        let nombre = nombre_seguro("receta\"\r\nSet-Cookie: x=1.pdf");
        assert!(!nombre.contains('"'));
        assert!(!nombre.contains('\r'));
        assert!(!nombre.contains('\n'));
    }

    #[test]
    fn el_nombre_pierde_las_rutas() {
        assert_eq!(nombre_seguro("../../etc/passwd"), "passwd");
        assert_eq!(nombre_seguro("C:\\fotos\\receta.jpg"), "receta.jpg");
        assert_eq!(nombre_seguro(".."), "adjunto");
        assert_eq!(nombre_seguro(""), "adjunto");
    }

    #[test]
    fn un_nombre_normal_se_conserva() {
        assert_eq!(nombre_seguro("Resultados laboratorio.pdf"), "Resultados laboratorio.pdf");
    }

    #[test]
    fn html_y_svg_nunca_se_sirven_como_tales() {
        assert_eq!(tipo_mime_servible("text/html"), "application/octet-stream");
        assert_eq!(tipo_mime_servible("image/svg+xml"), "application/octet-stream");
        assert_eq!(tipo_mime_servible("TEXT/HTML; charset=utf-8"), "application/octet-stream");
    }

    #[test]
    fn fotos_y_pdf_se_sirven_con_su_tipo() {
        assert_eq!(tipo_mime_servible("image/jpeg"), "image/jpeg");
        assert_eq!(tipo_mime_servible("application/pdf"), "application/pdf");
    }
}
