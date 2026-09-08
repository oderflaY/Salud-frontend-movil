package com.eter.salud.domain.mapper

import com.eter.salud.domain.model.HistorialClinico
import com.eter.salud.domain.model.Identificaciones
import com.eter.salud.domain.model.InventarioTratamiento
import com.eter.salud.domain.model.MetricasVitalesActuales
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.presentation.onboarding.BorradorPaciente
import com.eter.salud.presentation.onboarding.BorradorTratamiento
import kotlin.math.round

/**
 * Traduce el borrador de la UI (todo texto) al DTO tipado del
 * DM_HistorialMedico.md. Aqui, y solo aqui, ocurre la conversion de tipos.
 *
 * Reglas:
 *  - Un nodo que el paciente no toco no se construye: viaja omitido, no nulo.
 *  - `perfilEmergenciaReducido` siempre se construye porque es el bloque que lee
 *    el paramedico; sus listas vacias significan "el paciente declaro que no tiene".
 *  - Los identificadores (`idPaciente`, `idTratamiento`) y los nodos de acceso
 *    medico los asigna el backend en Go, no el cliente.
 */
fun BorradorPaciente.aPacienteDto(
    reloj: RelojSalud,
    /** Solo lo lleva la Fase 2, que edita un expediente ya registrado. */
    idPaciente: String? = null,
): PacienteDto = PacienteDto(
    idPaciente = idPaciente,
    identificaciones = identificacionesODescartar(),
    datosPersonales = datosPersonalesODescartar(),
    contactosEmergencia = contactos,
    perfilEmergenciaReducido = PerfilEmergenciaReducido(
        tipoSangre = tipoSangre.trim().ifBlank { null },
        donadorOrganos = donadorOrganos,
        alergias = alergias,
        condicionesCriticas = condicionesCriticas,
        medicacionRescate = medicacionRescate,
    ),
    metricasVitalesActuales = metricasODescartar(reloj),
    historialClinico = historialODescartar(),
    tratamientosActivos = tratamientos.mapNotNull { it.aTratamientoActivo() },
)

private fun BorradorPaciente.identificacionesODescartar(): Identificaciones? {
    val curpLimpia = curp.trim().uppercase()
    val nssLimpio = nss.trim()
    val aseguradoraLimpia = aseguradora.trim()
    if (curpLimpia.isBlank() && nssLimpio.isBlank() && aseguradoraLimpia.isBlank()) return null
    return Identificaciones(
        curp = curpLimpia.ifBlank { null },
        nss = nssLimpio.ifBlank { null },
        aseguradora = aseguradoraLimpia.ifBlank { null },
    )
}

private fun BorradorPaciente.datosPersonalesODescartar(): DatosPersonales? {
    val campos = listOf(nombre, apellidos, fechaNacimiento, genero, telefono)
    if (campos.all { it.isBlank() }) return null
    return DatosPersonales(
        nombre = nombre.trim(),
        apellidos = apellidos.trim(),
        fechaNacimiento = fechaNacimiento.trim(),
        genero = genero.trim(),
        telefono = telefono.trim(),
    )
}

private fun BorradorPaciente.metricasODescartar(reloj: RelojSalud): MetricasVitalesActuales? {
    val peso = pesoKg.trim().replace(',', '.').toDoubleOrNull()
    val altura = alturaCm.trim().toIntOrNull()
    val presion = presionArterialODescartar()
    if (peso == null && altura == null && presion == null) return null
    return MetricasVitalesActuales(
        pesoKg = peso,
        alturaCm = altura,
        imc = calcularImc(peso, altura),
        ultimaPresionArterial = presion,
        fechaTomaMetricas = reloj.instanteActual(),
    )
}

private fun BorradorPaciente.presionArterialODescartar(): String? {
    val sistolica = presionSistolica.trim().toIntOrNull() ?: return null
    val diastolica = presionDiastolica.trim().toIntOrNull() ?: return null
    return "$sistolica/$diastolica"
}

/**
 * El nodo se construye tambien cuando el paciente declaro explicitamente que no
 * tiene cirugias ni antecedentes: un arreglo vacio le dice al medico "ya se
 * pregunto y no hay", mientras que el nodo ausente significa "sin capturar".
 */
private fun BorradorPaciente.historialODescartar(): HistorialClinico? {
    val declarado = sinCirugias || sinAntecedentes
    if (!declarado && cirugias.isEmpty() && antecedentesHeredofamiliares.isEmpty()) return null
    return HistorialClinico(
        cirugias = cirugias,
        antecedentesHeredofamiliares = antecedentesHeredofamiliares,
    )
}

/**
 * Un tratamiento sin frecuencia numerica no se envia: el backend calcula los
 * recordatorios a partir de ese entero y un texto libre romperia el contrato.
 */
private fun BorradorTratamiento.aTratamientoActivo(): TratamientoActivo? {
    val frecuencia = frecuenciaHoras.trim().toIntOrNull() ?: return null
    if (medicamento.isBlank() || dosis.isBlank()) return null
    val cantidad = cantidadRestante.trim().toIntOrNull()
    return TratamientoActivo(
        medicamento = medicamento.trim(),
        dosis = dosis.trim(),
        frecuenciaHoras = frecuencia,
        viaAdministracion = viaAdministracion.trim().ifBlank { null },
        inventario = cantidad?.let { InventarioTratamiento(cantidadRestante = it) },
    )
}

/** Indice de masa corporal redondeado a un decimal, como en el DM. */
internal fun calcularImc(pesoKg: Double?, alturaCm: Int?): Double? {
    if (pesoKg == null || alturaCm == null) return null
    if (pesoKg <= 0.0 || alturaCm <= 0) return null
    val alturaMetros = alturaCm / 100.0
    return round(pesoKg / (alturaMetros * alturaMetros) * 10) / 10
}
