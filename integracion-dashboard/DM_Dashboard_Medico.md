# Documento Maestro (DM) - Conexión del Dashboard del Médico con el Backend

## 1. Visión General
Este documento define cómo el **Dashboard web del médico** (Salud-Frontend: React + TypeScript + Vite) se conecta al backend de Salud: qué endpoints consume, qué JSON recibe, qué reglas de negocio aplica el servidor y qué pasos hay que seguir en el frontend para dejarlo funcionando.

Todo lo descrito aquí ya está implementado en el backend (Rust/Axum + PostgreSQL), y se probó de punta a punta con peticiones reales: 84 comprobaciones HTTP y 59 pruebas de la base de datos.

**Quién usa el dashboard:** el **médico**, con la **misma cuenta que usa en la app móvil**. Solo ve a los pacientes que tienen un **vínculo vigente con él**. No existe una cuenta de administrador con acceso a todos los pacientes: el dashboard respeta las mismas reglas de privacidad que la app.

## 2. Conexión

| Concepto | Valor |
|---|---|
| URL base (local) | `http://localhost:8081/api/v1`. Es el valor que el dashboard ya trae por defecto. |
| URL base (otra computadora de la red) | `http://192.168.18.26:8081/api/v1` |
| URL base (producción) | `https://api.tudominio.com/api/v1` |
| Variable de entorno | `VITE_API_URL`, en el `.env` de la raíz de Salud-Frontend |
| Autenticación | `Authorization: Bearer <token>`. El token vive en `localStorage('auth_token')`. |
| Formato | JSON (`Content-Type: application/json`) |
| Duración del token | 12 horas |
| CORS | Permitidos `http://localhost:5173` y `http://localhost:4173` (Vite `dev` y `preview`) |

**Cuentas de la demo** (todas con la contraseña `Demo1234`):

| Correo | Médico | Pacientes vinculados |
|---|---|---|
| `dr.silva@salud.local` | Dr. Carlos Silva Rodríguez (Medicina general) | paciente1, paciente2 |
| `dra.lopez@salud.local` | Neumología | paciente1, paciente2 |
| `dr.garcia@salud.local` | Cardiología | paciente1, paciente3 |
| `dra.torres@salud.local` | Endocrinología | paciente3, paciente4 |

## 3. Normativas de Datos y Tipado

* **Nomenclatura de campos:** `camelCase`, exactamente como en `src/types/admin.ts`. El frontend no renombra nada.
* **Prefijos de IDs:** `doc_` médico, `pac_` paciente, `alerta_` alerta, `audreset_` registro de auditoría.
* **Fechas:** los instantes van en UTC con `Z` (`2026-09-14T15:04:05Z`). Las fechas sin hora van como `YYYY-MM-DD` y las horas como `HH:MM`. El frontend las convierte a hora local para mostrarlas.
* **Valores ausentes:** llegan como `null`, nunca se omiten. Las listas vacías llegan como `[]`.
* **Enumeraciones:** en MAYÚSCULAS (`ALTO`, `NUEVA`, `ALTA`). Excepción: `estadoCuenta` y `nivelAcceso`, que van en minúsculas.
* **Errores:** siempre `{"message": "MOTIVO"}` con el código HTTP correspondiente (sección 7). El frontend decide qué mostrar a partir de `message`, nunca a partir de un texto libre.
* **Privacidad:**
  * El correo del paciente llega enmascarado (`pa••••••1@salud.local`).
  * La contraseña temporal del reset se entrega una sola vez y no se guarda en ningún lado.
  * El dashboard no debe guardar datos clínicos en `localStorage`: ahí solo va el token.
* **Sin datos de ejemplo de respaldo:** con este backend, un `404` es una respuesta real (un paciente o una alerta que no es del médico). No debe interpretarse como "no hay servidor" ni taparse con `mockAdmin.ts`.

## 4. Endpoints

| Método | Ruta | Función en `adminApi.ts` |
|---|---|---|
| POST | `/auth/sesion` | `iniciarSesion` (nuevo: el dashboard no tenía login) |
| GET | `/auth/me` | `getSesion` |
| GET | `/admin/dashboard/kpis` | `getKpis` |
| GET | `/admin/pacientes` | `getPacientes` |
| PATCH | `/admin/usuarios/{idPaciente}/reset-cuenta` | `resetCuentaPaciente` |
| GET | `/admin/alertas?estado=&prioridad=` | `getAlertas` |
| PATCH | `/admin/alertas/{idAlerta}/estado` | `actualizarEstadoAlerta` |
| GET | `/admin/auditoria` | `getAuditoria` |

### 4.1 Iniciar sesión: `POST /auth/sesion`

Petición:
```json
{ "correo": "dr.silva@salud.local", "contrasena": "Demo1234" }
```

Respuesta `200`:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiraEn": "2026-09-15T03:10:00Z",
  "usuario": {
    "idMedico": "doc_3f1c9a...",
    "rol": "medico",
    "correo": "dr.silva@salud.local",
    "nombre": "Carlos",
    "apellidos": "Silva Rodríguez",
    "tratamiento": "Dr.",
    "nombreCompleto": "Dr. Carlos Silva Rodríguez",
    "cedulaProfesional": "1234567",
    "especialidad": "MEDICINA_GENERAL",
    "estadoVerificacion": "APROBADO",
    "expiraEn": "2026-09-15T03:10:00Z"
  }
}
```
El frontend guarda `token` en `localStorage('auth_token')`.

### 4.2 Sesión activa: `GET /auth/me`
Devuelve el mismo objeto que `usuario` del login. Sirve al abrir el dashboard, para saber si el token guardado sigue valiendo: si responde `401`, hay que volver al login.

### 4.3 KPIs: `GET /admin/dashboard/kpis`
```json
{
  "totalPacientes": 2,
  "pacientesRiesgoAlto": 0,
  "pacientesRiesgoMedio": 1,
  "pacientesRiesgoBajo": 1,
  "adherenciaPromedio": 78.4,
  "alertasAbiertas": 4,
  "alertasAltaPrioridad": 1,
  "citasHoy": 1,
  "citasProximos7Dias": 3,
  "propuestasSinResponder": 0,
  "mensajesSinLeer": 2,
  "entradasDiarioRojas7Dias": 0,
  "tratamientosPorAgotarse": 1,
  "resetsUltimos30Dias": 0,
  "generadoEn": "2026-09-14T15:10:00Z"
}
```
* `adherenciaPromedio` es un porcentaje con un decimal. Vale `null` cuando no hay tomas que medir, que no es lo mismo que 0 %.
* Todo se calcula solo sobre los pacientes vinculados al médico.

### 4.4 Pacientes: `GET /admin/pacientes`
Arreglo ordenado por riesgo (primero `ALTO`) y luego por nombre. Un elemento:
```json
{
  "idPaciente": "pac_8b2e...",
  "nombre": "Juan",
  "apellidos": "Pérez García",
  "nombreCompleto": "Juan Pérez García",
  "correoEnmascarado": "pa••••••1@salud.local",
  "fechaNacimiento": "1985-06-15",
  "edad": 41,
  "genero": "M",
  "telefono": "5551234567",
  "estadoCuenta": "activa",
  "registroCompleto": true,
  "requiereCambioContrasena": false,
  "pacienteDesde": "2026-03-10T16:00:00Z",
  "vinculo": { "nivelAcceso": "lectura_escritura", "desde": "2026-07-16T12:00:00Z", "expira": null },
  "riesgo": "BAJO",
  "adherencia": {
    "porcentaje7Dias": 88.9,
    "dosisProgramadas": 27,
    "dosisCumplidas": 24,
    "porDia": [
      { "fecha": "2026-09-08", "programadas": 4, "cumplidas": 4 },
      { "fecha": "2026-09-09", "programadas": 4, "cumplidas": 3 }
    ]
  },
  "metricasVitales": { "pesoKg": 82.5, "alturaCm": 175, "imc": 26.9, "presionArterial": "130/85", "fechaToma": "2026-09-11T14:00:00Z" },
  "tipoSangre": "O+",
  "alergias": [ { "alergeno": "Penicilina", "severidad": "ALTA", "reaccion": "Anafilaxia" } ],
  "condicionesCriticas": [ "Diabetes tipo 2", "Hipertensión" ],
  "antecedentesHeredofamiliares": [ "Diabetes (padre)" ],
  "cirugias": [ { "procedimiento": "Apendicectomía", "fecha": "2010-04-02", "notas": null } ],
  "tratamientosActivos": [
    {
      "idTratamiento": "trt_002", "medicamento": "Atorvastatina", "dosis": "20mg", "horarios": [ "21:00" ],
      "frecuenciaHoras": 24, "viaAdministracion": "Oral", "fechaInicio": "2026-04-17", "fechaFin": null,
      "cantidadRestante": 8, "umbralAlerta": 10, "porAgotarse": true
    }
  ],
  "ultimaEntradaDiario": { "instante": "2026-09-10T20:00:00Z", "severidad": "AMBAR", "texto": "Sentí un poco de sed..." },
  "proximaCita": { "idCita": "cita_...", "folio": "SAL-000123", "fecha": "2026-09-15", "horaInicio": "10:00", "horaFin": "10:30", "estado": "CONFIRMADA" },
  "idConversacion": "conv_001",
  "mensajesSinLeer": 1,
  "alertasAbiertas": 2,
  "tarjetaRfidActiva": true,
  "consultasTarjeta30Dias": 3,
  "ultimoReset": null
}
```
* `porDia` siempre trae los últimos 7 días. Sirve para una gráfica de adherencia.
* `consultasTarjeta30Dias` cuenta las veces que se leyó la tarjeta RFID de emergencia del paciente.

### 4.5 Alertas: `GET /admin/alertas`
Filtros opcionales: `?estado=NUEVA` y `?prioridad=ALTA`. Primero vienen las abiertas y más urgentes.
```json
[
  {
    "idAlerta": "alerta_5d0e...",
    "idPaciente": "pac_8b2e...",
    "nombrePaciente": "Juan Pérez García",
    "tipo": "INVENTARIO_BAJO",
    "prioridad": "MEDIA",
    "titulo": "Se está terminando Atorvastatina",
    "detalle": "Le quedan 8 dosis. Le alcanza para unos 8 días.",
    "estado": "NUEVA",
    "nota": null,
    "creadaEn": "2026-09-14T15:10:00Z",
    "actualizadaEn": "2026-09-14T15:10:00Z"
  }
]
```

### 4.6 Cambiar estado de alerta: `PATCH /admin/alertas/{idAlerta}/estado`
```json
{ "estado": "EN_REVISION", "nota": "Llamé al paciente, pasa mañana por la receta." }
```
* Responde `200` con la alerta ya actualizada, con el mismo formato que en 4.5.
* Sin `nota`, se conserva la anterior. Con `"nota": ""`, se borra.

### 4.7 Resetear cuenta: `PATCH /admin/usuarios/{idPaciente}/reset-cuenta`
```json
{ "motivo": "Olvidó su contraseña" }
```
El cuerpo es opcional. Respuesta `200` (con `Cache-Control: no-store`):
```json
{
  "idPaciente": "pac_8b2e...",
  "contrasenaTemporal": "k7m4p-9qx2h",
  "expiraEn": "2026-09-17T15:10:00Z",
  "sesionesCerradas": true,
  "idAuditoria": "audreset_91aa...",
  "reseteadoEn": "2026-09-14T15:10:00Z",
  "instrucciones": "Entrega esta contraseña al paciente. Al entrar a la app con su correo y esta contraseña, la app le pedirá elegir una nueva. Vence en 72 horas. Sus sesiones abiertas ya se cerraron."
}
```

### 4.8 Auditoría: `GET /admin/auditoria`
El más reciente primero:
```json
[
  {
    "idAuditoria": "audreset_91aa...",
    "accion": "RESET_CUENTA",
    "idPaciente": "pac_8b2e...",
    "nombrePaciente": "Juan Pérez García",
    "idMedico": "doc_3f1c9a...",
    "nombreMedico": "Dr. Carlos Silva Rodríguez",
    "realizadoPorMi": true,
    "motivo": "Olvidó su contraseña",
    "ipOrigen": "192.168.18.40",
    "fecha": "2026-09-14T15:10:00Z"
  }
]
```

## 5. Reglas de Negocio (las aplica el servidor)

### 5.1 Reset de cuenta
1. Solo puede resetear un médico con la cédula **aprobada** (`estadoVerificacion = APROBADO`) y con **vínculo vigente** con el paciente.
2. Se genera una contraseña temporal. Se muestra una sola vez; si se pierde, se resetea otra vez.
3. Se **cierran todas las sesiones abiertas** del paciente, en la app y en toda la API.
4. El paciente entra a la app con su correo y la temporal, y la app le **obliga a elegir una contraseña nueva** antes de hacer cualquier otra cosa.
5. La temporal **vence en 72 horas**.
6. **No se toca el expediente clínico.**
7. Todo reset queda en la auditoría. La ven el médico que lo hizo y los demás médicos del paciente, pero la IP solo la ve quien lo hizo.

**Recomendación de interfaz:** antes de resetear, mostrar `nombreCompleto` y `correoEnmascarado` para confirmar con el paciente que es su cuenta. Después, mostrar la contraseña en un modal (`ModalContrasenaTemporal.tsx`).

### 5.2 Alertas predictivas
Se generan solas a partir de datos que el sistema ya tiene. Se actualizan cada vez que el dashboard pide alertas, KPIs o pacientes.

| Tipo | Origen | Prioridad |
|---|---|---|
| `SINTOMA_DE_ALARMA` | Entrada del diario de los últimos 7 días en ROJO o ÁMBAR | ROJO → `ALTA`, ÁMBAR → `MEDIA` |
| `RIESGO_ADHERENCIA` | Riesgo de abandonar el tratamiento, según las tomas de 7 días (el mismo cálculo que la app) | ALTO → `ALTA`, MEDIO → `MEDIA` |
| `INVENTARIO_BAJO` | El medicamento llegó a su umbral; el detalle dice para cuántos días le alcanza | Menos de 2 días → `ALTA`, si no `MEDIA` |

* **Estados:** `NUEVA` → `EN_REVISION` → `ATENDIDA` o `DESCARTADA`. Se puede regresar a cualquiera.
* **Bandeja por médico:** que un médico atienda una alerta no se la oculta a otro médico del mismo paciente.
* **Sin duplicados:** nunca hay dos alertas abiertas del mismo tipo para el mismo paciente. Si el problema sigue, una alerta atendida vuelve a salir la semana siguiente.
* **Escalado:** si un paciente pasa de riesgo MEDIO a ALTO, su alerta abierta sube a `ALTA`; no se abre otra.

## 6. Integración en Salud-Frontend

Archivos listos en esta carpeta (`integracion-dashboard/`). Compilan con TypeScript en modo estricto:

| Archivo | Acción |
|---|---|
| `src/types/admin.ts` | Reemplaza al actual |
| `src/api/adminApi.ts` | Reemplaza al actual |
| `src/components/SesionGate.tsx` | Nuevo: login obligatorio y cierre de sesión automático |
| `src/components/Login.tsx` | Nuevo: pantalla de login |
| `src/components/ModalContrasenaTemporal.tsx` | Nuevo: muestra la contraseña del reset |
| `.env.example` | Copiarlo como `.env` |

Pasos:
1. **Backend arriba**, en la computadora del Docker:
   ```bash
   cd ~/Documents/GitHub/Salud-frontend-movil && sudo docker compose up migrate && sudo docker compose up -d --build && sudo bash scripts/seed_demo.sh
   ```
   Comprobar: `curl http://localhost:8081/healthz` debe responder `ok`.
2. **Copiar los archivos** (cambiar la ruta de Salud-Frontend):
   ```bash
   cp -r ~/Documents/GitHub/Salud-frontend-movil/integracion-dashboard/src/. ~/ruta/a/Salud-Frontend/src/ && cp ~/Documents/GitHub/Salud-frontend-movil/integracion-dashboard/.env.example ~/ruta/a/Salud-Frontend/.env
   ```
3. **Login obligatorio**, en `src/main.tsx`: `<SesionGate><App /></SesionGate>`. Dentro, el médico en sesión se obtiene con `const { usuario, salir } = useSesion();`.
4. **Quitar `mockAdmin.ts`**: ningún componente debe importarlo (ver la sección 3).
5. **Ajustar los componentes:** correr `npx tsc --noEmit` y corregir cada campo que marque, usando `src/types/admin.ts` como referencia.
6. **Botón de reset** en la tabla de pacientes:
   ```tsx
   const [reset, setReset] = useState<ResultadoReset | null>(null);
   async function resetear(p: Paciente) {
     if (!confirm(`¿Resetear la cuenta de ${p.nombreCompleto} (${p.correoEnmascarado})?`)) return;
     try { setReset(await resetCuentaPaciente(p.idPaciente, 'Olvidó su contraseña')); }
     catch (e) { alert(mensajeDeError(e)); }
   }
   {reset && <ModalContrasenaTemporal resultado={reset} onCerrar={() => setReset(null)} />}
   ```
7. `npm run dev` y entrar con `dr.silva@salud.local` / `Demo1234`.

## 7. Errores

| HTTP | `message` | Cuándo | Qué hace el frontend |
|---|---|---|---|
| 401 | `NO_AUTORIZADO` | Sin token, o con un token vencido o inválido | Volver al login (automático) |
| 401 | `SESION_REVOCADA` | El token es anterior a un cierre de sesiones | Volver al login (automático) |
| 401 | `CUENTA_INACTIVA` | La cuenta del médico se bloqueó o se dio de baja | Volver al login (automático) |
| 401 | `CREDENCIALES_INVALIDAS` | Correo o contraseña incorrectos en el login | Mostrar el error |
| 403 | `CUENTA_BLOQUEADA` | Login de una cuenta bloqueada | Mostrar el error |
| 403 | `ACCESO_DENEGADO` | Se usó un token de paciente | "Este panel es solo para médicos" |
| 403 | `MEDICO_NO_VERIFICADO` | Reset por un médico sin la cédula aprobada | Mostrar el error |
| 404 | `PACIENTE_NO_ENCONTRADO` | El paciente no existe o no está vinculado (a propósito se responde igual) | Mostrar el error |
| 404 | `ALERTA_NO_ENCONTRADA` | La alerta no existe o es de otro médico | Recargar la lista |
| 400 | `SOLICITUD_INVALIDA` | Estado o prioridad fuera de la lista, motivo de más de 500 caracteres o nota de más de 1000 | Revisar el formulario |

`mensajeDeError(e)` en `adminApi.ts` ya traduce cada motivo a un texto en español.

## 8. Solución de Problemas

| Síntoma | Causa y arreglo |
|---|---|
| "No hay conexión con el servidor" | El backend no está arriba, o `VITE_API_URL` apunta mal. Probar la URL con `curl` y reiniciar `npm run dev` después de cambiar el `.env`. |
| En la consola del navegador: *blocked by CORS policy* | El origen del dashboard no está permitido. En el `.env` **del backend**, agregarlo: `CORS_ORIGENES=http://localhost:5173,http://192.168.18.26:5173`, y luego `sudo docker compose up -d backend`. En producción va el dominio del dashboard; nunca `*`. |
| `404` en cualquier `/api/v1/...` | El backend en Docker es el anterior: repetir el paso 1 de la sección 6. |
| Lista de pacientes vacía | Ese médico no tiene pacientes vinculados. Probar con `dr.silva`. |
| El login rechaza `Demo1234` | Volver a sembrar la demo: `sudo bash scripts/seed_demo.sh`. |

## 9. Verificación
Con el backend arriba, `bash scripts/verificar_demo.sh` (del repo Salud-frontend-movil) corre 114 comprobaciones contra la API real. La sección **J** cubre el dashboard completo: login, CORS, KPIs, pacientes, alertas y un reset con un paciente desechable, sin tocar las cuentas de la demo.
