// Contrato del backend Salud (/api/v1). Fuente: docs/dashboard-medico.md del repo Salud-frontend-movil.
// Todos los nombres de campo son exactamente los que manda el backend.

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
