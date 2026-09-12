package com.eter.salud.domain.mapper

import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.presentation.onboarding.BorradorPaciente
import com.eter.salud.presentation.onboarding.BorradorTratamiento

/**
 * Hidratacion inversa: del DTO que devuelve la API al borrador editable.
 *
 * La Fase 2 edita un expediente que YA existe, asi que la pantalla de ajustes
 * debe arrancar con lo capturado en la Fase 1. Sin esta traduccion, guardar una
 * seccion sobrescribiria el resto del expediente con campos en blanco.
 *
 * Regla de lectura de ausencias: una lista vacia dentro de un nodo que el
 * backend SI devolvio significa "el paciente declaro que no tiene"; un nodo
 * ausente significa "todavia no se pregunto". Esa diferencia es la que
 * alimenta las banderas `sin...` del borrador.
 */
fun PacienteDto.aBorradorPaciente(): BorradorPaciente {
    val emergencia = perfilEmergenciaReducido
    val historial = historialClinico
    val presion = metricasVitalesActuales?.ultimaPresionArterial.orEmpty().split('/')
    return BorradorPaciente(
        nombre = datosPersonales?.nombre.orEmpty(),
        apellidos = datosPersonales?.apellidos.orEmpty(),
        fechaNacimiento = datosPersonales?.fechaNacimiento.orEmpty(),
        genero = datosPersonales?.genero.orEmpty(),
        telefono = datosPersonales?.telefono.orEmpty(),

        tipoSangre = emergencia?.tipoSangre.orEmpty(),
        desconoceTipoSangre = emergencia != null && emergencia.tipoSangre.isNullOrBlank(),
        alergias = emergencia?.alergias.orEmpty(),
        sinAlergiasConocidas = emergencia != null && emergencia.alergias.isEmpty(),
        condicionesCriticas = emergencia?.condicionesCriticas.orEmpty(),
        sinCondicionesCriticas = emergencia != null && emergencia.condicionesCriticas.isEmpty(),
        medicacionRescate = emergencia?.medicacionRescate.orEmpty(),
        sinMedicacionRescate = emergencia != null && emergencia.medicacionRescate.isEmpty(),

        // `tratamientosActivos` viaja siempre como arreglo: el DTO no distingue
        // "sin tratamientos" de "no preguntado", por eso la bandera queda en falso
        // y el paciente vuelve a declararlo si edita esa seccion.
        tratamientos = tratamientosActivos.map { it.aBorradorTratamiento() },
        contactos = contactosEmergencia,

        curp = identificaciones?.curp.orEmpty(),
        nss = identificaciones?.nss.orEmpty(),
        aseguradora = identificaciones?.aseguradora.orEmpty(),
        donadorOrganos = emergencia?.donadorOrganos,
        pesoKg = metricasVitalesActuales?.pesoKg?.toString().orEmpty(),
        alturaCm = metricasVitalesActuales?.alturaCm?.toString().orEmpty(),
        presionSistolica = presion.getOrNull(0).orEmpty().trim(),
        presionDiastolica = presion.getOrNull(1).orEmpty().trim(),
        cirugias = historial?.cirugias.orEmpty(),
        sinCirugias = historial != null && historial.cirugias.isEmpty(),
        antecedentesHeredofamiliares = historial?.antecedentesHeredofamiliares.orEmpty(),
        sinAntecedentes = historial != null && historial.antecedentesHeredofamiliares.isEmpty(),
    )
}

private fun TratamientoActivo.aBorradorTratamiento() = BorradorTratamiento(
    medicamento = medicamento,
    dosis = dosis,
    frecuenciaHoras = frecuenciaHoras.toString(),
    cantidadRestante = inventario?.cantidadRestante?.toString().orEmpty(),
    viaAdministracion = viaAdministracion.orEmpty(),
)
