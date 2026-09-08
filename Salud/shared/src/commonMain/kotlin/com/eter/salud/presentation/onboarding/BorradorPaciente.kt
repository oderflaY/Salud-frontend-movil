package com.eter.salud.presentation.onboarding

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.Cirugia
import com.eter.salud.domain.model.ContactoEmergencia

/**
 * Borrador editable del perfil. Guarda las respuestas tal como las escribe el
 * paciente (texto crudo); la conversion a los tipos exactos del DTO ocurre en
 * `BorradorPacienteMapper`.
 *
 * Las banderas `sin...` distinguen "todavia no contesto" de "contesto que no
 * tiene", diferencia clinicamente relevante en el perfil de emergencia.
 */
data class BorradorPaciente(
    // Fase 1 - identidad
    val nombre: String = "",
    val apellidos: String = "",
    val fechaNacimiento: String = "",
    val genero: String = "",
    val telefono: String = "",

    // Fase 1 - perfil de emergencia
    val tipoSangre: String = "",
    val desconoceTipoSangre: Boolean = false,
    val alergias: List<Alergia> = emptyList(),
    val sinAlergiasConocidas: Boolean = false,
    val condicionesCriticas: List<String> = emptyList(),
    val sinCondicionesCriticas: Boolean = false,
    val medicacionRescate: List<String> = emptyList(),
    val sinMedicacionRescate: Boolean = false,

    // Fase 1 - inventario y contactos
    val tratamientos: List<BorradorTratamiento> = emptyList(),
    val sinTratamientos: Boolean = false,
    val contactos: List<ContactoEmergencia> = emptyList(),

    // Fase 2 - historial profundo
    val curp: String = "",
    val nss: String = "",
    val aseguradora: String = "",
    val donadorOrganos: Boolean? = null,
    val pesoKg: String = "",
    val alturaCm: String = "",
    val presionSistolica: String = "",
    val presionDiastolica: String = "",
    val cirugias: List<Cirugia> = emptyList(),
    val sinCirugias: Boolean = false,
    val antecedentesHeredofamiliares: List<String> = emptyList(),
    val sinAntecedentes: Boolean = false,
)

/** Tratamiento en captura: numeros aun como texto porque vienen del teclado. */
data class BorradorTratamiento(
    val medicamento: String,
    val dosis: String,
    val frecuenciaHoras: String,
    val cantidadRestante: String = "",
    val viaAdministracion: String = "",
)
