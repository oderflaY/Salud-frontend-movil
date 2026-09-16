# Documento Maestro (DM) - Pendientes del Frontend del Panel Web (Salud+)

## 1. Visión General
El backend ya implementa las 12 rutas del "Contrato de API — Salud+ Panel Web" con las formas exactas que el frontend espera. Con eso el panel **conecta sin cambios**, pero no queda listo para usarse con pacientes reales.

Este documento es para quien mantiene el frontend y reúne tres cosas:

1. **Qué va a recibir** del backend que difiere de los ejemplos del contrato (valores `null`, valores fijos, errores).
2. **Qué tiene que cambiar** para no mostrar datos falsos ni romperse con datos reales.
3. **Qué funciones le faltan** para que el panel se comunique de verdad con la app móvil.

Cada punto indica **quién lo hace** (Frontend, Backend, Ambos o App móvil) y su **prioridad**:

| Prioridad | Significado |
|---|---|
| **P0** | Obligatorio antes de usarlo con pacientes reales |
| **P1** | Necesario para que el panel sea útil en consulta |
| **P2** | Mejora |

El contrato completo tal como quedó implementado está en `docs/DM_Panel_Web.md`.

## 2. Conexión

| Concepto | Valor |
|---|---|
| Base URL | `VITE_API_URL=http://localhost:8081/api/v1`, el valor que el panel ya trae |
| Cuenta para entrar | La del **médico** en la app, p. ej. `dr.silva@salud.local` / `Demo1234`. **`admin@salud.com` no existe.** |
| Pacientes visibles | Solo los que tienen vínculo vigente con ese médico |
| Orígenes permitidos (CORS) | `http://localhost:5173` y `:4173`. Otro origen se agrega en `CORS_ORIGENES` del backend. |
| Token | JWT de 12 horas, con `exp`. No hay token de renovación. |

Cuentas de la demo (todas con `Demo1234`):

| Médico | Pacientes vinculados |
|---|---|
| `dr.silva@salud.local` | paciente1, paciente2 |
| `dra.lopez@salud.local` | paciente1, paciente2 |
| `dr.garcia@salud.local` | paciente1, paciente3 |
| `dra.torres@salud.local` | paciente3, paciente4 |

## 3. Lo que va a recibir (diferencias con los ejemplos del contrato)
Las claves y los tipos son los del contrato. Lo que cambia son los **valores**: la app móvil no captura varios de los datos que el panel pide, y esos campos llegan en `null`, vacíos o con un valor fijo. **Nunca se inventan.**

### 3.1 Autenticación

| Campo | Qué llega |
|---|---|
| `POST /auth/login` → `usuario.rol` | Siempre `doctor`. No existe `administrator`. |
| `usuario.nombre_completo` | Sin título: `"Carlos Silva Rodríguez"` |
| `GET /auth/me` → `rol` | Siempre `medico` |
| `especialidad` | Nombre legible (`"Cardiología"`), o `null` si el médico no la capturó |

### 3.2 KPIs
Los 8 campos llegan siempre como enteros.

| Campo | Qué llega |
|---|---|
| `adherencia_global` | `0` cuando ningún paciente tiene tomas que medir. Ese 0 significa "sin datos", no "nadie tomó nada". |
| `alertas_pendientes` | Solo las alertas en `pendiente`. Las que están `en_revision` **no** cuentan. |

### 3.3 Pacientes (`GET /admin/pacientes`)

| Campo | Qué llega |
|---|---|
| `adherencia_porcentaje` | **Puede ser `null`**: el paciente no tiene tomas que medir en 7 días |
| `diagnostico_principal` | Las 2 primeras condiciones críticas del perfil ("Hipertensión + Diabetes tipo 2"); **`null`** si no tiene |
| `sexo_biologico` | `"Masculino"`, `"Femenino"`, o `null` |
| `edad`, `fecha_nacimiento`, `telefono`, `tipo_sangre`, `curp`, `nss`, `aseguradora`, `peso_kg`, `altura_cm`, `imc` | **Pueden ser `null`** si el paciente no los capturó |
| `ultima_telemetria` | Lo último que el paciente registró en la app (métricas, diario o una toma); **`null`** si nunca registró nada |
| `proxima_cita` | **`null`** si no tiene. La clave siempre viene, nunca se omite. |
| `rfid_estado` | `vinculada` o `sin_rfid`. **Nunca `pendiente`**: el sistema no tiene ese estado. |
| `rfid_uid` | **Enmascarado**: `"RFID:••••:10A8"`. El UID completo abre el perfil de emergencia sin sesión, por eso no se envía. `null` si no hay tarjeta. |
| `estado_cuenta` | `suspendida` = cuenta bloqueada; `reseteada` = el paciente tiene una contraseña temporal sin cambiar |
| `medico_tratante` | Quien recetó el tratamiento vigente más reciente; si nadie, el médico en sesión |
| `contactos_emergencia[].autorizado_para_reinicio_cuenta` | Siempre `false`: el sistema no guarda esa autorización |

`historial`:

| Campo | Qué llega |
|---|---|
| `antecedentes_patologicos` | Las condiciones críticas, una por renglón, con `cie10`, `anio_diagnostico`, `en_control` y `notas` en **`null`** |
| `alergias_y_reacciones[].confirmada_por_prueba` | **`null`** |
| `alergias_y_reacciones[].tipo_reaccion` | El mismo texto que `reaccion` |
| `esquema_vacunacion` | **`[]`**: el sistema no registra vacunas |
| `medicacion_activa_y_verificacion[].principio_activo`, `indicaciones_especiales` | **`null`** |
| `medicacion_activa_y_verificacion[].metodo_verificacion` | Siempre **`"manual"`** (el paciente confirma la toma en la app). Es un valor que no está en los ejemplos del contrato. |
| `estadisticas_adherencia_mensual`, `ultima_toma_confirmada` | **Pueden ser `null`** |

`telemetria_reciente`:
* Es **`null`** completo si el paciente nunca capturó métricas.
* Si capturó, el objeto llega con la forma completa, pero **solo tres valores tienen dato**: `presion_sistolica_matutina`, `presion_diastolica_matutina` y `peso_diario_kg`.
* **Todo lo demás llega en `null`**: frecuencia cardiaca, variabilidad, SpO2, glucosa, todo el sueño y toda la sintomatología geriátrica. Eso incluye los booleanos `episodios_confusion`, `episodios_incontinencia` y `mareo_ortostatico`.

### 3.4 Alertas

| Campo | Qué llega |
|---|---|
| `nivel_riesgo` | La prioridad de la alerta: alta → `Rojo`, media → `Amarillo` |
| `vectores_de_prediccion` | 2 textos: título y detalle (p. ej. `["Se está terminando Atorvastatina", "Le quedan 8 dosis. Le alcanza para unos 8 días."]`) |
| `sugerencia_ia_clinica` | **Reglas fijas por tipo de alerta, no IA.** Conviene no rotularlo como "IA" en pantalla, o cambiarlo a "Sugerencia". |
| `edad`, `diagnostico_principal` | Pueden ser `null` |
| `PATCH /alerts/{id}` `status` = `resuelto` | Se guarda como atendida. La alerta puede volver a salir la semana siguiente si el problema sigue. |

### 3.5 Chat

| Campo | Qué llega |
|---|---|
| `ultimoMensaje` | **`null`** si la conversación no tiene mensajes; `"Adjunto: receta.png"` si el último mensaje fue solo un archivo |
| `ultimoMensajeTimestamp` | Siempre viene; sin mensajes, es la fecha en que se creó la conversación |
| `estado` | `pendiente` si el último mensaje es del paciente; si no, `activa`. **Nunca `cerrada`.** |
| `medicoEscribiendo` | Siempre `false`: no hay indicador de "escribiendo" |
| `diagnosticoPrincipal` | Puede ser `null` |
| `adjunto.urlPreview` | URL absoluta que funciona en un `<img>` sin token. **Vence en 1 hora.** |
| `adjunto.tipo` | `foto`, `archivo` o `escaneo` |

### 3.6 Errores

| Qué | Detalle |
|---|---|
| Forma | `{"message": "frase para mostrar", "codigo": "MOTIVO"}`. `message` ya es legible; `codigo` es extra y sirve para decidir en código sin comparar textos. |
| **403 en vez de 404** | Un paciente, alerta o conversación que no es del médico responde 403. Ninguna ruta que existe responde 404. |
| **422** | Reinicio sin `REINICIO`, sin motivo o con la contraseña del médico mal; mensaje vacío o de más de 4000 caracteres |
| **401** | Solo para una sesión inválida o vencida, o credenciales malas en el login |
| `X-Request-ID` | Vuelve en la respuesta (CORS lo expone) y queda en el log del backend |

### 3.7 Campos que deben aceptar `null` en `src/types/*.ts`
Si alguno de estos está tipado como `number`, `string` o `boolean` a secas, cambiarlo a `| null`:

```ts
// PacienteAdmin
edad, fecha_nacimiento, sexo_biologico, diagnostico_principal, adherencia_porcentaje,
ultima_telemetria, rfid_uid, proxima_cita, tipo_sangre, curp, nss, aseguradora,
telefono, peso_kg, altura_cm, imc, telemetria_reciente

// historial
antecedentes_patologicos[]: cie10, anio_diagnostico, en_control, notas
alergias_y_reacciones[]: confirmada_por_prueba, reaccion, tipo_reaccion, severidad
antecedentes_quirurgicos[]: anio, complicaciones
medicacion_activa_y_verificacion[]: principio_activo, indicaciones_especiales, dosis,
  via_administracion, frecuencia_horas, estadisticas_adherencia_mensual, ultima_toma_confirmada

// telemetria_reciente: todos los valores de sus objetos internos, incluidos los booleanos

// AlertaAdmin
edad, diagnostico_principal

// ConversacionResumen
diagnosticoPrincipal, ultimoMensaje

// AdminSesion
especialidad
```

## 4. Lo que tiene que cambiar

### 4.1 [P0 · Frontend] Quitar el respaldo a mocks fuera de desarrollo
Hoy, ante un 404, un 501, un timeout o una red caída, el panel enseña datos de ejemplo **sin avisar**. Con pacientes reales eso significa:
* mostrar una lista de pacientes inventada;
* marcar como atendida una alerta que no se guardó;
* dar por enviado un mensaje que nunca le llegó al paciente;
* en un reinicio, mostrar una **contraseña temporal falsa** que el médico le entregaría al paciente.

Propuesta:
```ts
// Solo en `npm run dev` y solo si se pide explícitamente.
export const USAR_MOCKS = import.meta.env.DEV && import.meta.env.VITE_USAR_MOCKS === 'true';

// En el cliente HTTP, donde hoy cae al mock:
if (!USAR_MOCKS) throw new ErrorDeConexion('No hay conexión con el servidor.');
return mock();
```
Cuando no hay conexión, mostrar un aviso visible ("Sin conexión con el servidor. Los datos pueden no estar actualizados.") en vez de datos de ejemplo. **Nunca** usar un mock en el reinicio de cuenta ni al enviar un mensaje.

### 4.2 [P0 · Frontend] Mostrar bien los `null` de la sección 3
Si el código hace `valor.toFixed(1)`, `` `${valor}%` `` o `valor ? 'Sí' : 'No'` sin revisar, con datos reales se rompe o muestra "null%". Un booleano `null` no es "No": es "No registrado".

```ts
export const mostrar = (v: number | string | null | undefined, sufijo = '') =>
  v === null || v === undefined || v === '' ? '—' : `${v}${sufijo}`;

export const siNo = (v: boolean | null | undefined) =>
  v === null || v === undefined ? 'No registrado' : v ? 'Sí' : 'No';

// adherencia_porcentaje: null  -> "Sin tomas que medir" (no 0 %, no rojo)
// telemetria_reciente: null    -> ocultar la tarjeta o "Sin registros"
// esquema_vacunacion: []       -> "Sin vacunas registradas"
```

### 4.3 [P0 · Frontend] Login del médico
* Quitar `admin@salud.com` / `Admin1234` de placeholders, textos y documentación: esa cuenta no existe.
* El login aceptará `usuario.rol = "doctor"` y `/auth/me` devolverá `rol = "medico"`. Ambos ya están en las uniones del contrato; revisar que ninguna pantalla o ruta exija `administrator` o `admin` para mostrarse.

### 4.4 [P0 · Frontend] Reinicio de cuenta
* Ante **422**, mostrar `message` junto al campo: "Escribe REINICIO para confirmar…", "Tu contraseña no es correcta. El reinicio no se hizo.", "Escribe el motivo…". El modal **no** debe cerrarse.
* Ante **403**, el paciente no está vinculado con el médico, o el médico no tiene la cédula aprobada. Mostrar `message`, que es más claro que el texto genérico de permisos.
* `password_temporal` se muestra **una sola vez**. No guardarla en el estado global, en `localStorage` ni en logs. Al cerrar el modal se pierde; si hace falta otra, se reinicia de nuevo.
* Mostrar que el paciente queda `reseteada` hasta que elija su contraseña en la app, y que la temporal vence en 72 horas.

### 4.5 [P0 · Ambos] `/admin/pacientes` excederá los 5000 ms
La ruta devuelve el historial completo de cada paciente. Con la demo (2–4 pacientes) responde rápido, pero con decenas de pacientes superará el timeout y, por el punto 4.1, caerá en silencio a los mocks.

| Quién | Qué |
|---|---|
| Backend | Lista ligera en `GET /admin/pacientes` (sin `historial` ni `telemetria_reciente`) y detalle en `GET /admin/pacientes/{id}` |
| Frontend | Pedir el detalle al abrir un paciente |
| Mientras tanto | Subir el timeout solo de esa ruta (p. ej. 15000 ms) |

El backend lo implementa en cuanto el frontend confirme que va a hacer el cambio.

### 4.6 [P1 · Frontend] Distinguir 401, 403 y 422
* **401** → cerrar sesión, que ya lo hace.
* **403** → mostrar `message` (por ejemplo, "Ese paciente no está vinculado contigo") en vez del texto genérico.
* **422** → error de formulario: mostrarlo junto al campo, sin cerrar el diálogo.

### 4.7 [P2 · Frontend] Registrar el `X-Request-ID` de cada error
Guardarlo junto al error (consola o reporte). Con ese id se encuentra la petición exacta en el log del backend.

## 5. Funciones que le faltan

### 5.1 [P1 · Frontend] Chat en tiempo real con la app móvil
Hoy el panel solo ve mensajes nuevos si recarga. El backend ya tiene el mismo WebSocket que usa la app, y por ahí le llegan al paciente los mensajes que el médico envía desde el panel. Falta que el panel lo use para recibir.

* URL: `ws://localhost:8081/realtime?token=<jwt>`. Los WebSocket no pasan por CORS.
* Suscribirse: `{"suscribir": "chat:<idConversacion>"}` → responde `{"tipo":"suscrito","canal":"chat:<id>"}`
* Evento: `{"canal":"chat:<id>","evento":"mensaje_nuevo","payload":{"idMensaje","autor","texto"}}`

```ts
export function escucharChat(idConversacion: string, token: string, alLlegar: () => void) {
  const base = import.meta.env.VITE_API_URL.replace(/^http/, 'ws').replace(/\/api\/v1\/?$/, '');
  const ws = new WebSocket(`${base}/realtime?token=${encodeURIComponent(token)}`);
  ws.onopen = () => ws.send(JSON.stringify({ suscribir: `chat:${idConversacion}` }));
  ws.onmessage = (m) => {
    const dato = JSON.parse(m.data);
    if (dato.evento === 'mensaje_nuevo') alLlegar(); // recargar GET /admin/conversaciones/{id}/mensajes
  };
  return () => ws.close();
}
```

El médico también puede suscribirse a `diario:<idPaciente>` (entradas nuevas del diario) y a `agenda:<idMedico>` (citas creadas o cambiadas).

### 5.2 [P1 · Frontend] Recargar las imágenes vencidas
`urlPreview` vale 1 hora. En el `onError` del `<img>`, volver a pedir los mensajes de esa conversación; llegan con enlaces nuevos.

### 5.3 [P1 · Ambos] Enviar adjuntos desde el panel
El contrato solo envía texto. La app ya sube archivos, pero lo hace por una ruta sin CORS (`/chat/{id}/adjuntos`), así que el navegador del panel no puede usarla.

| Quién | Qué |
|---|---|
| Backend | `POST /admin/conversaciones/{id}/adjuntos` (multipart: `tipo` = `FOTO`\|`ARCHIVO`\|`ESCANEO`, `archivo`), y aceptar `idAdjunto` en `POST …/mensajes` |
| Frontend | Botón de adjuntar, vista previa y envío |

### 5.4 [P1 · Ambos] Agenda y citas
El backend ya maneja franjas, citas, propuestas del médico y reprogramación, y la app las usa. El panel solo muestra `citas_hoy` y `proxima_cita`.

| Quién | Qué |
|---|---|
| Backend | Exponer bajo `/api/v1` lo que ya usa la app: agenda del médico, citas del día y de la semana, proponer y reprogramar |
| Frontend | Pantalla de agenda |

### 5.5 [P1 · Ambos] Diario de síntomas completo
Hoy las entradas del diario solo aparecen como alertas (las ROJO y ÁMBAR).

| Quién | Qué |
|---|---|
| Backend | `GET /admin/pacientes/{id}/diario` |
| Frontend | Línea de tiempo del diario en el detalle del paciente, en vivo con `diario:<idPaciente>` |

### 5.6 [P2 · Ambos] Alertas nuevas sin recargar
No hay canal en tiempo real para alertas. Mientras no exista, se puede pedir `GET /admin/alertas` cada 1–2 minutos, o recargar al recibir un evento de `diario:<idPaciente>`.

### 5.7 [Hecho 2026-09-16] Rol de administrador
El contrato menciona `administrator`, `suspender_cuenta`, `activar_cuenta` y `crear_doctor`, pero **el sistema no tiene ese rol**. Hoy la aprobación de cédulas se hace directo en la base. Para hacerlo desde la web hace falta:

1. decidir quién es administrador;
2. crear el rol y sus rutas en el backend;
3. hacer las pantallas en el frontend.

Por seguridad, esto se decide antes de construirlo.

### 5.8 [P2 · Ambos] Sesión de más de 12 horas
El token vence a las 12 horas y el panel cierra la sesión. Si en consulta hace falta más, se agrega un token de renovación en el backend y su manejo en el frontend.

## 6. Lo que no depende del panel (datos que el sistema no captura)
Estos campos seguirán en `null` o `[]` aunque el frontend esté perfecto. Para llenarlos hay que capturarlos en la **app móvil** o con dispositivos:

| Dato del contrato | Qué haría falta |
|---|---|
| Frecuencia cardiaca, variabilidad, SpO2 | Wearable o captura manual en la app |
| Glucosa en ayuno | Captura en la app (o un glucómetro conectado) |
| Sueño (horas, fases, interrupciones, calidad) | Wearable |
| Síntomas geriátricos diarios (dolor, confusión, incontinencia, mareo, ánimo) | Cuestionario diario en la app |
| Esquema de vacunación | Sección nueva en el expediente de la app |
| CIE-10, año de diagnóstico, "en control" | Captura estructurada de diagnósticos (hoy solo hay "condiciones críticas" en texto) |
| Principio activo, indicaciones especiales | Campos nuevos en el tratamiento |
| Alergia confirmada por prueba | Campo nuevo en alergias |
| Contacto autorizado para reinicio de cuenta | Campo nuevo en contactos de emergencia |
| Verificación de toma por foto con IA | Función nueva en la app |
| Tarjeta RFID "pendiente" | Flujo de asignación de tarjetas |

## 7. Resumen

| # | Qué | Quién | Prioridad |
|---|---|---|---|
| 4.1 | Quitar mocks fuera de desarrollo | Frontend | P0 |
| 4.2 | Mostrar bien los `null` | Frontend | P0 |
| 4.3 | Login del médico (sin `admin@salud.com`) | Frontend | P0 |
| 4.4 | Reinicio: 422/403 en el modal y contraseña mostrada una sola vez | Frontend | P0 |
| 4.5 | Lista ligera + detalle de paciente | Ambos | P0 |
| 4.6 | Distinguir 401 / 403 / 422 | Frontend | P1 |
| 5.1 | Chat en tiempo real | Frontend | P1 |
| 5.2 | Recargar imágenes vencidas | Frontend | P1 |
| 5.3 | Enviar adjuntos | Ambos | P1 |
| 5.4 | Agenda y citas | Ambos | P1 |
| 5.5 | Diario de síntomas | Ambos | P1 |
| 4.7 | Registrar `X-Request-ID` | Frontend | P2 |
| 5.6 | Alertas sin recargar | Ambos | P2 |
| 5.7 | ~~Rol de administrador~~ (hecho) | Ambos | — |
| 5.8 | Sesión de más de 12 h | Ambos | P2 |
| 6 | Datos clínicos que la app no captura | App móvil | — |
