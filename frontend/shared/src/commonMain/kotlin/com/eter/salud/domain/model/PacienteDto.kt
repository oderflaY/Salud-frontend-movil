package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * DTO central del paciente. Espejo exacto del esquema definido en
 * DM_HistorialMedico.md, seccion 3 (Payload Central).
 *
 * Normativas aplicadas:
 *  - Fechas temporales en ISO 8601 UTC (`YYYY-MM-DDTHH:MM:SSZ`).
 *  - Fechas de calendario (nacimiento, cirugias, inicio y fin de tratamiento)
 *    en `YYYY-MM-DD`, tal como aparecen en el DM.
 *  - IDs alfanumericos con prefijo semantico (`pac_`, `rfid_`, `trt_`, `doc_`).
 *
 * Los campos que el onboarding no captura quedan nulos o vacios y se omiten al
 * serializar (ver [PacienteJson]), de modo que la app nunca envie ruido al
 * backend en Go.
 */
@Serializable
data class PacienteDto(
    val idPaciente: String? = null,
    val estadoCuenta: String? = null,
    val identificaciones: Identificaciones? = null,
    val dispositivosRfid: List<DispositivoRfid> = emptyList(),
    val datosPersonales: DatosPersonales? = null,
    val contactosEmergencia: List<ContactoEmergencia> = emptyList(),
    val perfilEmergenciaReducido: PerfilEmergenciaReducido? = null,
    val metricasVitalesActuales: MetricasVitalesActuales? = null,
    val historialClinico: HistorialClinico? = null,
    val tratamientosActivos: List<TratamientoActivo> = emptyList(),
    val registroAdherencia: List<RegistroAdherencia> = emptyList(),
    val controlAccesos: ControlAccesos? = null,
)

/**
 * `identificaciones`. `aseguradora` no aparece en el ejemplo del DM pero si en
 * el mapeo de la Fase 2 (pregunta 2), por lo que se modela como opcional.
 */
@Serializable
data class Identificaciones(
    val curp: String? = null,
    val nss: String? = null,
    val aseguradora: String? = null,
)

@Serializable
data class DispositivoRfid(
    val idTarjetaRfid: String,
    val estado: String,
    val fechaAsignacion: String,
    val fechaRevocacion: String? = null,
)

@Serializable
data class DatosPersonales(
    val nombre: String,
    val apellidos: String,
    /** Formato `YYYY-MM-DD`. */
    val fechaNacimiento: String,
    val genero: String,
    val telefono: String,
)

@Serializable
data class ContactoEmergencia(
    val nombre: String,
    val relacion: String,
    val telefono: String,
    /** 1 es el primer contacto al que se llama. */
    val prioridad: Int,
)

/**
 * Unico nodo que la API devuelve al escanear la tarjeta RFID
 * (DM_HistorialMedico.md, seccion 2: Segmentacion de Seguridad).
 */
@Serializable
data class PerfilEmergenciaReducido(
    val tipoSangre: String? = null,
    val donadorOrganos: Boolean? = null,
    val alergias: List<Alergia> = emptyList(),
    val condicionesCriticas: List<String> = emptyList(),
    val medicacionRescate: List<String> = emptyList(),
)

@Serializable
data class Alergia(
    val alergeno: String,
    val severidad: String,
    val reaccion: String,
)

@Serializable
data class MetricasVitalesActuales(
    val pesoKg: Double? = null,
    val alturaCm: Int? = null,
    /** Calculado por el cliente a partir de peso y altura. */
    val imc: Double? = null,
    /** Formato `sistolica/diastolica`, por ejemplo `120/80`. */
    val ultimaPresionArterial: String? = null,
    /** ISO 8601 UTC. */
    val fechaTomaMetricas: String? = null,
)

/**
 * `antecedentesHeredofamiliares` no aparece en el ejemplo del DM pero si en el
 * mapeo de la Fase 2 (pregunta 7); se modela como lista opcional.
 */
@Serializable
data class HistorialClinico(
    val cirugias: List<Cirugia> = emptyList(),
    val antecedentesHeredofamiliares: List<String> = emptyList(),
)

@Serializable
data class Cirugia(
    val procedimiento: String,
    /** Formato `YYYY-MM-DD`. */
    val fecha: String,
    val notas: String? = null,
)

@Serializable
data class TratamientoActivo(
    /** Asignado por el backend con prefijo `trt_`; nulo al darlo de alta. */
    val idTratamiento: String? = null,
    val medicamento: String,
    val dosis: String,
    val frecuenciaHoras: Int,
    val horariosSugeridos: List<String> = emptyList(),
    val viaAdministracion: String? = null,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val idMedicoReceta: String? = null,
    val inventario: InventarioTratamiento? = null,
)

@Serializable
data class InventarioTratamiento(
    val cantidadRestante: Int,
    val umbralAlerta: Int = UMBRAL_ALERTA_POR_DEFECTO,
) {
    companion object {
        /** Unidades restantes a partir de las cuales se avisa al paciente. */
        const val UMBRAL_ALERTA_POR_DEFECTO: Int = 5
    }
}

@Serializable
data class RegistroAdherencia(
    val idToma: String,
    val idTratamiento: String,
    val fechaHoraProgramada: String,
    val fechaHoraReal: String? = null,
    val estado: String,
    val sintomasAsociados: String? = null,
)

@Serializable
data class ControlAccesos(
    val medicosAutorizados: List<MedicoAutorizado> = emptyList(),
)

@Serializable
data class MedicoAutorizado(
    val idMedico: String,
    val nivelAcceso: String,
    val fechaExpiracion: String? = null,
)
