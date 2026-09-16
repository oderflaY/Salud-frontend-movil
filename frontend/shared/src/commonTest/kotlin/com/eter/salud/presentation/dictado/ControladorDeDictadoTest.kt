package com.eter.salud.presentation.dictado

import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.dictado.ErrorDictado
import com.eter.salud.domain.dictado.EscuchaDeDictado
import com.eter.salud.domain.dictado.ReconocedorDeVoz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ReconocedorFalso : ReconocedorDeVoz {
    override var disponible = true
    var escucha: EscuchaDeDictado? = null
    var detenido = false
    var cancelado = false

    override fun iniciar(escucha: EscuchaDeDictado) {
        this.escucha = escucha
        detenido = false
        cancelado = false
    }

    override fun detener() {
        detenido = true
    }

    override fun cancelar() {
        cancelado = true
    }
}

class ControladorDeDictadoTest {

    private val reconocedor = ReconocedorFalso()
    private var campo = ""
    private val controlador = ControladorDeDictado(reconocedor) { campo = it }

    private fun escucha() = reconocedor.escucha!!

    @Test
    fun el_texto_aparece_en_vivo_ya_procesado() {
        controlador.iniciar("")
        assertEquals(FaseDictado.PREPARANDO, controlador.estado.value.fase)
        escucha().alListo()
        assertEquals(FaseDictado.ESCUCHANDO, controlador.estado.value.fase)

        escucha().alParcial("me duele la cabeza coma")
        assertEquals("Me duele la cabeza,", campo)
        escucha().alSegmento("me duele la cabeza coma tengo treinta y ocho grados")
        assertEquals("Me duele la cabeza, tengo 38 °C", campo)
    }

    @Test
    fun al_detener_cierra_el_texto_y_agrega_el_resumen() {
        controlador.iniciar("")
        escucha().alListo()
        escucha().alSegmento("tengo fiebre de treinta y ocho y medio grados desde anoche")
        controlador.detener()
        assertTrue(reconocedor.detenido)
        assertEquals(FaseDictado.FINALIZANDO, controlador.estado.value.fase)

        escucha().alTerminar()
        assertEquals(FaseDictado.INACTIVO, controlador.estado.value.fase)
        assertEquals("Tengo fiebre de 38.5 °C desde anoche.\n\n— Resumen: Temperatura 38.5 °C · Inicio: desde anoche", campo)
    }

    @Test
    fun sin_resumen_si_se_apaga_la_opcion() {
        controlador.alternarResumen()
        controlador.iniciar("")
        escucha().alSegmento("fiebre de treinta y nueve grados")
        controlador.detener()
        escucha().alTerminar()
        assertEquals("Fiebre de 39 °C.", campo)
    }

    @Test
    fun continua_el_texto_que_ya_estaba_y_no_duplica_el_resumen() {
        controlador.iniciar("Buenos dias doctor.")
        escucha().alSegmento("hoy amaneci con fiebre de treinta y ocho grados")
        controlador.detener()
        escucha().alTerminar()
        val primero = campo
        assertTrue(primero.startsWith("Buenos dias doctor. Hoy amaneci con fiebre de 38 °C."))

        controlador.iniciar(primero)
        escucha().alSegmento("y me duele la cabeza")
        controlador.detener()
        escucha().alTerminar()
        assertEquals(1, Regex("Resumen").findAll(campo).count())
        assertTrue(campo.contains("Y me duele la cabeza."))
    }

    @Test
    fun detener_y_enviar_manda_el_texto_final() {
        var enviado: String? = null
        controlador.iniciar("")
        escucha().alSegmento("ya me tome la pastilla")
        controlador.detenerYEnviar { enviado = campo }
        assertNull(enviado, "No se envía antes de que llegue el texto final")
        escucha().alTerminar()
        assertEquals("Ya me tome la pastilla.", enviado)
    }

    @Test
    fun cancelar_deja_el_campo_como_estaba() {
        controlador.iniciar("Texto previo")
        escucha().alParcial("algo que no queria")
        controlador.cancelar()
        assertTrue(reconocedor.cancelado)
        assertEquals("Texto previo", campo)
        assertFalse(controlador.estado.value.activo)
    }

    @Test
    fun un_error_conserva_lo_dictado_pero_no_envia() {
        var enviado = false
        controlador.iniciar("")
        escucha().alSegmento("me siento mareado")
        controlador.detenerYEnviar { enviado = true }
        escucha().alFallar(ErrorDictado.SIN_CONEXION)
        assertFalse(enviado)
        assertEquals("Me siento mareado.", campo)
        assertEquals(ErrorDictado.SIN_CONEXION, controlador.estado.value.error)
        controlador.descartarError()
        assertNull(controlador.estado.value.error)
    }

    @Test
    fun el_triage_y_los_datos_se_calculan_en_vivo() {
        controlador.iniciar("")
        escucha().alParcial("tengo dolor en el pecho y la presion en ciento sesenta sobre cien")
        val estado = controlador.estado.value
        assertEquals(SeveridadDiario.ROJO, estado.triage.severidad)
        assertEquals("160/100", estado.resumen.presion)
    }

    @Test
    fun el_nivel_de_voz_se_escalona_para_no_recomponer_de_mas() {
        controlador.iniciar("")
        escucha().alNivel(0.43f)
        assertEquals(0.4f, controlador.estado.value.nivelDeVoz)
        escucha().alNivel(7f)
        assertEquals(1f, controlador.estado.value.nivelDeVoz)
    }

    @Test
    fun los_eventos_tardios_despues_de_terminar_se_ignoran() {
        controlador.iniciar("")
        escucha().alSegmento("hola doctor")
        controlador.detener()
        escucha().alTerminar()
        val final = campo
        escucha().alParcial("ruido tardio")
        escucha().alTerminar()
        assertEquals(final, campo)
    }
}
