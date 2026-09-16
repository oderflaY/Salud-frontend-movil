//! El "contexto recuperado" que se le pasa al modelo: los hallazgos del
//! análisis con lo que dice la base de conocimiento de cada uno. Función pura,
//! separada del handler, para probar que el modelo nunca recibe más (ni
//! menos) de lo que el análisis encontró.

use serde_json::Value;

pub fn contexto_recuperado(analisis: &Value) -> String {
    let nivel = analisis.get("nivel").and_then(Value::as_str).unwrap_or("VERDE");
    let mut lineas = vec![format!("NIVEL CALCULADO: {nivel}")];

    let hallazgos = analisis.get("hallazgos").and_then(Value::as_array);
    match hallazgos {
        Some(lista) if !lista.is_empty() => {
            for h in lista {
                let campo = |nombre: &str| h.get(nombre).and_then(Value::as_str).unwrap_or("");
                lineas.push(format!(
                    "- {} ({}), frase del paciente: \"{}\". Puede indicar: {} Qué hacer: {}",
                    campo("termino"),
                    campo("categoria"),
                    campo("frase_detectada"),
                    campo("que_puede_indicar"),
                    campo("que_hacer"),
                ));
            }
        }
        _ => lineas.push("- Sin señales de alarma en la base de conocimiento.".to_string()),
    }

    if let Some(negados) = analisis.get("descartados_por_negacion").and_then(Value::as_array) {
        let terminos: Vec<&str> = negados.iter().filter_map(Value::as_str).collect();
        if !terminos.is_empty() {
            lineas.push(format!("NEGADOS POR EL PACIENTE (no cuentan): {}", terminos.join(", ")));
        }
    }
    lineas.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn incluye_cada_hallazgo_con_lo_que_indica_y_que_hacer() {
        let analisis = json!({
            "nivel": "ROJO",
            "hallazgos": [{
                "termino": "Dolor u opresión en el pecho", "categoria": "URGENCIA_FISICA",
                "frase_detectada": "me duele el pecho", "que_puede_indicar": "Infarto.", "que_hacer": "Llamar al 911."
            }],
            "descartados_por_negacion": ["Fiebre"]
        });

        let contexto = contexto_recuperado(&analisis);

        assert!(contexto.starts_with("NIVEL CALCULADO: ROJO"));
        assert!(contexto.contains("Dolor u opresión en el pecho (URGENCIA_FISICA)"));
        assert!(contexto.contains("Qué hacer: Llamar al 911."));
        assert!(contexto.contains("NEGADOS POR EL PACIENTE (no cuentan): Fiebre"));
    }

    #[test]
    fn sin_hallazgos_lo_dice_en_vez_de_dejar_el_contexto_vacio() {
        let contexto = contexto_recuperado(&json!({"nivel": "VERDE", "hallazgos": [], "descartados_por_negacion": []}));

        assert_eq!(contexto, "NIVEL CALCULADO: VERDE\n- Sin señales de alarma en la base de conocimiento.");
    }
}
