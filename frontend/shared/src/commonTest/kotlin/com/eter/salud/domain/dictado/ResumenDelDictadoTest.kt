package com.eter.salud.domain.dictado

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResumenDelDictadoTest {

    private fun resumen(dictado: String) =
        ResumenDelDictado.extraer(ProcesadorDeDictado.cerrar(ProcesadorDeDictado.procesar(dictado)))

    @Test
    fun extrae_los_signos_vitales_que_se_dijeron() {
        val r = resumen(
            "tengo fiebre de treinta y ocho y medio grados coma mi presion esta en ciento treinta sobre ochenta y cinco " +
                "coma el pulso en ciento diez coma oxigenacion noventa y tres por ciento y la glucosa en ciento ochenta",
        )
        assertEquals("38.5 °C", r.temperatura)
        assertEquals("130/85", r.presion)
        assertEquals(110, r.pulso)
        assertEquals(93, r.saturacion)
        assertEquals(180, r.glucosa)
    }

    @Test
    fun extrae_la_intensidad_del_dolor_y_desde_cuando() {
        val r = resumen("me duele el pecho como un siete de diez desde hace tres dias")
        assertEquals(7, r.dolor)
        // "tres" se queda en letra (numero chico sin unidad), tal como se dijo.
        assertEquals("hace tres dias", r.desde)
        assertEquals("desde anoche", resumen("tengo tos desde anoche").desde)
        assertEquals("hace 2 horas", ResumenDelDictado.extraer("Empezó hace 2 horas.").desde)
    }

    @Test
    fun conserva_las_tildes_de_lo_que_se_dijo() {
        assertEquals("hace 3 días", ResumenDelDictado.extraer("Me duele desde hace 3 días.").desde)
    }

    @Test
    fun no_inventa_datos_ni_acepta_cifras_imposibles() {
        assertTrue(resumen("hoy me senti bien y sali a caminar").vacio)
        assertNull(resumen("mi temperatura es de ciento veinte").temperatura)
        assertNull(resumen("tengo 3 hijos de 10 años").presion)
        assertNull(ResumenDelDictado.extraer("Saturacion 300").saturacion)
    }

    @Test
    fun el_bloque_de_resumen_se_agrega_una_sola_vez() {
        val etiquetas = EtiquetasResumen()
        val texto = "Fiebre de 38.5 °C. Dolor 8 de 10."
        val r = ResumenDelDictado.extraer(texto)
        val con = ResumenDelDictado.conResumen(texto, r, etiquetas)
        assertEquals("Fiebre de 38.5 °C. Dolor 8 de 10.\n\n— Resumen: Temperatura 38.5 °C · Dolor 8/10", con)

        val otraVez = ResumenDelDictado.conResumen(con, ResumenDelDictado.extraer(con), etiquetas)
        assertEquals(con, otraVez)
        assertEquals(texto, ResumenDelDictado.conResumen(texto, ResumenClinico(), etiquetas))
    }
}
