use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

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
}

pub fn emitir(
    sub: &str,
    role: &str,
    secret: &str,
    horas_expiracion: i64,
) -> Result<String, jsonwebtoken::errors::Error> {
    let exp = (Utc::now() + Duration::hours(horas_expiracion)).timestamp() as usize;
    let claims = Claims {
        sub: sub.to_string(),
        role: role.to_string(),
        exp,
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
}
