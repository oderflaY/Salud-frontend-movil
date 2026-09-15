use axum::http::HeaderMap;
use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

use crate::error::AppError;

/// Claims mínimos a propósito: `sub` (id) y `role` (el rol Postgres al que
/// PostgREST hace `SET ROLE`). Nunca se mete aquí la lista de pacientes
/// vinculados de un médico ni nada que cambie con frecuencia — eso lo
/// resuelve una política RLS consultando la tabla en cada request, no un
/// claim embebido que quedaría desactualizado hasta que el token expire.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,
    pub role: String,
    pub exp: usize,
    /// Cuándo se emitió. Es lo que permite cerrar las sesiones de una cuenta
    /// (`app.estado_sesion`, 0016): un token emitido antes de la marca de la
    /// cuenta se rechaza. Los tokens de antes de este campo no lo traen y
    /// cuentan como emitidos en 0 — solo importa si la cuenta tiene marca.
    #[serde(default)]
    pub iat: usize,
}

pub fn emitir(
    sub: &str,
    role: &str,
    secret: &str,
    horas_expiracion: i64,
) -> Result<String, jsonwebtoken::errors::Error> {
    let ahora = Utc::now();
    let claims = Claims {
        sub: sub.to_string(),
        role: role.to_string(),
        exp: (ahora + Duration::hours(horas_expiracion)).timestamp() as usize,
        iat: ahora.timestamp() as usize,
    };
    encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(secret.as_bytes()),
    )
}

/// Decodifica y valida un token ya emitido por `emitir` — usado por cualquier
/// endpoint de Axum que necesite saber quién llama (`crate::ia`,
/// `crate::realtime::ws`) sin pasar por PostgREST.
pub fn verificar(token: &str, secret: &str) -> Result<Claims, jsonwebtoken::errors::Error> {
    decode::<Claims>(token, &DecodingKey::from_secret(secret.as_bytes()), &Validation::default())
        .map(|datos| datos.claims)
}

/// Quién llama, a partir de `Authorization: Bearer <token>`. La conexión de
/// Axum a Postgres es la del rol dueño y no pasa por PostgREST, así que no
/// existe el GUC `request.jwt.claims` que leen las funciones RPC: cada
/// endpoint de Axum con sesión valida el token aquí, en un solo lugar.
pub fn autenticar(headers: &HeaderMap, secret: &str) -> Result<Claims, AppError> {
    let valor = headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .ok_or(AppError::NoAutorizado)?;
    let token = valor.strip_prefix("Bearer ").ok_or(AppError::NoAutorizado)?;
    verificar(token, secret).map_err(|_| AppError::NoAutorizado)
}

#[cfg(test)]
mod tests {
    use super::*;
    use jsonwebtoken::{decode, DecodingKey, Validation};

    #[test]
    fn emitir_produce_un_token_decodificable_con_el_mismo_secreto() {
        let token = emitir("pac_abc", "paciente", "secreto-de-prueba", 1).unwrap();

        let datos = decode::<Claims>(
            &token,
            &DecodingKey::from_secret("secreto-de-prueba".as_bytes()),
            &Validation::default(),
        )
        .unwrap();

        assert_eq!(datos.claims.sub, "pac_abc");
        assert_eq!(datos.claims.role, "paciente");
    }

    #[test]
    fn decodificar_con_secreto_distinto_falla() {
        let token = emitir("pac_abc", "paciente", "secreto-correcto", 1).unwrap();

        let resultado = decode::<Claims>(
            &token,
            &DecodingKey::from_secret("secreto-incorrecto".as_bytes()),
            &Validation::default(),
        );

        assert!(resultado.is_err());
    }

    #[test]
    fn verificar_recupera_los_mismos_claims_que_emitir() {
        let token = emitir("doc_xyz", "medico", "secreto-de-prueba", 1).unwrap();

        let claims = verificar(&token, "secreto-de-prueba").unwrap();

        assert_eq!(claims.sub, "doc_xyz");
        assert_eq!(claims.role, "medico");
    }

    #[test]
    fn el_token_lleva_cuando_se_emitio() {
        let antes = Utc::now().timestamp() as usize;
        let claims = verificar(&emitir("pac_abc", "paciente", "s", 1).unwrap(), "s").unwrap();
        assert!(claims.iat >= antes && claims.iat <= antes + 1);
    }

    #[test]
    fn un_token_anterior_sin_iat_se_sigue_leyendo() {
        #[derive(Serialize)]
        struct ClaimsViejos {
            sub: String,
            role: String,
            exp: usize,
        }
        let exp = (Utc::now() + Duration::hours(1)).timestamp() as usize;
        let viejo = encode(
            &Header::default(),
            &ClaimsViejos { sub: "pac_abc".into(), role: "paciente".into(), exp },
            &EncodingKey::from_secret(b"s"),
        )
        .unwrap();

        assert_eq!(verificar(&viejo, "s").unwrap().iat, 0);
    }
}
