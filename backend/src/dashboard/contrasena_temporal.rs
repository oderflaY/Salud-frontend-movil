use argon2::password_hash::rand_core::{OsRng, RngCore};

/// Sin 0/o ni 1/l/i: el médico la dicta o la escribe en un papel, y el
/// paciente la teclea en el teléfono. Solo minúsculas por la misma razón.
const ALFABETO: &[u8] = b"abcdefghjkmnpqrstuvwxyz23456789";
const CARACTERES: usize = 10;

/// Contraseña temporal para el reset de cuenta, con forma `k7m4p-9qx2h`.
///
/// 10 caracteres de 31 posibles (~49 bits) sobran para una contraseña que
/// vence en horas, que Argon2 hace lenta de probar y que solo sirve para
/// elegir otra.
pub fn generar() -> String {
    let mut contrasena = String::with_capacity(CARACTERES + 1);
    for i in 0..CARACTERES {
        if i == CARACTERES / 2 {
            contrasena.push('-');
        }
        contrasena.push(caracter_aleatorio());
    }
    contrasena
}

/// Muestreo por rechazo: 256 no es múltiplo de 31, y tomar `byte % 31` a
/// secas haría más probables los primeros caracteres del alfabeto.
fn caracter_aleatorio() -> char {
    let limite = 256 - (256 % ALFABETO.len());
    loop {
        let mut byte = [0u8; 1];
        OsRng.fill_bytes(&mut byte);
        let valor = byte[0] as usize;
        if valor < limite {
            return ALFABETO[valor % ALFABETO.len()] as char;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::handlers::MINIMO_CARACTERES_CONTRASENA;

    #[test]
    fn tiene_la_forma_de_dos_bloques_de_cinco() {
        let c = generar();
        assert_eq!(c.len(), 11);
        assert_eq!(c.chars().nth(5), Some('-'));
    }

    #[test]
    fn solo_usa_caracteres_que_no_se_confunden() {
        for _ in 0..200 {
            let c = generar();
            assert!(c.chars().filter(|&ch| ch != '-').all(|ch| ALFABETO.contains(&(ch as u8))), "{c}");
            assert!(!c.contains(['0', 'o', '1', 'l', 'i']), "{c}");
        }
    }

    #[test]
    fn cumple_la_regla_de_contrasena_de_la_app() {
        assert!(generar().chars().count() >= MINIMO_CARACTERES_CONTRASENA);
    }

    #[test]
    fn no_se_repite() {
        let a: std::collections::HashSet<String> = (0..500).map(|_| generar()).collect();
        assert_eq!(a.len(), 500);
    }
}
