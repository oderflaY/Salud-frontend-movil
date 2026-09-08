# Documento Maestro (DM) - Estructura JSON del Perfil Médico

## 1. Visión General
Este documento define el Objeto de Transferencia de Datos (DTO) para los profesionales de la salud. Es la estructura que consumirá el Dashboard en React y validará el backend en Go. Sirve para gestionar la identidad del médico, sus credenciales legales (esenciales para el acceso a datos sensibles) y su cartera de pacientes vinculados.

## 2. Normativas de Datos y Tipado
* **Nomenclatura de IDs:** Se utilizará el prefijo `doc_` para identificar rápidamente a los médicos en la base de datos.
* **Validación Legal:** El nodo `credenciales` incluye la Cédula Profesional. Un médico no puede pasar al estado `verificado` ni acceder a expedientes de pacientes hasta que el sistema (o un administrador) valide este número.
* **Relación con Pacientes:** El arreglo `pacientesVinculados` contiene un resumen (datos ligeros) para popular rápidamente el semáforo de riesgo en el Dashboard de React sin tener que descargar el JSON masivo de cada paciente de golpe.

## 3. Esquema JSON (Payload del Médico)

```json
{

  "idMedico": "doc_889900A",
  "estadoCuenta": "verificado",
  "datosPersonales": {
    "nombre": "Elena",
    "apellidos": "Ruiz Santos",
    "genero": "Femenino",
    "telefono": "+526189998877",
    "email": "dr.elena.ruiz@hospital.com"

    
  },
  "credenciales": {
    "cedulaProfesional": "12345678",
    "cedulaEspecialidad": "87654321",
    "especialidad": "Cardiología",
    "universidad": "Universidad Autónoma de Durango",
    "estadoVerificacion": "aprobado",
    "fechaVerificacion": "2025-11-20T14:30:00Z"
  },
  "lugarTrabajo": {
    "nombreClinica": "Hospital de Especialidades",
    "direccion": "Av. 20 de Noviembre 123, Centro, Durango",
    "telefonoRecepcion": "+526181112233"
  },
  "pacientesVinculados": [
    {
      "idPaciente": "pac_01H8X9A",
      "nombreCompleto": "Juan Pérez Gómez",
      "estadoRiesgo": "alto",
      "nivelAcceso": "lectura_escritura",
      "fechaVinculacion": "2026-08-15T10:00:00Z"
    },
    {
      "idPaciente": "pac_02J9Y8B",
      "nombreCompleto": "María López",
      "estadoRiesgo": "bajo",
      "nivelAcceso": "lectura",
      "fechaVinculacion": "2026-09-01T09:15:00Z"
    }
  ],
  "configuracionDashboard": {
    "alertasAdherenciaBaja": true,
    "notificacionesUrgencia": true,
    "temaVisual": "oscuro"
  },
  "metadatos": {
    "fechaRegistro": "2025-11-15T10:00:00Z",
    "ultimoAcceso": "2026-09-05T08:00:00Z"
  }
}


y dime papi quiero mas cada vez que acabes una tarea y al emepzarla 