# Contexto del Sistema: Estructura JSON del Historial Médico (DTO)




## 1. Visión General
Este documento define el Objeto de Transferencia de Datos (DTO) central del ecosistema de asistencia médica. Representa la estructura JSON estandarizada que consumirán la aplicación móvil (Kotlin Multiplatform) y el panel web (React), gestionada y validada por la API en el backend (Go). Utiliza este documento como fuente de verdad para modelar bases de datos, generar interfaces y estructurar peticiones de red.

## 2. Normativas de Datos y Tipado
* **Formato de Fechas:** Todo campo temporal debe usar estrictamente el formato estándar ISO 8601 en UTC (ej. `YYYY-MM-DDTHH:MM:SSZ`).
* **Nomenclatura de IDs:** Se utilizarán identificadores alfanuméricos únicos con prefijos semánticos para detectar rápidamente la entidad en la base de datos (ej. `pac_` para paciente, `rfid_` para tarjetas, `trt_` para tratamientos, `doc_` para médicos).
* **Segmentación de Seguridad:** El nodo `perfilEmergenciaReducido` es el único bloque de datos que la API devolverá cuando un paramédico escanee la tarjeta RFID. La descarga de nodos como `historialClinico` o `registroAdherencia` exige un token JWT con privilegios de médico tratante.
* **Bajas Lógicas:** Las tarjetas perdidas no se eliminan de la base de datos; su estado cambia a `extraviada` y se les asigna una `fechaRevocacion` para mantener la trazabilidad.

## 3. Esquema JSON (Payload Central)

```json
{
  "idPaciente": "pac_01H8X9A",
  "estadoCuenta": "activo",
  "identificaciones": {
    "curp": "PAGJ850412HDFRXX09",
    "nss": "12345678901"
  },
  "dispositivosRfid": [
    {
      "idTarjetaRfid": "rfid_hash_abc123",
      "estado": "activa",
      "fechaAsignacion": "2026-01-10T10:00:00Z",
      "fechaRevocacion": null
    }
  ],
  "datosPersonales": {
    "nombre": "Juan",
    "apellidos": "Pérez Gómez",
    "fechaNacimiento": "1985-04-12",
    "genero": "Masculino",
    "telefono": "+526181234567"
  },
  "contactosEmergencia": [
    {
      "nombre": "María Gómez",
      "relacion": "Madre",
      "telefono": "+526189876543",
      "prioridad": 1
    }
  ],
  "perfilEmergenciaReducido": {
    "tipoSangre": "O+",
    "donadorOrganos": true,
    "alergias": [
      {
        "alergeno": "Penicilina",
        "severidad": "Alta (Anafilaxia)",
        "reaccion": "Cierre de vías respiratorias"
      }
    ],
    "condicionesCriticas": [
      "Hipertensión arterial"
    ],
    "medicacionRescate": [
      "Anticoagulantes activos - Riesgo de hemorragia"
    ]
  },
  "metricasVitalesActuales": {
    "pesoKg": 78.5,
    "alturaCm": 175,
    "imc": 25.6,
    "ultimaPresionArterial": "120/80",
    "fechaTomaMetricas": "2026-09-01T08:00:00Z"
  },
  "historialClinico": {
    "cirugias": [
      {
        "procedimiento": "Apendicectomía",
        "fecha": "2015-08-20",
        "notas": "Sin complicaciones"
      }
    ]
  },
  "tratamientosActivos": [
    {
      "idTratamiento": "trt_001",
      "medicamento": "Losartán",
      "dosis": "50mg",
      "frecuenciaHoras": 12,
      "horariosSugeridos": ["08:00", "20:00"],
      "viaAdministracion": "Oral",
      "fechaInicio": "2026-08-01",
      "fechaFin": "2026-12-31",
      "idMedicoReceta": "doc_112233",
      "inventario": {
        "cantidadRestante": 14,
        "umbralAlerta": 5
      }
    }
  ],
  "registroAdherencia": [
    {
      "idToma": "log_8899",
      "idTratamiento": "trt_001",
      "fechaHoraProgramada": "2026-09-05T08:00:00Z",
      "fechaHoraReal": "2026-09-05T08:45:00Z",
      "estado": "tomado_tarde",
      "sintomasAsociados": "Ligero mareo"
    }
  ],
  "controlAccesos": {
    "medicosAutorizados": [
      {
        "idMedico": "doc_112233",
        "nivelAcceso": "lectura_escritura",
        "fechaExpiracion": "2027-01-01T00:00:00Z"
      }
    ]
  }
}




y dime papi quiero mas cada vez que acabes una tarea y al emepzarla 