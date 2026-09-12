# Contratos de datos para el backend

Este documento traduce las interfaces `domain/repository/*` del cliente Kotlin
Multiplatform a un mapeo REST/WebSocket concreto, para que el backend pueda
implementarse sin leer el código Kotlin. Es un **mapeo sugerido**, no
una URL ya decidida en piedra: lo que sí es contrato firme es la forma de cada
payload (nombres de campo, tipos, nulabilidad) y el comportamiento descrito en
cada método, porque de eso depende el código del cliente que ya existe.

Fuente de verdad del lado del cliente: `shared/src/commonMain/kotlin/com/eter/salud/domain/repository/` (11 interfaces) y `domain/model/` (los DTOs). Los documentos `DM_Arquitectura_App.md`, `DM_HistorialMedico.md` y `DM_PerfilMedico.md` en la raíz del repo son la referencia normativa de la que salen estos modelos; ante cualquier ambigüedad, esos DM mandan.

> Nota de implementación (2026-09-12): la versión final decidida para este proyecto es
> **PostgreSQL + PostgREST + un servicio Rust/Axum delgado**, desplegado con Docker. Ver
> `db/` y `backend/` para el mapeo real de este documento a esa arquitectura. Este archivo
> se conserva tal cual como la fuente normativa de payloads/comportamiento del cliente.

## Convenciones globales

- **Fechas de calendario**: `YYYY-MM-DD` (nacimiento, cirugías, fecha de una cita, fecha de una entrada del diario).
- **Horas locales**: `HH:MM`, en la hora de pared del consultorio del médico — nunca convertidas a UTC. Una agenda se razona en hora local; convertirla obligaría al cliente a deshacer la conversión en cada pantalla y un cambio de horario de verano movería citas ya agendadas.
- **Instantes**: ISO 8601 UTC, formato `YYYY-MM-DDTHH:MM:SSZ` (mensajes de chat, entradas del diario, expiración de una reserva, timestamps del historial clínico).
- **IDs**: alfanuméricos con prefijo semántico — `pac_`, `doc_`, `rfid_`, `trt_`, `cita_`, `conv_`, `msg_`, `entrada_`, `franja_`, `reserva_`. El cliente nunca construye un ID, siempre los recibe del backend.
- **Autenticación**: JWT Bearer en el header `Authorization` tras `iniciarSesion`/`crearCuenta`. El único endpoint que se invoca sin sesión es la consulta de emergencia por tarjeta RFID (la tarjeta física es la credencial).
- **Errores**: cada método del cliente espera un **motivo tipado**, no un mensaje de texto libre. La tabla de cada sección abajo da el mapeo `motivo enum → HTTP status`; el cuerpo de error sugerido es `{"motivo": "FRANJA_OCUPADA"}` para que el cliente no tenga que parsear prosa.
- **Serialización**: hoy solo `PacienteDto` y `PerfilSupervivencia` llevan `@Serializable` (kotlinx.serialization) en el cliente. El resto de los modelos (`Cita`, `MensajeChat`, `TomaDelDia`, etc.) son `data class` planas — **hay que añadirles `@Serializable`** al cablear el cliente HTTP real; los nombres de campo de este documento son los que debe respetar el JSON para que ese cableado sea un mapeo directo sin renombrar nada.
- **Streams en vivo**: tres métodos del cliente son `Flow<T>` y no una consulta puntual — `agendaDelMedico`, `mensajesSinLeer`, `entradasDe`. Necesitan WebSocket o SSE (ver sección final); todo lo demás es petición/respuesta normal.

---

## 1. Autenticación — paciente

Contrato: `AutenticacionRepositorio`

| Método cliente | Endpoint sugerido | Body | Respuesta |
|---|---|---|---|
| `iniciarSesion(correo, contrasena)` | `POST /auth/pacientes/sesion` | `{"correo": string, "contrasena": string}` | `SesionPaciente` |
| `crearCuenta(correo, contrasena)` | `POST /auth/pacientes` | `{"correo": string, "contrasena": string}` | `SesionPaciente` |

**`SesionPaciente`**
```json
{
  "idPaciente": "pac_xxx",
  "token": "jwt...",
  "requiereOnboarding": true
}
```
`requiereOnboarding: true` significa cuenta creada pero sin perfil de emergencia — el cliente manda al usuario al onboarding de Fase 1 en vez de a la Home.

**Errores** (`FalloAutenticacion` / `FalloRegistro`):

| Motivo | HTTP | Cuándo |
|---|---|---|
| `CREDENCIALES_INVALIDAS` | 401 | correo/contraseña no coinciden |
| `CUENTA_BLOQUEADA` | 403 | cuenta suspendida |
| `CORREO_YA_REGISTRADO` | 409 | alta con correo existente |
| `SIN_CONEXION` | — | el cliente lo genera localmente si la red falla; el backend no necesita emitirlo |

---

## 2. Autenticación — profesional (médico/paramédico)

Contrato: `AutenticacionProfesionalRepositorio`. Vive separado del anterior: son dos portales con reglas de alta distintas (DM_PerfilMedico.md, sección 2).

| Método cliente | Endpoint sugerido | Body | Respuesta |
|---|---|---|---|
| `iniciarSesion(correo, contrasena)` | `POST /auth/profesionales/sesion` | `{"correo", "contrasena"}` | `SesionProfesional` |
| `crearCuenta(correo, contrasena, nombre, apellidos, tratamiento, cedulaProfesional)` | `POST /auth/profesionales` | ver abajo | `SesionProfesional` |

Body de alta:
```json
{
  "correo": "string",
  "contrasena": "string",
  "nombre": "string",
  "apellidos": "string",
  "tratamiento": "Dr. | Dra. | Dr(a).",
  "cedulaProfesional": "string"
}
```

**`SesionProfesional`**
```json
{
  "idMedico": "doc_xxx",
  "token": "jwt...",
  "nombre": "Elena",
  "apellidos": "Ruiz Santos",
  "tratamiento": "Dra.",
  "estadoVerificacion": "PENDIENTE"
}
```
`estadoVerificacion` ∈ `PENDIENTE | APROBADO | RECHAZADO`. Toda alta nueva nace en `PENDIENTE`: la cuenta existe pero la cédula profesional aún no fue validada por el backend/un administrador. El cliente ya renderiza ese estado; el backend debe tener un flujo (manual o automatizado) que la suba a `APROBADO`.

**Errores** (`FalloAutenticacionProfesional` / `FalloRegistroProfesional`):

| Motivo | HTTP |
|---|---|
| `CREDENCIALES_INVALIDAS` | 401 |
| `CUENTA_BLOQUEADA` | 403 |
| `CORREO_YA_REGISTRADO` | 409 |
| `CEDULA_YA_REGISTRADA` | 409 — la cédula profesional debe ser única en todo el sistema |

---

## 3. Alta y expediente del paciente (`PacienteDto`)

Contratos: `PacienteRepositorio` + `HistorialMedicoRepositorio`. Se mantienen separados a propósito: el alta y la edición del expediente tienen permisos distintos (DM_HistorialMedico.md, sección 2, Segmentación de Seguridad) — quien solo registra no debe poder leer el expediente completo.

| Método cliente | Endpoint sugerido | Notas |
|---|---|---|
| `registrarPaciente(paciente: PacienteDto)` | `POST /pacientes` | responde solo el `idPaciente` asignado (string, prefijo `pac_`) |
| `obtenerPaciente(idPaciente)` | `GET /pacientes/{idPaciente}` | requiere token con privilegios sobre ese paciente |
| `actualizarHistorial(paciente: PacienteDto)` | `PUT /pacientes/{idPaciente}` | el backend asigna y conserva los IDs internos (tratamientos, etc.) |

`PacienteDto` completo (todo opcional salvo lo marcado; lo que el onboarding no capturó se omite, nunca se manda como ruido):

```jsonc
{
  "idPaciente": "pac_xxx",             // null al registrar
  "estadoCuenta": "string",
  "identificaciones": {
    "curp": "string", "nss": "string", "aseguradora": "string"
  },
  "dispositivosRfid": [
    { "idTarjetaRfid": "rfid_xxx", "estado": "string",
      "fechaAsignacion": "YYYY-MM-DD", "fechaRevocacion": "YYYY-MM-DD" }
  ],
  "datosPersonales": {                  // requerido en la práctica
    "nombre": "string", "apellidos": "string",
    "fechaNacimiento": "YYYY-MM-DD", "genero": "string", "telefono": "string"
  },
  "contactosEmergencia": [
    { "nombre": "string", "relacion": "string", "telefono": "string", "prioridad": 1 }
  ],
  "perfilEmergenciaReducido": {
    "tipoSangre": "string", "donadorOrganos": true,
    "alergias": [ { "alergeno": "string", "severidad": "string", "reaccion": "string" } ],
    "condicionesCriticas": ["string"],
    "medicacionRescate": ["string"]
  },
  "metricasVitalesActuales": {
    "pesoKg": 70.0, "alturaCm": 170,
    "imc": 24.2,                         // lo calcula el CLIENTE, el backend solo lo persiste
    "ultimaPresionArterial": "120/80",
    "fechaTomaMetricas": "ISO-8601 UTC"
  },
  "historialClinico": {
    "cirugias": [ { "procedimiento": "string", "fecha": "YYYY-MM-DD", "notas": "string" } ],
    "antecedentesHeredofamiliares": ["string"]
  },
  "tratamientosActivos": [
    { "idTratamiento": "trt_xxx",        // null al dar de alta, lo asigna el backend
      "medicamento": "string", "dosis": "string", "frecuenciaHoras": 8,
      "horariosSugeridos": ["HH:MM"], "viaAdministracion": "string",
      "fechaInicio": "YYYY-MM-DD", "fechaFin": "YYYY-MM-DD",
      "idMedicoReceta": "doc_xxx",
      "inventario": { "cantidadRestante": 20, "umbralAlerta": 5 } }
  ],
  "registroAdherencia": [
    { "idToma": "string", "idTratamiento": "trt_xxx",
      "fechaHoraProgramada": "ISO-8601 UTC", "fechaHoraReal": "ISO-8601 UTC",
      "estado": "pendiente|tomado|tomado_tarde|omitido", "sintomasAsociados": "string" }
  ],
  "controlAccesos": {
    "medicosAutorizados": [
      { "idMedico": "doc_xxx", "nivelAcceso": "string", "fechaExpiracion": "YYYY-MM-DD" }
    ]
  }
}
```

Notas de negocio:
- `imc` lo calcula el cliente a partir de peso/altura; el backend lo guarda tal cual, no lo recalcula.
- `estado` en `registroAdherencia`/`EstadoToma` usa los valores de texto en minúsculas (`pendiente`, `tomado`, `tomado_tarde`, `omitido`) — son los que persiste el DM, distintos del nombre del enum Kotlin.
- `umbralAlerta` por defecto es `5` si el cliente no lo manda.

---

## 4. Adherencia (tomas de medicación)

Contrato: `AdherenciaRepositorio`. Separado del expediente porque su ritmo de escritura es mucho mayor (varias veces al día vs. consultas ocasionales).

| Método cliente | Endpoint sugerido | Respuesta |
|---|---|---|
| `obtenerTomasDelDia(idPaciente, fecha)` | `GET /pacientes/{idPaciente}/tomas?fecha=YYYY-MM-DD` | `List<TomaDelDia>` |
| `registrarToma(idPaciente, idToma, estado, instante)` | `POST /pacientes/{idPaciente}/tomas/{idToma}/registro` | `Unit` (204) |
| `obtenerSemana(idPaciente, fechaFinal)` | `GET /pacientes/{idPaciente}/adherencia/semana?fechaFinal=YYYY-MM-DD` | `List<DiaDeAdherencia>` (**siempre 7**) |
| `obtenerResumenSemanal(idPaciente, fechaFinal)` | `GET /pacientes/{idPaciente}/adherencia/resumen?fechaFinal=YYYY-MM-DD` | `ResumenAdherencia` |

`TomaDelDia`:
```json
{ "idToma": "string", "idTratamiento": "trt_xxx", "medicamento": "string",
  "dosis": "string", "horaProgramada": "HH:MM", "estado": "pendiente" }
```

Body de `registrarToma`:
```json
{ "estado": "tomado|omitido", "instante": "ISO-8601 UTC" }
```
**Regla de negocio crítica**: el cliente solo envía `tomado` u `omitido` con el instante real. **El backend decide** si eso se traduce en `tomado` o `tomado_tarde`, comparando contra el horario programado y la zona horaria del tratamiento. Hacerlo en el cliente sería comparar un instante UTC contra una hora local y se equivocaría al cruzar medianoche.

`DiaDeAdherencia` (la respuesta de `/adherencia/semana` debe traer exactamente 7 elementos, incluidos los días sin tratamiento — un hueco ausente descuadra la franja semanal del panel):
```json
{ "fecha": "YYYY-MM-DD", "tomasProgramadas": 4, "tomasCumplidas": 3, "enCurso": false }
```
`enCurso` lo decide el backend (conoce la zona horaria del tratamiento), no se debe calcular comparando con el reloj del dispositivo.

`ResumenAdherencia`:
```json
{ "tomasProgramadas": 14, "tomasCumplidas": 9 }
```
(el cliente calcula el porcentaje mostrado; el backend solo entrega los dos conteos).

---

## 5. Emergencia — consulta por tarjeta RFID

Contrato: `PerfilEmergenciaRepositorio`. **Es el único endpoint invocable sin sesión** — la tarjeta física es la credencial.

| Método cliente | Endpoint sugerido |
|---|---|
| `consultarPorTarjeta(idTarjetaRfid)` | `GET /tarjetas/{idTarjetaRfid}/perfil-supervivencia` |

Respuesta (`PerfilSupervivencia`, ya `@Serializable` en el cliente):
```json
{
  "idTarjetaRfid": "rfid_xxx",
  "datosPersonales": { "nombre": "string", "apellidos": "string", "fechaNacimiento": "YYYY-MM-DD" },
  "perfilEmergenciaReducido": {
    "tipoSangre": "string", "donadorOrganos": true,
    "alergias": [ { "alergeno": "string", "severidad": "string", "reaccion": "string" } ],
    "condicionesCriticas": ["string"], "medicacionRescate": ["string"]
  }
}
```
**Deliberadamente pobre**: nunca debe incluir teléfono, CURP, historial completo ni tratamientos — DM_HistorialMedico.md sección 2 limita explícitamente lo que se entrega sin token de médico tratante. Si este payload creciera, una tarjeta perdida se convertiría en una fuga del expediente completo.

Errores (`FalloEmergencia`):

| Motivo | HTTP |
|---|---|
| `TARJETA_DESCONOCIDA` | 404 |
| `TARJETA_REVOCADA` | 403 — baja lógica: la tarjeta existe pero fue reportada extraviada |
| `SIN_CONEXION` | — generado localmente por el cliente |

---

## 6. Cartera de pacientes del profesional

Contrato: `PacientesVinculadosRepositorio`. Separado del expediente: aquí solo va el resumen ligero para el semáforo de riesgo de la Home del médico.

| Método cliente | Endpoint sugerido |
|---|---|
| `obtenerPacientesVinculados(idMedico)` | `GET /profesionales/{idMedico}/pacientes` |

Respuesta — `List<PacienteVinculado>`:
```json
{ "idPaciente": "pac_xxx", "nombreCompleto": "string", "riesgo": "ALTO", "idConversacion": "conv_xxx" }
```
`riesgo` ∈ `ALTO | MEDIO | BAJO`. `idConversacion` es la misma clave que usa el chat del paciente — ambos portales leen/escriben la misma conversación.

---

## 7. Directorio médico

Contrato: `DirectorioMedicoRepositorio`.

| Método cliente | Endpoint sugerido | Respuesta |
|---|---|---|
| `obtenerMedicoVinculado(idPaciente)` | `GET /pacientes/{idPaciente}/medico-vinculado` | `MedicoVinculado?` (null si no tiene) |
| `obtenerMedicosVinculados(idPaciente)` | `GET /pacientes/{idPaciente}/medicos-vinculados` | `List<MedicoVinculado>` |
| `buscarDirectorio(especialidad?)` | `GET /directorio-medico?especialidad=CARDIOLOGIA` (query omitido = todas) | `List<PerfilDoctorDirectorio>` |
| `obtenerPerfilDeMedico(idMedico)` | `GET /directorio-medico/{idMedico}` | `PerfilDoctorDirectorio?` (null si ya no figura: baja, cédula revocada) |
| `solicitarVinculacion(idPaciente, idMedico)` | `POST /pacientes/{idPaciente}/vinculaciones` con `{"idMedico": "doc_xxx"}` | `MedicoVinculado` |

`Especialidad` (enum cerrado, 10 valores — el filtro compara contra valores exactos, la traducción vive en `strings.xml` del cliente):
`MEDICINA_GENERAL, CARDIOLOGIA, PEDIATRIA, DERMATOLOGIA, GINECOLOGIA, PSIQUIATRIA, GERIATRIA, ENDOCRINOLOGIA, NEUMOLOGIA, NEUROLOGIA`

`PerfilDoctorDirectorio`:
```json
{ "idMedico": "doc_xxx", "nombreCompleto": "Dra. Elena Ruiz Santos",
  "especialidad": "CARDIOLOGIA", "cedulaVerificada": true,
  "universidad": "string", "disponibilidad": "Inmediata para chat" }
```
`cedulaVerificada` espeja `credenciales.estadoVerificacion == "aprobado"` del DM. `disponibilidad` es texto libre del backend, no traducible.

`MedicoVinculado`:
```json
{ "idMedico": "doc_xxx", "nombreCompleto": "string", "especialidad": "CARDIOLOGIA", "idConversacion": "conv_xxx" }
```

Nota de rendimiento: `buscarDirectorio` se filtra por texto **en el cliente** sobre la lista ya descargada — no se espera un endpoint de búsqueda por substring en el servidor, solo el filtro por especialidad.

---

## 8. Chat de orientación

Contrato: `ChatRepositorio`.

| Método cliente | Endpoint sugerido | Respuesta |
|---|---|---|
| `obtenerHistorial(idConversacion)` | `GET /conversaciones/{idConversacion}/mensajes` | `List<MensajeChat>` |
| `enviarMensaje(idConversacion, texto, instante, autor)` | `POST /conversaciones/{idConversacion}/mensajes` | `MensajeChat` |
| `mensajesSinLeer(idConversacion)` | **WebSocket/SSE** — ver sección 11 | `Flow<Int>` |
| `marcarConversacionLeida(idConversacion)` | `POST /conversaciones/{idConversacion}/leida` | — |
| `obtenerRespuestaAutomatica(idConversacion, instante)` | `POST /conversaciones/{idConversacion}/acuse-recibo` | `MensajeChat` |

`MensajeChat`:
```json
{ "idMensaje": "msg_xxx", "autor": "PACIENTE|MEDICO", "texto": "string",
  "instante": "ISO-8601 UTC", "tipo": "NORMAL|ORIENTACION_INICIAL" }
```
`autor` viaja explícito en cada envío porque la misma conversación la usan las dos partes desde sus propios portales — darlo por supuesto pintaría los mensajes del médico como si fueran del paciente.

`obtenerRespuestaAutomatica` modela **una única cortesía del sistema** (acuse de recibo automático tras el primer mensaje del paciente) — nunca una IA respondiendo en nombre del médico. No diseñar esto como un endpoint de "bot" genérico.

---

## 9. Citas y agenda

Contrato: `CitasRepositorio` (`FalloCita`). El más complejo: lo comparten el chat del paciente (escribe citas) y la agenda del médico (las lee).

| Método cliente | Endpoint sugerido |
|---|---|
| `franjasLibres(idMedico, desde, ahora)` | `GET /profesionales/{idMedico}/franjas-libres?desde=YYYY-MM-DD&ahora=ISO-8601` |
| `reservarTemporalmente(idFranja, ahora)` | `POST /franjas/{idFranja}/reserva` body `{"ahora": "ISO-8601"}` |
| `liberarReserva(idReserva)` | `DELETE /reservas/{idReserva}` |
| `confirmarCita(idReserva, idPaciente, contacto, ahora)` | `POST /reservas/{idReserva}/confirmacion` |
| `agendaDelMedico(idMedico)` | **WebSocket/SSE** — ver sección 11 |
| `cargarAgenda(idMedico)` | `GET /profesionales/{idMedico}/agenda` (carga inicial; el stream cubre lo que viene después) |
| `cambiarEstado(idCita, nuevo)` | `PATCH /citas/{idCita}/estado` body `{"estado": "CONFIRMADA"}` |
| `bloquearHorario(idMedico, fecha, horaInicio, horaFin, nota)` | `POST /profesionales/{idMedico}/bloqueos` |
| `reprogramar(idCita, idFranjaNueva, ahora)` | `POST /citas/{idCita}/reprogramacion` |

**`ahora` viaja como parámetro explícito, no se consulta con `time.Now()` del lado del cliente en ningún punto sensible al tiempo** — el reloj se inyecta una sola vez arriba en el cliente para que la caducidad de una reserva sea determinista y testeable. El backend, sin embargo, **es quien decide** si `ahora` ya pasó la caducidad real (nunca confiar en el reloj del teléfono para eso).

`FranjaAgenda`:
```json
{ "idFranja": "franja_xxx", "idMedico": "doc_xxx", "fecha": "YYYY-MM-DD",
  "horaInicio": "HH:MM", "horaFin": "HH:MM" }
```
"Estrictamente libre" excluye: franjas con cita que ocupa (todo estado salvo `CANCELADA`), franjas bloqueadas por el médico, y franjas retenidas por otro paciente cuya reserva no ha caducado a la altura de `ahora`.

`ReservaFranja`:
```json
{ "idReserva": "reserva_xxx", "franja": { "...": "FranjaAgenda" }, "expiraEn": "ISO-8601 UTC" }
```
**Regla fija: la retención dura 5 minutos** (`MINUTOS_DE_RETENCION = 5` en el cliente — si el backend cambia esta política, debe ser el backend quien la exprese en `expiraEn`, el cliente no debe asumir el número).

Body de `confirmarCita`:
```json
{ "idPaciente": "pac_xxx",
  "contacto": { "nombreCompleto": "string", "telefono": "string", "correo": "string", "motivo": "string" },
  "ahora": "ISO-8601 UTC" }
```
Respuesta `ConfirmacionCita`:
```json
{ "cita": { "...": "Cita" }, "canalesNotificados": ["CORREO", "WHATSAPP"] }
```
`canalesNotificados` son los canales que el backend **efectivamente pudo notificar**, no los que se pidieron — si WhatsApp falla, el chat debe poder decir "te llegó por correo" en vez de prometer ambos.

`Cita`:
```json
{ "idCita": "cita_xxx", "folio": "CITA-4F2A", "idMedico": "doc_xxx", "nombreMedico": "string",
  "idPaciente": "pac_xxx", "fecha": "YYYY-MM-DD", "horaInicio": "HH:MM", "horaFin": "HH:MM",
  "estado": "PENDIENTE", "contacto": { "...": "DatosContactoCita o null" }, "notaBloqueo": "" }
```
`estado` ∈ `PENDIENTE | CONFIRMADA | EN_CURSO | CANCELADA | NO_ASISTIO | BLOQUEADO`.
- `contacto` es `null` **únicamente** cuando `estado == BLOQUEADO` (un bloqueo no tiene paciente).
- `CANCELADA` y `NO_ASISTIO` se pintan igual (rojo) pero son estados distintos: cancelada libera la franja, inasistencia no; el backend debe contarlas por separado para estadística de la clínica.
- Toda cita nace en `PENDIENTE` al confirmarse — el consultorio la sube a `CONFIRMADA` después.
- `reprogramar` **regresa la cita a `PENDIENTE`**, conservando `idCita`, `folio`, paciente y motivo — solo cambia franja/fecha/horario.

Errores (`FalloCita`):

| Motivo | HTTP | Cuándo |
|---|---|---|
| `RESERVA_EXPIRADA` | 410 | `ahora` ya pasó `expiraEn` al confirmar |
| `FRANJA_OCUPADA` | 409 | otro paciente ganó la carrera por la franja |
| `SIN_CONEXION` | — | generado localmente |

---

## 10. Diario de síntomas

Contrato: `DiarioRepositorio`. **Es local-first por diseño, no por falta de backend**: el paciente escribe síntomas aunque no tenga cobertura y el semáforo de triage tiene que estar listo de inmediato. Lo que viaja al backend es el **resultado ya calculado**, no un borrador para que el servidor lo re-triage.

| Método cliente | Endpoint sugerido |
|---|---|
| `entradasDe(idPaciente)` | **WebSocket/SSE o polling** — ver sección 11 (o puede resolverse solo local + sync en segundo plano) |
| `guardar(entrada)` | `POST /pacientes/{idPaciente}/diario` |
| `eliminar(idEntrada)` | `DELETE /diario/{idEntrada}` |
| `ultimaEntradaDe(idPaciente)` | `GET /pacientes/{idPaciente}/diario/ultima` (la usa la bandeja del médico para el punto de prioridad sin descargar la bitácora completa) |

`EntradaDiario`:
```json
{ "idEntrada": "entrada_xxx", "idPaciente": "pac_xxx", "instante": "ISO-8601 UTC",
  "fecha": "YYYY-MM-DD", "texto": "string", "severidad": "VERDE|AMBAR|ROJO",
  "terminosDetectados": ["string"] }
```
**Regla de negocio crítica: `severidad` se guarda calculada y NUNCA se recalcula al leer.** El diccionario de términos vigilados evolucionará con el tiempo, y si el backend (o el cliente) recalculara la severidad en cada lectura, una entrada de hace tres meses cambiaría de color sola — el médico vería moverse un historial que ya revisó. `severidad` es un snapshot inmutable en el momento de escribir, `codigo` 0/1/2 = VERDE/AMBAR/ROJO si se persiste como entero.

`terminosDetectados` existe para que el médico vea **por qué** se asignó ese color — nunca se debe ocultar aunque parezca redundante con `severidad`.

Este semáforo **no es un diagnóstico** — es un orden de lectura para el médico ("con veinte pacientes escribiendo, por cuál empezar"). El backend no debe presentarlo en ningún endpoint o documentación como un dictamen clínico.

---

## 11. Los tres streams en vivo

El cliente modela tres contratos como `Flow<T>` porque la UI tiene que reaccionar sin recargar:

| Flow | Para qué | Sugerencia de transporte |
|---|---|---|
| `ChatRepositorio.mensajesSinLeer(idConversacion): Flow<Int>` | El punto rojo de la barra inferior debe aparecer en el instante en que llega un mensaje, con el paciente mirando otra pestaña | WebSocket por conversación, o SSE con reconexión |
| `CitasRepositorio.agendaDelMedico(idMedico): Flow<List<Cita>>` | Una cita confirmada en el chat del paciente debe aparecer en la agenda del médico sin que este recargue | WebSocket por médico, emite la lista completa (o un diff) en cada alta/cambio/bloqueo |
| `DiarioRepositorio.entradasDe(idPaciente): Flow<List<EntradaDiario>>` | Nuevas entradas del diario, más reciente primero | Puede resolverse con sync local-first + push opcional; no es tan crítico en tiempo real como los otros dos |

Sugerencia concreta: un solo endpoint WebSocket por sesión (`WS /realtime?token=jwt`) que multiplexa canales (`chat:{idConversacion}`, `agenda:{idMedico}`) con un envelope tipo:
```json
{ "canal": "agenda:doc_xxx", "evento": "cita_actualizada", "payload": { "...": "Cita" } }
```
es más simple de operar que un socket por contrato.

---

## Seguridad — pendientes explícitos

- El cliente guarda el token de sesión en DataStore **sin cifrar** (`AlmacenDeSesion`). Si el backend expone datos sensibles bajo ese JWT, considerar rotación corta + refresh, ya que el almacenamiento local no es a prueba de un dispositivo comprometido.
- `PerfilEmergenciaRepositorio` es la única puerta sin sesión — cualquier campo que se le añada en el futuro debe pasar la misma revisión de "¿esto se puede leer de una tarjeta física robada?" antes de agregarse.
- La segmentación de permisos entre `PacienteRepositorio` (alta) y `HistorialMedicoRepositorio` (lectura/edición del expediente completo) descrita en DM_HistorialMedico.md sección 2 debe reflejarse en roles/scopes del JWT, no solo en la separación de interfaces del cliente.
