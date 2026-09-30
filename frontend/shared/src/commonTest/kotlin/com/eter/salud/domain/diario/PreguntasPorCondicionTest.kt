package com.eter.salud.domain.diario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lo que este modulo promete: que a un hipertenso se le pregunte por su
 * presion y a un asmatico por su inhalador. Si eso se rompe, el diario vuelve
 * a ser una hoja en blanco que todos contestan "bien".
 */
class PreguntasPorCondicionTest {

    private fun preguntasDe(vararg condiciones: String) =
        PreguntasPorCondicion.para(condiciones.toList()).map { it.pregunta }

    @Test
    fun a_un_hipertenso_se_le_pregunta_por_su_presion() {
        val preguntas = preguntasDe("Hipertension arterial")

        assertTrue(preguntas.any { it.contains("presion", ignoreCase = true) }, preguntas.toString())
    }

    @Test
    fun a_un_diabetico_por_su_glucosa_y_sus_pies() {
        val preguntas = preguntasDe("Diabetes tipo 2")

        assertTrue(preguntas.any { it.contains("glucosa", ignoreCase = true) }, preguntas.toString())
        assertTrue(preguntas.any { it.contains("pies", ignoreCase = true) }, preguntas.toString())
    }

    @Test
    fun a_quien_tiene_asma_por_el_inhalador_de_rescate() {
        val preguntas = preguntasDe("Asma reactiva")

        assertTrue(preguntas.any { it.contains("inhalador", ignoreCase = true) }, preguntas.toString())
    }

    @Test
    fun el_acento_y_las_mayusculas_no_cambian_la_condicion() {
        val conAcento = preguntasDe("Hipertensión Arterial")
        val sinAcento = preguntasDe("hipertension arterial")

        assertEquals(sinAcento, conAcento)
        assertTrue(conAcento.any { it.contains("presion", ignoreCase = true) })
    }

    @Test
    fun varias_condiciones_suman_las_preguntas_de_todas() {
        val preguntas = preguntasDe("Hipertension arterial", "Diabetes tipo 2")

        assertTrue(preguntas.any { it.contains("presion", ignoreCase = true) })
        assertTrue(preguntas.any { it.contains("glucosa", ignoreCase = true) })
    }

    @Test
    fun nunca_se_repite_una_pregunta_aunque_dos_condiciones_la_compartan() {
        val preguntas = PreguntasPorCondicion.para(listOf("Asma reactiva", "EPOC"))

        assertEquals(preguntas.map { it.clave }.distinct().size, preguntas.size)
    }

    @Test
    fun sin_condiciones_registradas_quedan_las_preguntas_generales() {
        val preguntas = PreguntasPorCondicion.para(emptyList())

        assertTrue(preguntas.isNotEmpty())
        assertTrue(preguntas.any { it.pregunta.contains("medicinas", ignoreCase = true) }, preguntas.toString())
    }

    @Test
    fun una_condicion_desconocida_no_deja_al_paciente_sin_preguntas() {
        val preguntas = PreguntasPorCondicion.para(listOf("Sindrome rarisimo no catalogado"))

        assertEquals(PreguntasPorCondicion.para(emptyList()), preguntas)
    }

    @Test
    fun las_generales_van_al_final_no_antes_que_las_de_su_condicion() {
        val preguntas = PreguntasPorCondicion.para(listOf("Hipertension arterial"))

        assertEquals("otra_cosa", preguntas.last().clave)
        assertTrue(preguntas.first().clave.startsWith("presion"), preguntas.first().clave)
    }

    @Test
    fun la_respuesta_empieza_a_medias_para_que_la_complete_el_paciente() {
        val presion = PreguntasPorCondicion.para(listOf("Hipertension arterial"))
            .first { it.clave == "presion_cifra" }

        // Un inicio que pide el dato, no una frase ya cerrada.
        assertTrue(presion.inicioDeRespuesta.endsWith(" "), presion.inicioDeRespuesta)
        assertFalse(presion.inicioDeRespuesta.endsWith("."), presion.inicioDeRespuesta)
    }

    @Test
    fun solo_se_reportan_como_reconocidas_las_condiciones_que_cambian_las_preguntas() {
        val reconocidas = PreguntasPorCondicion.condicionesReconocidas(
            listOf("Hipertension arterial", "Sindrome rarisimo no catalogado"),
        )

        assertEquals(listOf("Hipertension arterial"), reconocidas)
    }
}
