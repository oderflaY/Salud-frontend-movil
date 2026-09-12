# Endpoints reales del backend (para el cliente)

Referencia definitiva de lo que quedó implementado — no la sugerencia inicial
de `docs/contratos-datos-backend.md` (los paths ahí eran "sugeridos"; varios
terminaron como RPC en vez de rutas REST clásicas, según se explica en cada
módulo). El **shape de cada payload sí es el contrato firme** y coincide
exactamente con los DTOs de ese documento salvo donde se anota lo contrario.

## Base

- Todo pasa por el proxy Caddy: `http://localhost:8000` en desarrollo
  (`PROXY_PORT` en `.env`).
- `Authorization: Bearer <token>` en todo excepto el login/alta y la consulta
  de emergencia por RFID.
- Content-Type: `application/json` en todos los POST.
- Los endpoints `/rpc/...` y las vistas (`/paciente_expediente`,
  `/directorio_medico`, `/pacientes_vinculados`, `/entradas_diario`) los sirve
  **PostgREST**; `/auth/*`, `/realtime` y `/tarjetas/*` los sirve el servicio
  **Axum**. Ambos responden errores como `{"message": "MOTIVO"}` (ver
  `docs/errores.md` para la tabla completa).
- Una función RPC con **más de un parámetro** recibe un body JSON con esas
  claves exactas (snake_case, tal como se documentan abajo — no camelCase).
  Una función RPC con **un solo parámetro `jsonb`** (marcado abajo) necesita
  el header `Prefer: params=single-object`, y el body es el objeto completo
  tal cual, con sus claves en **camelCase** (el DTO, no un parámetro nombrado).

---

## 1-2. Autenticación

| Método | Endpoint | Body | Respuesta |
|---|---|---|---|
| POST | `/auth/pacientes` | `{"correo","contrasena"}` | `SesionPaciente` |
| POST | `/auth/pacientes/sesion` | `{"correo","contrasena"}` | `SesionPaciente` |
| POST | `/auth/profesionales` | `{"correo","contrasena","nombre","apellidos","tratamiento","cedulaProfesional"}` | `SesionProfesional` |
| POST | `/auth/profesionales/sesion` | `{"correo","contrasena"}` | `SesionProfesional` |

`SesionPaciente`: `{"idPaciente","token","requiereOnboarding"}`.
`SesionProfesional`: `{"idMedico","token","nombre","apellidos","tratamiento","estadoVerificacion"}`.

## 3. Expediente del paciente

| Método | Endpoint | Notas |
|---|---|---|
| GET | `/paciente_expediente?idPaciente=eq.{id}` | Devuelve un **array** de 0 o 1 elemento (PostgREST), no un objeto suelto |
| POST | `/rpc/registrar_paciente` | `Prefer: params=single-object`. Body = `PacienteDto` completo. Devuelve el `idPaciente` (string) |
| POST | `/rpc/actualizar_historial` | `Prefer: params=single-object`. Body = `PacienteDto` (incluir `idPaciente` si lo llama un médico) |

`registrar_paciente`/`actualizar_historial` reemplazan cada sub-colección
completa (tratamientos, contactos, alergias, cirugías, dispositivos RFID) con
lo que venga en el body — mandar la lista completa deseada, no un diff.

## 4. Adherencia

Todas por RPC (POST), parámetros en snake_case:

| Endpoint | Body | Respuesta |
|---|---|---|
| `/rpc/tomas_del_dia` | `{"id_paciente","fecha"}` (`fecha`: `YYYY-MM-DD`) | array `TomaDelDia` |
| `/rpc/registrar_toma` | `{"id_toma","estado","instante"}` (`estado`: `tomado\|omitido`) | 204 |
| `/rpc/adherencia_semana` | `{"id_paciente","fecha_final"}` | array de **7** `DiaDeAdherencia` |
| `/rpc/adherencia_resumen` | `{"id_paciente","fecha_final"}` | array con 1 `{"tomasProgramadas","tomasCumplidas"}` |

## 5. Emergencia por tarjeta RFID (sin sesión)

| Método | Endpoint | Respuesta |
|---|---|---|
| GET | `/tarjetas/{idTarjetaRfid}/perfil-supervivencia` | `PerfilSupervivencia` |

Sujeto a rate limit por IP (1 req/s, ráfaga de 10) — pasado eso responde 429.

## 6. Cartera de pacientes del médico

| Método | Endpoint | Respuesta |
|---|---|---|
| GET | `/pacientes_vinculados` | array `PacienteVinculado` (los del médico autenticado, no acepta parámetro) |

## 7. Directorio médico + vinculación

| Método | Endpoint | Respuesta |
|---|---|---|
| GET | `/directorio_medico` (opcional `?especialidad=eq.CARDIOLOGIA`) | array `PerfilDoctorDirectorio` |
| GET | `/directorio_medico?idMedico=eq.{id}` | array de 0 o 1 |
| POST | `/rpc/actualizar_mi_perfil_directorio` | `Prefer: params=single-object`. Body `{"especialidad"?,"universidad"?,"disponibilidad"?}` → 204. **Solo médico, sobre su propio perfil** — no está en los 11 contratos originales, hace falta para que el directorio tenga algo que mostrar |
| POST | `/rpc/medico_vinculado` | Body `{"id_paciente"}` → objeto `MedicoVinculado` (campos null si no tiene ninguno) |
| POST | `/rpc/medicos_vinculados` | Body `{"id_paciente"}` → array `MedicoVinculado` |
| POST | `/rpc/solicitar_vinculacion` | Body `{"id_medico"}` → `MedicoVinculado` (idempotente, se puede repetir) |

## 8. Chat de orientación

| Endpoint | Body | Respuesta |
|---|---|---|
| `/rpc/obtener_historial` | `{"id_conversacion"}` | array `MensajeChat` |
| `/rpc/enviar_mensaje` | `{"id_conversacion","texto_mensaje","instante_enviado","autor_remitente"}` (`autor_remitente`: `PACIENTE\|MEDICO`) | array con 1 `MensajeChat` |
| `/rpc/marcar_conversacion_leida` | `{"id_conversacion"}` | 204 |
| `/rpc/obtener_respuesta_automatica` | `{"id_conversacion","instante_recibido"}` | array con 1 `MensajeChat` (idempotente: la 2ª llamada devuelve el mismo mensaje) |

Nota: los nombres de parámetro no son `texto`/`instante`/`autor` sino
`texto_mensaje`/`instante_enviado`/`autor_remitente` — Postgres no permite un
parámetro de entrada con el mismo nombre que una columna de la respuesta (ver
`docs/base-de-datos-convenciones.md` punto 10). El **campo de la respuesta**
sí es `texto`/`instante`/`autor` tal como dice el contrato.

## 9. Citas y agenda

| Endpoint | Body | Respuesta | Quién |
|---|---|---|---|
| `/rpc/generar_franjas` | `{"fecha_inicio","fecha_fin","hora_inicio","hora_fin","duracion_minutos"}` | integer (cuántas creó) | médico |
| `/rpc/franjas_libres` | `{"id_medico","desde","ahora"}` | array `FranjaAgenda` | paciente/médico |
| `/rpc/reservar_temporalmente` | `{"id_franja","ahora"}` | `ReservaFranja` `{"idReserva","franja":{...},"expiraEn"}` | paciente |
| `/rpc/liberar_reserva` | `{"id_reserva"}` | 204 | paciente |
| `/rpc/confirmar_cita` | `{"id_reserva","id_paciente","contacto":{"nombreCompleto","telefono","correo","motivo"},"ahora"}` | `ConfirmacionCita` `{"cita":{...},"canalesNotificados":[]}` | paciente |
| `/rpc/cargar_agenda` | `{"id_medico"}` | array `Cita` | médico |
| `/rpc/cambiar_estado` | `{"id_cita","nuevo_estado"}` | `Cita` | médico |
| `/rpc/bloquear_horario` | `{"id_medico","fecha","hora_inicio","hora_fin","nota"}` | array `Cita` (una por franja bloqueada) | médico |
| `/rpc/reprogramar` | `{"id_cita","id_franja_nueva","ahora"}` | `Cita` (vuelve a `PENDIENTE`) | paciente o médico |

`canalesNotificados` siempre viene `[]`: no hay integración de correo/WhatsApp
implementada todavía (se reporta honestamente, no se simula éxito).

## 10. Diario de síntomas

CRUD directo sobre una vista — sin RPC.

| Método | Endpoint | Notas |
|---|---|---|
| POST | `/entradas_diario` | Body = `EntradaDiario` sin `idEntrada`. Agregar header `Prefer: return=representation` para recibir el objeto creado (si no, PostgREST responde 201 sin cuerpo) |
| GET | `/entradas_diario?idPaciente=eq.{id}` | Lista completa |
| GET | `/entradas_diario?idPaciente=eq.{id}&order=instante.desc&limit=1` | Equivalente a `ultimaEntradaDe` |
| DELETE | `/entradas_diario?idEntrada=eq.{id}` | 204 |

Nota: `instante` en la respuesta viene como `...+00:00` (no `...Z`) — es el
único módulo donde no se normalizó, para no perder la actualización
automática de la vista. Ambas formas son ISO-8601 UTC válidas.

## 11. Tiempo real (WebSocket)

```
WS /realtime?token={jwt}
```

Cliente → servidor (mensajes de texto JSON):
```json
{"suscribir": "chat:conv_xxx"}
{"desuscribir": "chat:conv_xxx"}
```
Canales válidos: `chat:{idConversacion}`, `agenda:{idMedico}`, `diario:{idPaciente}`.
El servidor valida contra la base que el JWT tenga derecho a ese canal antes
de aceptarlo.

Servidor → cliente:
```json
{"tipo":"suscrito","canal":"chat:conv_xxx"}
{"tipo":"error","mensaje":"NO_AUTORIZADO"}
{"canal":"chat:conv_xxx","evento":"mensaje_nuevo","payload":{"idMensaje":"...","autor":"PACIENTE","texto":"..."}}
```
Eventos disponibles: `mensaje_nuevo` (chat), `cita_creada`/`cita_actualizada`/`agenda_bloqueada` (agenda) — el diario aún no emite eventos propios (módulo 10 no generó ninguno; agregarlo implica una migración nueva con `app.emitir_evento` en el INSERT).

## IA (DeepSeek): resumen de chat y traducción

No es uno de los 11 módulos del documento de contratos — capacidad nueva añadida
sobre el chat existente. Vive en Axum (`backend/src/ia/`), no en PostgREST,
porque necesita la API key de DeepSeek. Requiere `Authorization: Bearer <token>`
igual que el resto; si `DEEPSEEK_API_KEY` no está configurada en el backend,
responde `{"message":"IA_NO_CONFIGURADA"}` (503) sin afectar ningún otro
endpoint.

| Método | Endpoint | Body | Respuesta | Quién |
|---|---|---|---|---|
| POST | `/chat/{idConversacion}/resumen` | (sin body) | `{"resumen": string}` | paciente o médico, solo si participan en esa conversación |
| POST | `/chat/traducir` | `{"texto","idiomaDestino"}` | `{"traduccion": string}` | cualquier usuario autenticado, texto arbitrario |

Nota: `/chat/{id}/resumen` da 401 si el llamador no es participante de la
conversación (misma verificación en Rust que usa `realtime::ws` para los
canales — esta conexión de Axum a Postgres no tiene el GUC
`request.jwt.claims` que sí usan las funciones RPC). Un `IA_NO_DISPONIBLE`
(502) significa que la llamada a DeepSeek falló (red, cuota agotada, etc.) —
se reintenta del lado del cliente si tiene sentido, no es un error del
paciente/médico.
