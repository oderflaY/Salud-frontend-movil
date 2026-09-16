# Documento Maestro (DM) - Conexión del Panel Web (Salud+) con el Backend

## 1. Visión General
El **Panel Web Salud+** (React + Vite) es la ventana del médico al mismo sistema que usa la app móvil: mismo backend, misma base de datos, mismas cuentas. Lo que el paciente registra en la app (tomas, diario, métricas, mensajes) aparece en el panel. Lo que el médico hace en el panel (responder un mensaje, reiniciar una cuenta) le llega a la app.

El backend implementa **exactamente** el "Contrato de API — Salud+ Panel Web": las 12 rutas, con las formas, los nombres y los formatos de fecha que el frontend ya espera. Si el panel apunta al backend actualizado, **no hay que tocar el frontend para que conecte**.

Verificado contra datos reales de la demo: **100 comprobaciones** automáticas, incluida la de que un mensaje enviado desde el panel le llega **en vivo** a la app del paciente por el WebSocket.

**Quién entra:**
* El **médico**, con su **misma cuenta de la app** (p. ej. `dr.silva@salud.local` / `Demo1234`). Solo ve a los pacientes con **vínculo vigente** con él.
* El **administrador** (p. ej. `admin@salud.local` / `Demo1234`, migración 0018). Gestiona cuentas: alta de médicos, cédulas, suspensiones, contraseñas temporales y solicitudes de recuperación. **Nunca ve expedientes clínicos** (403). Se crea desde el servidor con `backend crear-admin`; no hay ruta HTTP para crearlo.
* `admin@salud.com` / `Admin1234` del ejemplo del contrato no existe.

**Recuperar contraseña:** el sistema no envía correos. `POST /auth/recuperar-contrasena` deja la solicitud en la bandeja del administrador, que da una contraseña temporal. Con ella, el panel obliga a elegir una nueva (`POST /auth/cambiar-contrasena`) antes de mostrar nada más. Las rutas y cuerpos están en `docs/CONTRATO-API.md` (sección 7) del repo del panel.

## 2. Puesta en marcha

| Paso | Comando o valor |
|---|---|
| 1. Actualizar el backend (una vez) | `cd ~/Documents/GitHub/Salud-frontend-movil && sudo docker compose up migrate && sudo docker compose up -d --build && sudo bash scripts/seed_demo.sh` |
| 2. Comprobar | `curl http://localhost:8081/healthz` debe responder `ok` |
| 3. Panel | `npm run dev` en Salud+ (Vite en `:5173`). `VITE_API_URL` por defecto ya es `http://localhost:8081/api/v1`. |
| 4. Entrar | `dr.silva@salud.local` / `Demo1234` |
| 5. Verificar todo | `bash scripts/verificar_demo.sh` (sección J = panel) |

Otro origen (el panel abierto desde otra PC, o en producción): agregarlo en `CORS_ORIGENES` del `.env` **del backend** y reiniciar con `sudo docker compose up -d backend`. En producción va el dominio del panel, nunca `*`.

## 3. Normativas de Datos y Tipado (cómo quedó implementado)

* **Nombres:** módulo admin en `snake_case`; chat en `camelCase`, tal cual el contrato.
* **Fechas del módulo admin:** hora local de la clínica (`America/Mexico_City`) **sin zona**: `"2026-09-08T07:30:00"`. Fechas sin hora como `"2026-09-08"`.
* **Fechas del chat:** UTC con milisegundos: `"2026-09-15T10:25:00.000Z"`.
* **Nulos:** `null` explícito; nunca se omite una clave. La única excepción es `adjunto`, que el contrato pide omitir cuando no hay.
* **Cabeceras:** CORS permite `Content-Type`, `Authorization` y `X-Request-ID` con `GET, POST, PATCH, OPTIONS`. El `X-Request-ID` queda en el log del backend y **vuelve en la respuesta**.
* **JWT:** trae `exp` en segundos y dura 12 horas.
* **Errores:** `{"message": "frase para mostrar", "codigo": "MOTIVO"}`. `message` es legible (el panel lo muestra tal cual); `codigo` es extra y no estorba.
* **Nunca 404 en rutas que existen.** Un recurso ajeno o inexistente responde **403**, porque el panel trataría el 404 como "no implementado" y enseñaría mocks (en un reinicio, una contraseña falsa).
* **Privacidad:** solo pacientes con vínculo vigente. El `rfid_uid` sale enmascarado (`RFID:••••:10A8`) porque el UID abre el perfil de emergencia sin sesión.

## 4. Rutas

| Método | Ruta | Estado |
|---|---|---|
| `POST` | `/auth/login` | Implementada. `rol` = `doctor`. `nombre_completo` sin título. |
| `GET` | `/auth/me` | Implementada. `rol` = `medico`; `especialidad` legible ("Cardiología"). |
| `GET` | `/admin/dashboard/kpis` | Implementada. Los 8 campos, enteros. |
| `GET` | `/admin/pacientes` | Implementada. Las 28 claves, con `historial` y `telemetria_reciente` completos (ver 5.A). |
| `GET` | `/admin/alertas` | Implementada. |
| `PATCH` | `/alerts/{id}` | Implementada. También responde en `/admin/alertas/{id}`, por si la cambian. |
| `GET` | `/admin/auditoria` | Implementada. |
| `PATCH` | `/admin/usuarios/{id}/reset-cuenta` | Implementada, con la doble confirmación **revalidada en el servidor**. |
| `GET` | `/admin/conversaciones` | Implementada. |
| `GET` | `/admin/conversaciones/{id}/mensajes` | Implementada. Últimos 500 mensajes, en orden. |
| `POST` | `/admin/conversaciones/{id}/mensajes` | Implementada. **Le llega a la app del paciente en vivo.** |
| `PATCH` | `/admin/conversaciones/{id}/leer` | Implementada. |
| `GET` | `/adjuntos/{id}?firma=...` | Nueva: es la URL de `urlPreview`. Funciona en un `<img>` sin token; vale 1 hora y solo para ese archivo. |

### 4.1 Reinicio de cuenta
1. `codigo_confirmacion` debe ser `REINICIO`; si no, **422** "Escribe REINICIO para confirmar...".
2. `motivo` es obligatorio (máximo 500 caracteres) porque queda en la auditoría (NOM-004); si falta, **422**.
3. `password_operador` se verifica contra la contraseña del médico en sesión. Si no coincide, responde **422** y no 401, porque un 401 haría que el panel cerrara la sesión del médico.
4. Requisitos: cédula `APROBADO` y vínculo vigente con el paciente; si no, **403**.
5. Efecto: una `password_temporal` (forma `k7m4p-9qx2h`, vence en 72 h) que se muestra **una sola vez**. Se cierran todas las sesiones del paciente, y la app le **obliga a elegir una contraseña nueva** al entrar. El expediente no se toca. Queda en `/admin/auditoria`.
6. Mientras el paciente no elige su contraseña nueva, el panel lo muestra con `estado_cuenta` = `reseteada`.

## 5. De dónde sale cada dato

### 5.A Campos del contrato sin dato en el sistema
La app móvil no captura estos datos. Salen en `null` o `[]`, **nunca inventados**:

| Campo | Qué se manda |
|---|---|
| `telemetria_reciente`: FC, variabilidad FC, SpO2, glucosa, **todo el sueño**, **toda la sintomatología geriátrica** | `null` (la forma del objeto se manda completa) |
| `historial.esquema_vacunacion` | `[]` |
| `antecedentes_patologicos[].cie10`, `anio_diagnostico`, `en_control`, `notas` | `null` |
| `alergias_y_reacciones[].confirmada_por_prueba` | `null` |
| `medicacion[].principio_activo`, `indicaciones_especiales` | `null` |
| `contactos_emergencia[].autorizado_para_reinicio_cuenta` | `false` |
| `rfid_estado` = `pendiente` | Nunca sale: el sistema solo sabe si la tarjeta está `vinculada` o no (`sin_rfid`). |

### 5.B Campos derivados (decisiones que el frontend debe conocer)

| Campo | Cómo se calcula |
|---|---|
| `nivel_riesgo` (paciente) | El mismo riesgo de la app, según las tomas de 7 días: ALTO → `Rojo`, MEDIO → `Amarillo`, BAJO → `Verde`. |
| `adherencia_porcentaje` | Tomas cumplidas en 7 días. **`null` si no tiene tomas que medir** (no es 0 %). |
| `adherencia_global` (KPI) | Lo mismo, sumado sobre todos sus pacientes; 0 si ninguno tiene tomas (el contrato pide entero). |
| `alertas_pendientes` (KPI) | Solo las `pendiente`; las que están `en_revision` no cuentan. |
| `diagnostico_principal` | Las 2 primeras condiciones críticas del perfil de emergencia ("Hipertensión + Diabetes tipo 2"). |
| `antecedentes_patologicos` | Las condiciones críticas, una por renglón. |
| `medico_tratante` | Quien recetó el tratamiento vigente más reciente; si nadie, el médico en sesión. |
| `ultima_telemetria` | Lo último que el paciente registró: métricas, diario o una toma. |
| `telemetria_reciente` | Solo peso y presión (de las métricas de la app); `null` si nunca las capturó. |
| `estado_toma_hoy` | Según los horarios de hoy (hora local) y las tomas registradas: `retrasada` si ya pasó un horario (más el margen de gracia) sin toma. |
| `metodo_verificacion` | Siempre `manual`: el paciente confirma la toma en la app. No hay verificación por foto. |
| `es_cuidador_principal` | El contacto de emergencia de mayor prioridad. |
| `estado_cuenta` | `suspendida` = cuenta bloqueada; `reseteada` = tiene una contraseña temporal sin cambiar. |
| Alertas | Se generan solas a partir del diario (ROJO/ÁMBAR), del riesgo de abandono y del medicamento por terminarse. `nivel_riesgo` de la alerta = su prioridad. `resuelto` se guarda como atendida. |
| `sugerencia_ia_clinica` | **Reglas fijas por tipo de alerta, no IA.** El backend tiene DeepSeek configurado; conectarlo aquí es posible, pero no está hecho. |
| Chat `estado` | `pendiente` si el último mensaje es del paciente (espera respuesta); si no, `activa`. `cerrada` no se usa. |
| Chat `medicoEscribiendo` | Siempre `false`: el sistema no tiene indicador de "escribiendo". |

## 6. Lo que le falta al frontend

### 6.A Obligatorio antes de usarlo con pacientes reales
1. **Quitar el respaldo a mocks en producción.** Hoy, ante un 404, un timeout o una red caída, el panel enseña datos falsos sin avisar. Ejemplos: una lista de pacientes inventada, una alerta "atendida" que no se guardó, un mensaje "enviado" que no llegó o una **contraseña temporal falsa**. Los mocks deberían encenderse solo con una bandera de desarrollo (p. ej. `VITE_USAR_MOCKS=true`), y fuera de ella el panel debería mostrar "Sin conexión con el servidor".
2. **Manejar los `null` de la sección 5.A.** Por ejemplo: `adherencia_porcentaje` null → "Sin datos"; valores de telemetría null → "—"; booleanos null (`en_control`, `confirmada_por_prueba`, `episodios_*`) → "No registrado". Si el código hace `valor.toFixed()` o `` `${valor}%` `` sin revisar, se rompe o muestra "null%".
3. **Pantalla de login con el médico**, no con `admin@salud.com`: esa cuenta no existe.
4. **Timeout de `/admin/pacientes`.** Esta ruta devuelve el historial completo de cada paciente. Con muchos pacientes pasará de 5000 ms y, por el punto 1, el panel caerá en silencio a los mocks. Hay que cambiar a una lista ligera más `GET /admin/pacientes/{id}` (el contrato ya lo anticipa); el backend lo puede agregar en cuanto el frontend lo pida.

### 6.B Para que el panel se comunique de verdad con la app móvil
5. **Chat en tiempo real.** El panel solo ve mensajes nuevos si recarga. El backend ya tiene el WebSocket que usa la app: `ws://localhost:8081/realtime?token=<jwt>`. Hay que enviar `{"suscribir":"chat:<idConversacion>"}`; al llegar `{"canal":"chat:<id>","evento":"mensaje_nuevo",...}`, se recargan los mensajes.
6. **Enviar adjuntos desde el panel.** El contrato solo manda texto. El backend ya recibe archivos en `POST /chat/{id}/adjuntos` (multipart `tipo` + `archivo`), igual que la app.
7. **`urlPreview` vence en 1 hora.** Si una imagen falla al cargar, se recargan los mensajes de esa conversación para obtener enlaces nuevos.

### 6.C Funciones que el sistema ya tiene y el panel no muestra
8. **Agenda y citas:** franjas, citas, propuestas del médico y reprogramación ya existen en el backend. El panel solo muestra `citas_hoy` y `proxima_cita`.
9. **Diario de síntomas completo:** hoy solo aparece como alertas.
10. ~~Rol de administrador~~: hecho (0018). Queda pendiente dar de alta administradores desde la web; hoy solo se crean en el servidor.

### 6.D Ajustes menores
11. `X-Request-ID` vuelve en la respuesta (y CORS lo expone): conviene registrarlo junto a cada error para buscarlo en el log del backend.
12. Tras **12 horas** el token vence y el panel cierra la sesión (ya lo decodifica); no hay token de renovación.

## 7. Errores que devuelve

| HTTP | Cuándo | Qué hace el panel hoy |
|---|---|---|
| 401 | Sin token, token vencido o alterado, sesión cerrada o cuenta dada de baja; login con credenciales malas | Cierra la sesión (o muestra "Credenciales incorrectas" en el login) |
| 403 | Token de paciente; paciente, alerta o conversación que no es del médico; cédula no aprobada; enlace de adjunto vencido | "No tienes permisos para realizar esta acción" |
| 400 | `status` fuera del enum; `id_paciente` del cuerpo distinto al de la ruta | Muestra `message` |
| 422 | Reinicio sin `REINICIO`, sin motivo o con la contraseña del médico mal; mensaje vacío o de más de 4000 caracteres | Muestra `message` |
| 500 | Falla interna (queda en el log con su `X-Request-ID`) | Muestra `message` |

## 8. Dónde está en el backend

* Rutas: `backend/src/dashboard/` (`mod.rs`, `handlers.rs`, `error.rs`, `enlace_adjunto.rs`).
* JSON clínico: `db/migrations/0017_panel_web.sql` (funciones `app.panel_*`). Las sesiones revocables y las alertas vienen de `0016_dashboard_medico.sql`.
* Cuentas: `db/migrations/0018_cuentas_panel.sql` (administradores, `auditoria_cuentas`, `solicitudes_recuperacion`), `backend/src/dashboard/cuentas.rs`, `sesion_panel.rs` y `backend/src/cli.rs` (`crear-admin`).
* Pruebas: 38 unitarias en `cargo test`; sección J de `scripts/verificar_demo.sh` contra el stack real.
