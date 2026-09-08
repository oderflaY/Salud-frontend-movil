package com.eter.salud.presentation.login

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reglas de acceso. DM_Arquitectura_App.md, seccion 2: TDD obligatorio. */
class ValidadorLoginTest {

    @Test
    fun un_formulario_vacio_reporta_los_dos_campos() {
        val errores = ValidadorLogin.validar(correo = "", contrasena = "")

        assertContains(errores, ErrorCampoLogin.CORREO_VACIO)
        assertContains(errores, ErrorCampoLogin.CONTRASENA_VACIA)
    }

    @Test
    fun un_correo_sin_arroba_ni_dominio_se_rechaza() {
        val sinArroba = ValidadorLogin.validar("pacientecorreo.com", "12345678")
        val sinDominio = ValidadorLogin.validar("paciente@correo", "12345678")

        assertEquals(listOf(ErrorCampoLogin.CORREO_FORMATO), sinArroba)
        assertEquals(listOf(ErrorCampoLogin.CORREO_FORMATO), sinDominio)
    }

    @Test
    fun un_correo_con_espacios_alrededor_sigue_siendo_valido() {
        val errores = ValidadorLogin.validar("  paciente@correo.com  ", "12345678")

        assertTrue(errores.isEmpty())
    }

    @Test
    fun una_contrasena_mas_corta_que_el_minimo_se_rechaza() {
        val errores = ValidadorLogin.validar("paciente@correo.com", "1234567")

        assertEquals(listOf(ErrorCampoLogin.CONTRASENA_CORTA), errores)
    }

    @Test
    fun la_contrasena_no_se_recorta_porque_los_espacios_son_caracteres_validos() {
        // Ocho caracteres contando los espacios: cumple el minimo.
        val errores = ValidadorLogin.validar("paciente@correo.com", "  1234  ")

        assertTrue(errores.isEmpty())
    }

    @Test
    fun el_correo_se_normaliza_a_minusculas_antes_de_salir_a_la_red() {
        assertEquals(
            "paciente@correo.com",
            ValidadorLogin.normalizarCorreo("  Paciente@Correo.COM "),
        )
    }
}
