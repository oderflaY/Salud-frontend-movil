//! `urlPreview` de los adjuntos del chat en el panel. Un `<img src>` no puede
//! mandar `Authorization`, así que el enlace lleva su propia firma: vale solo
//! para ese adjunto y vence en una hora.
//!
//! Se firma con una clave derivada de `JWT_SECRET` y una audiencia propia:
//! aunque tiene forma de JWT, no sirve como sesión ni en Axum ni en PostgREST.

use chrono::Utc;
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

const AUDIENCIA: &str = "panel-adjunto";
const VIGENCIA_MINUTOS: i64 = 60;

#[derive(Serialize, Deserialize)]
struct Enlace {
    sub: String,
    aud: String,
    exp: usize,
}

fn clave(secreto: &str) -> Vec<u8> {
    format!("{secreto}:enlaces-de-adjuntos-del-panel").into_bytes()
}

pub fn firmar(id_adjunto: &str, secreto: &str) -> String {
    let enlace = Enlace {
        sub: id_adjunto.to_string(),
        aud: AUDIENCIA.to_string(),
        exp: (Utc::now().timestamp() + VIGENCIA_MINUTOS * 60) as usize,
    };
    encode(&Header::new(Algorithm::HS256), &enlace, &EncodingKey::from_secret(&clave(secreto)))
        .unwrap_or_default()
}

pub fn es_valido(firma: &str, id_adjunto: &str, secreto: &str) -> bool {
    let mut validacion = Validation::new(Algorithm::HS256);
    validacion.set_audience(&[AUDIENCIA]);
    validacion.set_required_spec_claims(&["exp", "aud", "sub"]);
    decode::<Enlace>(firma, &DecodingKey::from_secret(&clave(secreto)), &validacion)
        .map(|datos| datos.claims.sub == id_adjunto)
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::jwt;

    const SECRETO: &str = "secreto-de-prueba-suficientemente-largo-para-hs256";

    #[test]
    fn el_enlace_sirve_solo_para_su_adjunto() {
        let firma = firmar("adj_1", SECRETO);
        assert!(es_valido(&firma, "adj_1", SECRETO));
        assert!(!es_valido(&firma, "adj_2", SECRETO));
        assert!(!es_valido(&firma, "adj_1", "otro-secreto-distinto-y-largo-para-hs256"));
        assert!(!es_valido("basura", "adj_1", SECRETO));
    }

    #[test]
    fn un_enlace_no_es_una_sesion_ni_una_sesion_es_un_enlace() {
        let firma = firmar("adj_1", SECRETO);
        assert!(jwt::verificar(&firma, SECRETO).is_err());

        let sesion = jwt::emitir("adj_1", "medico", SECRETO, 1).unwrap();
        assert!(!es_valido(&sesion, "adj_1", SECRETO));
    }
}
