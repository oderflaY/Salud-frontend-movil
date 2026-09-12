use serde::Serialize;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DatosPersonalesReducidos {
    pub nombre: String,
    pub apellidos: String,
    pub fecha_nacimiento: Option<chrono::NaiveDate>,
}

#[derive(Debug, Serialize)]
pub struct AlergiaReducida {
    pub alergeno: String,
    pub severidad: Option<String>,
    pub reaccion: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PerfilEmergenciaReducido {
    pub tipo_sangre: Option<String>,
    pub donador_organos: bool,
    pub alergias: Vec<AlergiaReducida>,
    pub condiciones_criticas: Vec<String>,
    pub medicacion_rescate: Vec<String>,
}

/// Deliberadamente pobre: nunca debe crecer para incluir teléfono, CURP,
/// historial completo ni tratamientos (ver docs/contratos-datos-backend.md
/// sección 5). Cualquier campo nuevo aquí debe pasar esa misma revisión
/// antes de agregarse.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PerfilSupervivencia {
    pub id_tarjeta_rfid: String,
    pub datos_personales: DatosPersonalesReducidos,
    pub perfil_emergencia_reducido: PerfilEmergenciaReducido,
}
