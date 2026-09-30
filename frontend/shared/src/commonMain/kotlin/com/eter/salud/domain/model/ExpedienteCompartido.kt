package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Los bloques del expediente que el paciente decide si comparte con su medico.
 *
 * El nombre y la fecha de nacimiento no estan: sin ellos el medico no sabe a
 * quien esta leyendo. Todo lo demas es del paciente, y el decide.
 *
 * [esVital] marca lo que un medico necesita para no hacer dano (recetar algo a
 * lo que eres alergico, duplicar un medicamento): ocultarlo esta permitido,
 * pero la pantalla lo advierte.
 */
@Serializable
enum class BloqueDeExpediente(val esVital: Boolean) {
    EMERGENCIA(esVital = true),
    TRATAMIENTOS(esVital = true),
    HISTORIAL_CLINICO(esVital = false),
    METRICAS(esVital = false),
    IDENTIFICACIONES(esVital = false),
    CONTACTOS(esVital = false),
}

/** Que tipo de estudio es; ordena la galeria y le da nombre a lo que el medico abre. */
@Serializable
enum class CategoriaDocumento {
    RADIOGRAFIA,
    LABORATORIO,
    RECETA,
    FOTO,
    OTRO,
}

/**
 * Una foto o un estudio que el paciente adjunto a su expediente: una
 * radiografia escaneada, un resultado de laboratorio, la foto de una lesion.
 *
 * [rutaLocal] apunta a la copia en el almacenamiento privado de la app (igual
 * que los adjuntos del chat): el archivo original del carrete puede borrarse y
 * el expediente no debe perder nada por eso.
 */
@Serializable
data class DocumentoClinico(
    val idDocumento: String,
    val titulo: String,
    val categoria: CategoriaDocumento,
    val nombreArchivo: String,
    val rutaLocal: String,
    val tipoMime: String,
    /** `YYYY-MM-DD`: el dia en que se adjunto. */
    val fecha: String,
    /** Si su medico lo puede ver. Nace visible: se adjunta para eso. */
    val visibleParaMedico: Boolean = true,
) {
    val esImagen: Boolean get() = tipoMime.startsWith("image/")
}

/**
 * Todo lo que el paciente decide sobre su expediente frente a su medico: que
 * bloques oculta y que documentos adjunto.
 */
@Serializable
data class ExpedienteCompartido(
    val bloquesOcultos: Set<BloqueDeExpediente> = emptySet(),
    val documentos: List<DocumentoClinico> = emptyList(),
) {
    fun muestra(bloque: BloqueDeExpediente): Boolean = bloque !in bloquesOcultos

    /** Lo que el medico puede abrir, del mas reciente al mas antiguo. */
    val documentosVisibles: List<DocumentoClinico>
        get() = documentos.filter { it.visibleParaMedico }.sortedByDescending { it.fecha }

    val vitalesOcultos: List<BloqueDeExpediente>
        get() = BloqueDeExpediente.entries.filter { it.esVital && it in bloquesOcultos }
}

/**
 * El expediente tal como lo vera el medico: los bloques ocultos se vacian, la
 * identidad basica queda siempre.
 *
 * Vaciar y no quitar el nodo: la pantalla del medico ya sabe pintar "sin
 * datos" para una seccion vacia, y asi no depende de cuantos bloques existan.
 */
fun PacienteDto.paraElMedico(compartido: ExpedienteCompartido): PacienteDto = copy(
    perfilEmergenciaReducido = perfilEmergenciaReducido.takeIf { compartido.muestra(BloqueDeExpediente.EMERGENCIA) },
    tratamientosActivos = tratamientosActivos.takeIf { compartido.muestra(BloqueDeExpediente.TRATAMIENTOS) }.orEmpty(),
    historialClinico = historialClinico.takeIf { compartido.muestra(BloqueDeExpediente.HISTORIAL_CLINICO) },
    metricasVitalesActuales = metricasVitalesActuales.takeIf { compartido.muestra(BloqueDeExpediente.METRICAS) },
    identificaciones = identificaciones.takeIf { compartido.muestra(BloqueDeExpediente.IDENTIFICACIONES) },
    contactosEmergencia = contactosEmergencia.takeIf { compartido.muestra(BloqueDeExpediente.CONTACTOS) }.orEmpty(),
)
