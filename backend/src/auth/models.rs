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
    /// Entró con la contraseña temporal que le dio su médico: la app lo manda
    /// a elegir una nueva antes de cualquier otra cosa.
    pub requiere_cambio_contrasena: bool,
}

/// `contrasenaActual` solo se puede omitir en el cambio obligatorio tras un
/// reset: esa sesión se abrió con la contraseña temporal hace un momento.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CambiarContrasenaRequest {
    pub contrasena_actual: Option<String>,
    pub contrasena_nueva: String,
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
