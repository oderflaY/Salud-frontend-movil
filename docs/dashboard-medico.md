# Dashboard web del médico: contrato de `/api/v1`

Para quien mantiene **Salud-Frontend** (React + TypeScript + Vite). Todo lo de aquí está implementado y probado contra el backend real.

## Conexión

| | |
|---|---|
| Base URL | `http://localhost:8081/api/v1`, el valor por defecto de `VITE_API_URL`: funciona sin configurar nada. También sirve `http://localhost:8000/api/v1`. En producción: `https://api.tudominio.com/api/v1`. |
| Autenticación | `Authorization: Bearer <token>`, con el token guardado en `localStorage('auth_token')`. |
| CORS | Permitidos `http://localhost:5173` y `:4173` (Vite `dev` y `preview`). Otro origen se agrega en `CORS_ORIGENES` del `.env` del backend. |
| Errores | Siempre `{"message": "MOTIVO"}` con el código HTTP. Tabla al final. |
| Fechas | Instantes en UTC con `Z` (`2026-09-14T15:04:05Z`); fechas sin hora como `YYYY-MM-DD`. |

**Quién entra:** el médico, con **su misma cuenta de la app** (p. ej. `dr.silva@salud.local` / `Demo1234` en la demo). Ve **solo a los pacientes con vínculo vigente con él**, las mismas reglas que la app. No existe una cuenta de administrador que vea a todos.

## Endpoints

| Método | Ruta | Uso en el dashboard |
|---|---|---|
| POST | `/auth/sesion` | Login (**nuevo**: la lista original no traía login). Cuerpo `{correo, contrasena}` → `{token, expiraEn, usuario}`. |
| GET | `/auth/me` | `getSesion` |
| GET | `/admin/dashboard/kpis` | `getKpis` |
| GET | `/admin/pacientes` | `getPacientes` |
| PATCH | `/admin/usuarios/{idPaciente}/reset-cuenta` | `resetCuentaPaciente`. Cuerpo opcional `{motivo}`. |
| GET | `/admin/alertas?estado=&prioridad=` | `getAlertas`. Los dos filtros son opcionales. |
| PATCH | `/admin/alertas/{idAlerta}/estado` | `actualizarEstadoAlerta`. Cuerpo `{estado, nota?}`; devuelve la alerta actualizada. |
| GET | `/admin/auditoria` | `getAuditoria` |

### Qué hace "Resetear cuenta"

1. Genera una **contraseña temporal** (forma `k7m4p-9qx2h`). La respuesta la trae **una sola vez** y no se guarda en ningún lado: si se pierde, se resetea otra vez.
2. **Cierra todas las sesiones abiertas** del paciente, en la app y en toda la API.
3. El paciente entra a la app con su correo y la temporal, y la app le **obliga a elegir una nueva** antes de hacer cualquier otra cosa.
4. La temporal **vence en 72 h**.
5. **No toca el expediente clínico.**
6. Queda en la auditoría. La ven el médico que reseteó y los demás médicos del paciente, pero la IP solo la ve quien reseteó.

Requisitos: médico con cédula `APROBADO` (si no, `403 MEDICO_NO_VERIFICADO`) y vínculo vigente con el paciente (si no, `404 PACIENTE_NO_ENCONTRADO`). En pantalla conviene mostrar `correoEnmascarado`, para confirmar con el paciente que es su cuenta antes de resetear.

### Cómo se generan las alertas

Salen de datos que el sistema ya tiene, y se ponen al día cada vez que se piden alertas, KPIs o pacientes:

| Tipo | Cuándo | Prioridad |
|---|---|---|
| `SINTOMA_DE_ALARMA` | Entrada del diario de los últimos 7 días en ROJO o ÁMBAR | ROJO → `ALTA`, ÁMBAR → `MEDIA` |
| `RIESGO_ADHERENCIA` | Riesgo ALTO o MEDIO según las tomas de 7 días (el mismo cálculo que la cartera de la app) | ALTO → `ALTA`, MEDIO → `MEDIA` |
| `INVENTARIO_BAJO` | El medicamento llegó a su umbral; el detalle dice para cuántos días le alcanza | Menos de 2 días → `ALTA`, si no `MEDIA` |

Cada médico tiene su propia bandeja: que uno marque una alerta como atendida no se la oculta a otro médico del mismo paciente.

Si el problema sigue, una alerta de riesgo o de inventario ya atendida vuelve a salir la semana siguiente. Nunca hay dos abiertas del mismo tipo para el mismo paciente. Si un paciente pasa de MEDIO a ALTO, su alerta abierta sube a `ALTA`; no se abre otra.

Los estados son `NUEVA`, `EN_REVISION`, `ATENDIDA` y `DESCARTADA`. Se aceptan en mayúsculas o minúsculas.

## Tipos para `src/types/admin.ts`

```ts
export type Riesgo = 'BAJO' | 'MEDIO' | 'ALTO';
export type EstadoAlerta = 'NUEVA' | 'EN_REVISION' | 'ATENDIDA' | 'DESCARTADA';
export type PrioridadAlerta = 'ALTA' | 'MEDIA' | 'BAJA';
export type TipoAlerta = 'SINTOMA_DE_ALARMA' | 'RIESGO_ADHERENCIA' | 'INVENTARIO_BAJO';

export interface SesionAdmin {            // GET /auth/me  y  usuario de POST /auth/sesion
  idMedico: string;
  rol: 'medico';
  correo: string;
  nombre: string;
  apellidos: string;
  tratamiento: 'Dr.' | 'Dra.' | 'Dr(a).';
  nombreCompleto: string;                  // "Dr. Carlos Silva Rodríguez"
  cedulaProfesional: string;
  especialidad: string | null;             // "CARDIOLOGIA", ...
  estadoVerificacion: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
  expiraEn: string;                        // cuándo vence el token
}

export interface RespuestaLogin {         // POST /auth/sesion
  token: string;                           // -> localStorage.setItem('auth_token', token)
  expiraEn: string;
  usuario: SesionAdmin;
}

export interface Kpis {                   // GET /admin/dashboard/kpis
  totalPacientes: number;
  pacientesRiesgoAlto: number;
  pacientesRiesgoMedio: number;
  pacientesRiesgoBajo: number;
  adherenciaPromedio: number | null;       // %, 1 decimal; null = sin tomas que medir
  alertasAbiertas: number;
  alertasAltaPrioridad: number;
  citasHoy: number;
  citasProximos7Dias: number;
  propuestasSinResponder: number;
  mensajesSinLeer: number;
  entradasDiarioRojas7Dias: number;
  tratamientosPorAgotarse: number;
  resetsUltimos30Dias: number;
  generadoEn: string;
}

export interface Paciente {               // GET /admin/pacientes -> Paciente[]  (primero riesgo ALTO)
  idPaciente: string;
  nombre: string | null;
  apellidos: string | null;
  nombreCompleto: string;
  correoEnmascarado: string;               // "pa••••••1@salud.local"
  fechaNacimiento: string | null;
  edad: number | null;
  genero: string | null;
  telefono: string | null;
  estadoCuenta: 'activa' | 'bloqueada';
  registroCompleto: boolean;
  requiereCambioContrasena: boolean;       // tiene una temporal sin usar
  pacienteDesde: string;
  vinculo: { nivelAcceso: 'lectura' | 'lectura_escritura'; desde: string; expira: string | null };
  riesgo: Riesgo;
  adherencia: {
    porcentaje7Dias: number | null;
    dosisProgramadas: number;
    dosisCumplidas: number;
    porDia: { fecha: string; programadas: number; cumplidas: number }[];   // 7 días
  };
  metricasVitales: {
    pesoKg: number | null; alturaCm: number | null; imc: number | null;
    presionArterial: string | null; fechaToma: string | null;
  };
  tipoSangre: string | null;
  alergias: { alergeno: string; severidad: string | null; reaccion: string | null }[];
  condicionesCriticas: string[];
  antecedentesHeredofamiliares: string[];
  cirugias: { procedimiento: string; fecha: string | null; notas: string | null }[];
  tratamientosActivos: {
    idTratamiento: string; medicamento: string; dosis: string | null; horarios: string[];
    frecuenciaHoras: number | null; viaAdministracion: string | null;
    fechaInicio: string | null; fechaFin: string | null;
    cantidadRestante: number | null; umbralAlerta: number; porAgotarse: boolean;
  }[];
  ultimaEntradaDiario: { instante: string; severidad: 'VERDE' | 'AMBAR' | 'ROJO'; texto: string } | null;
  proximaCita: { idCita: string; folio: string; fecha: string; horaInicio: string; horaFin: string; estado: string } | null;
  idConversacion: string | null;
  mensajesSinLeer: number;
  alertasAbiertas: number;
  tarjetaRfidActiva: boolean;
  consultasTarjeta30Dias: number;          // veces que se leyó su tarjeta de emergencia
  ultimoReset: string | null;
}

export interface Alerta {                 // GET /admin/alertas -> Alerta[]  (abiertas y urgentes primero)
  idAlerta: string;
  idPaciente: string;
  nombrePaciente: string;
  tipo: TipoAlerta;
  prioridad: PrioridadAlerta;
  titulo: string;
  detalle: string;
  estado: EstadoAlerta;
  nota: string | null;
  creadaEn: string;                        // cuándo ocurrió la señal
  actualizadaEn: string;
}

export interface CambioEstadoAlerta { estado: EstadoAlerta; nota?: string }  // nota vacía la borra; sin nota se conserva

export interface ResultadoReset {         // PATCH /admin/usuarios/{id}/reset-cuenta
  idPaciente: string;
  contrasenaTemporal: string;              // mostrar UNA vez
  expiraEn: string;
  sesionesCerradas: true;
  idAuditoria: string;
  reseteadoEn: string;
  instrucciones: string;                   // texto listo para mostrar al médico
}

export interface RegistroAuditoria {      // GET /admin/auditoria -> RegistroAuditoria[] (más reciente primero)
  idAuditoria: string;
  accion: 'RESET_CUENTA';
  idPaciente: string;
  nombrePaciente: string;
  idMedico: string;
  nombreMedico: string;
  realizadoPorMi: boolean;
  motivo: string | null;
  ipOrigen: string | null;                 // solo en los propios
  fecha: string;
}
```

## Errores

| HTTP | `message` | Cuándo |
|---|---|---|
| 401 | `NO_AUTORIZADO` | Sin token, o con un token inválido o vencido. **Ir al login.** |
| 401 | `SESION_REVOCADA`, `CUENTA_INACTIVA` | La cuenta se bloqueó o se dio de baja después de emitir el token. **Ir al login.** |
| 401 | `CREDENCIALES_INVALIDAS` | Login con correo o contraseña incorrectos. |
| 403 | `CUENTA_BLOQUEADA` | Login de una cuenta bloqueada. |
| 403 | `ACCESO_DENEGADO` | Un token de paciente en el dashboard. |
| 403 | `MEDICO_NO_VERIFICADO` | Reset por un médico sin la cédula aprobada. |
| 404 | `PACIENTE_NO_ENCONTRADO` | El paciente no existe o no tiene vínculo con este médico (a propósito se responde igual). |
| 404 | `ALERTA_NO_ENCONTRADA` | La alerta no existe o es de otro médico. |
| 400 | `SOLICITUD_INVALIDA` | Estado o prioridad fuera de la lista, o motivo o nota demasiado largos (500 y 1000 caracteres). |

**Ojo con los mocks.** El dashboard cae a sus datos de ejemplo cuando el backend responde 404 o no hay conexión. Con este backend, un 404 es una respuesta real (paciente o alerta ajenos): conviene no tratar ese 404 como "no hay backend".
