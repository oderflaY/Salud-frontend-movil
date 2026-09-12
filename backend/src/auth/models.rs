use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize)]
pub struct IniciarSesionRequest {
    pub correo: String,
    pub contrasena: String,
}

#[derive(Debug, Deserialize)]
pub struct CrearCuentaPacienteRequest {
    pub correo: String,
    pub contrasena: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CrearCuentaProfesionalRequest {
    pub correo: String,
    pub contrasena: String,
    pub nombre: String,
    pub apellidos: String,
    pub tratamiento: String,
    pub cedula_profesional: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SesionPaciente {
    pub id_paciente: String,
    pub token: String,
    pub requiere_onboarding: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SesionProfesional {
    pub id_medico: String,
    pub token: String,
    pub nombre: String,
    pub apellidos: String,
    pub tratamiento: String,
    pub estado_verificacion: String,
}
