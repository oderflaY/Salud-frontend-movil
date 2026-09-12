# Mapeo motivo → HTTP

Fuente única de verdad para `app.lanzar_error(status, motivo)` (funciones RPC en
Postgres) y para `AppError` en `backend/src/error.rs` (Axum). Todo error de negocio
responde `{"message": "MOTIVO"}`; el cliente nunca parsea prosa. `SIN_CONEXION` no
aparece abajo porque el cliente lo genera localmente — el backend nunca lo emite.

| Módulo | Motivo | HTTP | Cuándo |
|---|---|---|---|
| Auth paciente | `CREDENCIALES_INVALIDAS` | 401 | correo/contraseña no coinciden |
| Auth paciente | `CUENTA_BLOQUEADA` | 403 | cuenta suspendida |
| Auth paciente | `CORREO_YA_REGISTRADO` | 409 | alta con correo existente |
| Auth profesional | `CREDENCIALES_INVALIDAS` | 401 | correo/contraseña no coinciden |
| Auth profesional | `CUENTA_BLOQUEADA` | 403 | cuenta suspendida |
| Auth profesional | `CORREO_YA_REGISTRADO` | 409 | alta con correo existente |
| Auth profesional | `CEDULA_YA_REGISTRADA` | 409 | cédula profesional duplicada |
| Emergencia RFID | `TARJETA_DESCONOCIDA` | 404 | tarjeta no existe |
| Emergencia RFID | `TARJETA_REVOCADA` | 403 | baja lógica: reportada extraviada |
| Adherencia | `TOMA_DESCONOCIDA` | 404 | `idToma` no existe o RLS no lo deja ver (no se distingue, ver módulo 4) |
| Chat | `CONVERSACION_DESCONOCIDA` | 404 | `marcarConversacionLeida` sobre una conversación que no es tuya o no existe |
| Citas | `RESERVA_EXPIRADA` | 410 | `ahora` ya pasó `expiraEn` al confirmar |
| Citas | `FRANJA_OCUPADA` | 409 | franja ocupada, bloqueada, o inexistente (se unifican a propósito, ver módulo 9) |
| Transversal | `NO_AUTORIZADO` | 401 | acción sobre un recurso ajeno (ej. médico con solo "lectura" intentando editar) |
| Transversal | `SOLICITUD_INVALIDA` | 400 | valor fuera de un dominio cerrado que el cliente conocido nunca manda (defensa de borde) |
| IA (resumen/traducción) | `IA_NO_CONFIGURADA` | 503 | `DEEPSEEK_API_KEY` no está puesta — el módulo existe pero no está activado |
| IA (resumen/traducción) | `IA_NO_DISPONIBLE` | 502 | la llamada a DeepSeek falló (red, cuota, respuesta irreconocible) — el detalle se registra con `tracing`, nunca se expone al cliente |

Convención SQL (Postgres → PostgREST): `RAISE EXCEPTION USING ERRCODE = 'PT' || status,
MESSAGE = motivo` — **texto plano, no JSON-envuelto**. PostgREST traduce un SQLSTATE
`PTxyz` directo al status HTTP `xyz` y responde
`{"code": "PTxyz", "message": "MOTIVO", "details": null, "hint": null}`. Se probó en vivo
(módulo 9) que PostgREST **no** parsea un `MESSAGE` con forma de JSON — lo deja como
string literal — así que envolver el motivo en `{"motivo": "..."}` ahí adentro solo
producía `"message": "{\"motivo\":\"X\"}"` (un string con JSON dentro, no un objeto).
Toda función RPC usa `app.lanzar_error(status, motivo)` en vez de repetir el `RAISE` a
mano.

Convención Rust (Axum): `AppError` es un enum con una variante por motivo; implementa
`IntoResponse` devolviendo `{"message": "MOTIVO"}` con el status de esta tabla — mismo
nombre de campo (`message`) que el `message` que PostgREST ya pone en sus propios errores
de RPC, así el cliente siempre lee `body.message` sin importar qué servicio respondió,
ignorando `code`/`details`/`hint` cuando vienen nulos (solo los pone PostgREST).
