package com.eter.salud.presentation.registro

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reglas de creacion de cuenta. DM_Arquitectura_App.md, seccion 2: TDD. */
class ValidadorRegistroTest {

    private fun validar(
        correo: String = "paciente@correo.com",
        contrasena: String = "salud12345",
        confirmacion: String = "salud12345",
        avisoAceptado: Boolean = true,
    ) = ValidadorRegistro.validar(correo, contrasena, confirmacion, avisoAceptado)

    @Test
    fun un_formulario_completo_y_coherente_no_reporta_errores() {
        assertTrue(validar().isEmpty())
    }

    @Test
    fun un_formulario_vacio_reporta_todos_los_campos_obligatorios() {
        val errores = validar(correo = "", contrasena = "", confirmacion = "", avisoAceptado = false)

        assertContains(errores, ErrorCampoRegistro.CORREO_VACIO)
        assertContains(errores, ErrorCampoRegistro.CONTRASENA_VACIA)
        assertContains(errores, ErrorCampoRegistro.AVISO_NO_ACEPTADO)
    }

    @Test
    fun el_correo_se_valida_con_la_misma_regla_que_el_inicio_de_sesion() {
        assertEquals(
            listOf(ErrorCampoRegistro.CORREO_FORMATO),
            validar(correo = "paciente@correo"),
        )
    }

    @Test
    fun una_contrasena_solo_de_letras_o_solo_de_numeros_es_debil() {
        assertEquals(
            listOf(ErrorCampoRegistro.CONTRASENA_DEBIL),
            validar(contrasena = "saludsalud", confirmacion = "saludsalud"),
        )
        assertEquals(
            listOf(ErrorCampoRegistro.CONTRASENA_DEBIL),
            validar(contrasena = "1234567890", confirmacion = "1234567890"),
        )
    }

    @Test
    fun una_contrasena_corta_se_reporta_como_corta_y_no_como_debil() {
        val errores = validar(contrasena = "sal123", confirmacion = "sal123")

        assertEquals(listOf(ErrorCampoRegistro.CONTRASENA_CORTA), errores)
    }

    @Test
    fun la_confirmacion_que_no_coincide_se_reporta_aunque_la_contrasena_sea_valida() {
        val errores = validar(confirmacion = "salud54321")

        assertEquals(listOf(ErrorCampoRegistro.CONFIRMACION_NO_COINCIDE), errores)
    }

    @Test
    fun la_confirmacion_no_se_compara_hasta_que_la_contrasena_es_valida() {
        // Con la contrasena aun invalida, senalar la confirmacion solo estorba.
        val errores = validar(contrasena = "sal", confirmacion = "")

        assertEquals(listOf(ErrorCampoRegistro.CONTRASENA_CORTA), errores)
    }

    @Test
    fun sin_aceptar_el_aviso_de_privacidad_no_se_puede_crear_la_cuenta() {
        val errores = validar(avisoAceptado = false)

        assertEquals(listOf(ErrorCampoRegistro.AVISO_NO_ACEPTADO), errores)
    }
}
