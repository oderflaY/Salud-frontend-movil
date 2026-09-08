package com.eter.salud.domain.mapper

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.Cirugia
import com.eter.salud.domain.model.ContactoEmergencia
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PacienteJson
import com.eter.salud.presentation.onboarding.BorradorPaciente
import com.eter.salud.presentation.onboarding.BorradorTratamiento
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de integracion del mapeo borrador -> DTO -> JSON.
 *
 * Verifican que el payload que sale de la app respete los tipos exactos del
 * DM_HistorialMedico.md: los numeros viajan como numeros, los booleanos como
 * booleanos y las fechas en ISO 8601.
 */
class BorradorPacienteMapperTest {

    private val reloj = RelojFijo(fecha = "2026-09-05", instante = "2026-09-05T08:00:00Z")

    private fun borradorFase1() = BorradorPaciente(
        nombre = "Juan",
        apellidos = "Perez Gomez",
        fechaNacimiento = "1985-04-12",
        genero = "Masculino",
        telefono = "+526181234567",
        tipoSangre = "O+",
        alergias = listOf(
            Alergia("Penicilina", "Alta (Anafilaxia)", "Cierre de vias respiratorias"),
        ),
        condicionesCriticas = listOf("Hipertension arterial"),
        medicacionRescate = listOf("Anticoagulantes activos"),
        tratamientos = listOf(
            BorradorTratamiento(
                medicamento = "Losartan",
                dosis = "50mg",
                frecuenciaHoras = "12",
                cantidadRestante = "14",
                viaAdministracion = "Oral",
            ),
        ),
        contactos = listOf(
            ContactoEmergencia("Maria Gomez", "Madre", "+526189876543", 1),
        ),
    )

    private fun raiz(borrador: BorradorPaciente): JsonObject {
        val crudo = PacienteJson.encodeToString(
            PacienteDto.serializer(),
            borrador.aPacienteDto(reloj),
        )
        return PacienteJson.parseToJsonElement(crudo).jsonObject
    }

    // ---------------------------------------------------------------- Fase 1

    @Test
    fun los_datos_personales_caen_en_su_nodo_con_la_fecha_en_formato_de_calendario() {
        val personales = raiz(borradorFase1())["datosPersonales"]!!.jsonObject

        assertEquals("Juan", personales["nombre"]!!.jsonPrimitive.content)
        assertEquals("Perez Gomez", personales["apellidos"]!!.jsonPrimitive.content)
        assertEquals("1985-04-12", personales["fechaNacimiento"]!!.jsonPrimitive.content)
        assertEquals("Masculino", personales["genero"]!!.jsonPrimitive.content)
        assertEquals("+526181234567", personales["telefono"]!!.jsonPrimitive.content)
    }

    @Test
    fun la_alergia_conserva_alergeno_severidad_y_reaccion() {
        val alergia = raiz(borradorFase1())["perfilEmergenciaReducido"]!!
            .jsonObject["alergias"]!!.jsonArray.first().jsonObject

        assertEquals("Penicilina", alergia["alergeno"]!!.jsonPrimitive.content)
        assertEquals("Alta (Anafilaxia)", alergia["severidad"]!!.jsonPrimitive.content)
        assertEquals("Cierre de vias respiratorias", alergia["reaccion"]!!.jsonPrimitive.content)
    }

    @Test
    fun la_frecuencia_y_el_inventario_viajan_como_enteros_no_como_texto() {
        val tratamiento = raiz(borradorFase1())["tratamientosActivos"]!!
            .jsonArray.first().jsonObject

        val frecuencia = tratamiento["frecuenciaHoras"]!!.jsonPrimitive
        assertFalse(frecuencia.isString, "frecuenciaHoras debe serializarse como numero")
        assertEquals(12, frecuencia.content.toInt())

        val cantidad = tratamiento["inventario"]!!.jsonObject["cantidadRestante"]!!.jsonPrimitive
        assertFalse(cantidad.isString, "cantidadRestante debe serializarse como numero")
        assertEquals(14, cantidad.content.toInt())
    }

    @Test
    fun el_inventario_recibe_el_umbral_de_alerta_por_defecto() {
        val inventario = raiz(borradorFase1())["tratamientosActivos"]!!
            .jsonArray.first().jsonObject["inventario"]!!.jsonObject

        assertEquals(5, inventario["umbralAlerta"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun la_prioridad_del_contacto_viaja_como_entero() {
        val contacto = raiz(borradorFase1())["contactosEmergencia"]!!
            .jsonArray.first().jsonObject

        val prioridad = contacto["prioridad"]!!.jsonPrimitive
        assertFalse(prioridad.isString, "prioridad debe serializarse como numero")
        assertEquals(1, prioridad.content.toInt())
    }

    @Test
    fun declarar_que_no_hay_alergias_produce_una_lista_vacia_no_un_nulo() {
        val borrador = borradorFase1().copy(alergias = emptyList(), sinAlergiasConocidas = true)

        val emergencia = raiz(borrador)["perfilEmergenciaReducido"]!!.jsonObject

        assertTrue(emergencia["alergias"]!!.jsonArray.isEmpty())
    }

    @Test
    fun desconocer_el_tipo_de_sangre_omite_la_clave_en_lugar_de_enviar_nulo() {
        val borrador = borradorFase1().copy(tipoSangre = "", desconoceTipoSangre = true)

        val emergencia = raiz(borrador)["perfilEmergenciaReducido"]!!.jsonObject

        assertNull(emergencia["tipoSangre"])
    }

    @Test
    fun los_nodos_reservados_al_backend_no_se_envian_desde_el_onboarding() {
        val json = raiz(borradorFase1())

        assertNull(json["idPaciente"])
        assertNull(json["controlAccesos"])
        assertTrue(json["registroAdherencia"]!!.jsonArray.isEmpty())
        assertTrue(json["dispositivosRfid"]!!.jsonArray.isEmpty())
        assertNull(json["tratamientosActivos"]!!.jsonArray.first().jsonObject["idTratamiento"])
    }

    // ---------------------------------------------------------------- Fase 2

    @Test
    fun el_imc_se_calcula_a_partir_del_peso_y_la_altura() {
        val borrador = borradorFase1().copy(pesoKg = "78.5", alturaCm = "175")

        val metricas = raiz(borrador)["metricasVitalesActuales"]!!.jsonObject

        assertEquals(78.5, metricas["pesoKg"]!!.jsonPrimitive.content.toDouble())
        assertEquals(175, metricas["alturaCm"]!!.jsonPrimitive.content.toInt())
        assertEquals(25.6, metricas["imc"]!!.jsonPrimitive.content.toDouble())
        assertFalse(metricas["pesoKg"]!!.jsonPrimitive.isString)
        assertFalse(metricas["alturaCm"]!!.jsonPrimitive.isString)
    }

    @Test
    fun las_metricas_se_sellan_con_la_fecha_de_toma_en_iso_8601_utc() {
        val borrador = borradorFase1().copy(pesoKg = "78.5", alturaCm = "175")

        val metricas = raiz(borrador)["metricasVitalesActuales"]!!.jsonObject

        assertEquals(
            "2026-09-05T08:00:00Z",
            metricas["fechaTomaMetricas"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun sin_peso_ni_altura_no_se_envia_el_nodo_de_metricas() {
        val json = raiz(borradorFase1())

        assertNull(json["metricasVitalesActuales"])
    }

    @Test
    fun la_presion_arterial_se_arma_como_sistolica_sobre_diastolica() {
        val borrador = borradorFase1().copy(
            presionSistolica = "120",
            presionDiastolica = "80",
        )

        val metricas = raiz(borrador)["metricasVitalesActuales"]!!.jsonObject

        assertEquals("120/80", metricas["ultimaPresionArterial"]!!.jsonPrimitive.content)
    }

    @Test
    fun el_donador_de_organos_viaja_como_booleano() {
        val borrador = borradorFase1().copy(donadorOrganos = true)

        val donador = raiz(borrador)["perfilEmergenciaReducido"]!!
            .jsonObject["donadorOrganos"]!!.jsonPrimitive

        assertFalse(donador.isString, "donadorOrganos debe serializarse como booleano")
        assertEquals("true", donador.content)
    }

    @Test
    fun las_identificaciones_incluyen_curp_nss_y_aseguradora() {
        val borrador = borradorFase1().copy(
            curp = "PAGJ850412HDFRXX09",
            nss = "12345678901",
            aseguradora = "IMSS",
        )

        val identificaciones = raiz(borrador)["identificaciones"]!!.jsonObject

        assertEquals("PAGJ850412HDFRXX09", identificaciones["curp"]!!.jsonPrimitive.content)
        assertEquals("12345678901", identificaciones["nss"]!!.jsonPrimitive.content)
        assertEquals("IMSS", identificaciones["aseguradora"]!!.jsonPrimitive.content)
    }

    @Test
    fun el_historial_clinico_recibe_cirugias_y_antecedentes_heredofamiliares() {
        val borrador = borradorFase1().copy(
            cirugias = listOf(Cirugia("Apendicectomia", "2015-08-20", "Sin complicaciones")),
            antecedentesHeredofamiliares = listOf("Diabetes tipo 2 en linea materna"),
        )

        val historial = raiz(borrador)["historialClinico"]!!.jsonObject
        val cirugia = historial["cirugias"]!!.jsonArray.first().jsonObject

        assertEquals("Apendicectomia", cirugia["procedimiento"]!!.jsonPrimitive.content)
        assertEquals("2015-08-20", cirugia["fecha"]!!.jsonPrimitive.content)
        assertEquals("Sin complicaciones", cirugia["notas"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("Diabetes tipo 2 en linea materna"),
            historial["antecedentesHeredofamiliares"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun sin_datos_de_fase_dos_no_se_envian_los_nodos_del_medico_tratante() {
        val json = raiz(borradorFase1())

        assertNull(json["identificaciones"])
        assertNull(json["historialClinico"])
    }

    @Test
    fun el_json_se_puede_volver_a_leer_como_dto_sin_perdida_de_datos() {
        val borrador = borradorFase1().copy(
            curp = "PAGJ850412HDFRXX09",
            pesoKg = "78.5",
            alturaCm = "175",
            donadorOrganos = false,
        )
        val original = borrador.aPacienteDto(reloj)

        val ida = PacienteJson.encodeToString(PacienteDto.serializer(), original)
        val vuelta = PacienteJson.decodeFromString(PacienteDto.serializer(), ida)

        assertEquals(original, vuelta)
    }
}
