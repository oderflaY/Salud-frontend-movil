use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Algorithm, Argon2, Params, Version,
};

/// `pepper` es `ARGON2_SECRET_KEY`: un secreto que vive solo en el proceso
/// (variable de entorno), nunca en la base de datos. Si la base se filtrara
/// entera, los hashes por sí solos no alcanzan para probar contraseñas sin
/// también tener este secreto.
fn instancia(pepper: &[u8]) -> Argon2<'_> {
    Argon2::new_with_secret(pepper, Algorithm::Argon2id, Version::V0x13, Params::default())
        .expect("parámetros de Argon2 inválidos")
}

pub fn hash(password: &str, pepper: &[u8]) -> Result<String, String> {
    let salt = SaltString::generate(&mut OsRng);
    instancia(pepper)
        .hash_password(password.as_bytes(), &salt)
        .map(|h| h.to_string())
        .map_err(|e| e.to_string())
}

pub fn verificar(password: &str, hash_almacenado: &str, pepper: &[u8]) -> bool {
    let Ok(parsed) = PasswordHash::new(hash_almacenado) else {
        return false;
    };
    instancia(pepper)
        .verify_password(password.as_bytes(), &parsed)
        .is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hash_y_verificar_da_positivo_con_la_misma_contrasena_y_pepper() {
        let pepper = b"pepper-de-prueba";
        let h = hash("una-contrasena-segura", pepper).unwrap();
        assert!(verificar("una-contrasena-segura", &h, pepper));
    }

    #[test]
    fn verificar_rechaza_contrasena_incorrecta() {
        let pepper = b"pepper-de-prueba";
        let h = hash("una-contrasena-segura", pepper).unwrap();
        assert!(!verificar("otra-contrasena", &h, pepper));
    }

    #[test]
    fn verificar_rechaza_si_el_pepper_no_coincide() {
        let h = hash("una-contrasena-segura", b"pepper-original").unwrap();
        assert!(!verificar("una-contrasena-segura", &h, b"pepper-distinto"));
    }

    #[test]
    fn hash_no_es_determinista_por_el_salt_aleatorio() {
        let pepper = b"pepper-de-prueba";
        let h1 = hash("misma-contrasena", pepper).unwrap();
        let h2 = hash("misma-contrasena", pepper).unwrap();
        assert_ne!(h1, h2, "cada hash debe llevar un salt distinto");
    }
}
