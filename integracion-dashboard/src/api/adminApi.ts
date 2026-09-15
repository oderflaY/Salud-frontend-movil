// Cliente del backend Salud para el dashboard del médico. Todas las llamadas
// pasan por `pedir`: pone el token, lee el error `{"message": "MOTIVO"}` y, si
// la sesión ya no sirve, la cierra y avisa a `SesionGate`.
//
// Sin respaldo a datos de ejemplo a propósito: con este backend un 404 es una
// respuesta real (paciente o alerta de otro médico), no "no hay backend", y
// taparlo con mocks escondería errores de verdad.

import type {
  Alerta,
  EstadoAlerta,
  Kpis,
  Paciente,
  PrioridadAlerta,
  RegistroAuditoria,
  RespuestaLogin,
  ResultadoReset,
  SesionAdmin,
} from '../types/admin';

export const API_URL: string = String(import.meta.env.VITE_API_URL ?? 'http://localhost:8081/api/v1').replace(/\/+$/, '');

const CLAVE_TOKEN = 'auth_token';

/** Error del backend, con su código HTTP y el motivo tipado (`CREDENCIALES_INVALIDAS`, ...). */
export class ErrorApi extends Error {
  readonly status: number;
  readonly motivo: string;

  constructor(status: number, motivo: string) {
    super(motivo);
    this.name = 'ErrorApi';
    this.status = status;
    this.motivo = motivo;
  }
}

// Motivos que significan "vuelve a entrar". NO_AUTORIZADO sale con un token
// vencido (duran 12 horas) o inválido.
const MOTIVOS_SIN_SESION = new Set(['NO_AUTORIZADO', 'SESION_REVOCADA', 'CUENTA_INACTIVA']);

const oyentesDeSesion = new Set<() => void>();

/** `SesionGate` se suscribe aquí para volver al login cuando la sesión muere. */
export function alPerderSesion(oyente: () => void): () => void {
  oyentesDeSesion.add(oyente);
  return () => {
    oyentesDeSesion.delete(oyente);
  };
}

export function obtenerToken(): string | null {
  return localStorage.getItem(CLAVE_TOKEN);
}

export function cerrarSesion(): void {
  localStorage.removeItem(CLAVE_TOKEN);
  oyentesDeSesion.forEach((oyente) => oyente());
}

async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const token = obtenerToken();
  const cabeceras = new Headers(opciones.headers);
  if (token) cabeceras.set('Authorization', `Bearer ${token}`);
  if (opciones.body !== undefined) cabeceras.set('Content-Type', 'application/json');

  const respuesta = await fetch(`${API_URL}${ruta}`, { ...opciones, headers: cabeceras });
  if (respuesta.ok) {
    return (respuesta.status === 204 ? undefined : await respuesta.json()) as T;
  }

  let motivo = 'ERROR_DESCONOCIDO';
  try {
    const cuerpo: unknown = await respuesta.json();
    if (cuerpo && typeof cuerpo === 'object' && typeof (cuerpo as { message?: unknown }).message === 'string') {
      motivo = (cuerpo as { message: string }).message;
    }
  } catch {
    // Cuerpo vacío o no JSON: se queda ERROR_DESCONOCIDO.
  }
  if (respuesta.status === 401 && token && MOTIVOS_SIN_SESION.has(motivo)) cerrarSesion();
  throw new ErrorApi(respuesta.status, motivo);
}

// ------------------------------------------------------------------ Sesión

/** Login con la cuenta del médico en la app. Guarda el token en `localStorage('auth_token')`. */
export async function iniciarSesion(correo: string, contrasena: string): Promise<RespuestaLogin> {
  const respuesta = await pedir<RespuestaLogin>('/auth/sesion', {
    method: 'POST',
    body: JSON.stringify({ correo: correo.trim().toLowerCase(), contrasena }),
  });
  localStorage.setItem(CLAVE_TOKEN, respuesta.token);
  return respuesta;
}

export const getSesion = (): Promise<SesionAdmin> => pedir<SesionAdmin>('/auth/me');

// ------------------------------------------------------------------ Lecturas

export const getKpis = (): Promise<Kpis> => pedir<Kpis>('/admin/dashboard/kpis');

export const getPacientes = (): Promise<Paciente[]> => pedir<Paciente[]>('/admin/pacientes');

export const getAuditoria = (): Promise<RegistroAuditoria[]> => pedir<RegistroAuditoria[]>('/admin/auditoria');

export function getAlertas(filtro: { estado?: EstadoAlerta; prioridad?: PrioridadAlerta } = {}): Promise<Alerta[]> {
  const parametros = new URLSearchParams();
  if (filtro.estado) parametros.set('estado', filtro.estado);
  if (filtro.prioridad) parametros.set('prioridad', filtro.prioridad);
  const consulta = parametros.toString();
  return pedir<Alerta[]>(`/admin/alertas${consulta ? `?${consulta}` : ''}`);
}

// ------------------------------------------------------------------ Acciones

/** Devuelve la alerta ya actualizada. `nota` vacía la borra; sin `nota` se conserva la anterior. */
export function actualizarEstadoAlerta(idAlerta: string, estado: EstadoAlerta, nota?: string): Promise<Alerta> {
  return pedir<Alerta>(`/admin/alertas/${encodeURIComponent(idAlerta)}/estado`, {
    method: 'PATCH',
    body: JSON.stringify(nota === undefined ? { estado } : { estado, nota }),
  });
}

/**
 * Reset de la cuenta del paciente: devuelve la contraseña temporal, que hay que
 * mostrarle al médico UNA vez (ver `ModalContrasenaTemporal`). Cierra todas las
 * sesiones del paciente; no toca su expediente.
 */
export function resetCuentaPaciente(idPaciente: string, motivo?: string): Promise<ResultadoReset> {
  return pedir<ResultadoReset>(`/admin/usuarios/${encodeURIComponent(idPaciente)}/reset-cuenta`, {
    method: 'PATCH',
    body: JSON.stringify(motivo?.trim() ? { motivo: motivo.trim() } : {}),
  });
}

// ------------------------------------------------------------------ Mensajes

const MENSAJES: Record<string, string> = {
  CREDENCIALES_INVALIDAS: 'El correo o la contraseña no coinciden.',
  CUENTA_BLOQUEADA: 'Tu cuenta está bloqueada. Contacta a la clínica.',
  NO_AUTORIZADO: 'Tu sesión venció. Vuelve a entrar.',
  SESION_REVOCADA: 'Tu sesión se cerró. Vuelve a entrar.',
  CUENTA_INACTIVA: 'Tu cuenta ya no está activa.',
  ACCESO_DENEGADO: 'Este panel es solo para médicos.',
  MEDICO_NO_VERIFICADO: 'Tu cédula aún no está aprobada: no puedes resetear cuentas.',
  PACIENTE_NO_ENCONTRADO: 'Ese paciente no está vinculado contigo.',
  ALERTA_NO_ENCONTRADA: 'Esa alerta ya no existe o no es tuya.',
  SOLICITUD_INVALIDA: 'Revisa los datos enviados.',
};

/** Texto para mostrar en pantalla a partir de cualquier error de estas funciones. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ErrorApi) return MENSAJES[error.motivo] ?? `Error del servidor (${error.status}).`;
  return `No hay conexión con el servidor (${API_URL}).`;
}
