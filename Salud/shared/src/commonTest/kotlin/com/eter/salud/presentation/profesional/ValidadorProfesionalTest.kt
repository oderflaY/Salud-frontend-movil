package com.eter.salud.presentation.profesional

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reglas del acceso y alta de personal medico. */
class ValidadorProfesionalTest {

    // ------------------------------------------------------------------- Login

    @Test
    fun un_formulario_de_acceso_vacio_reporta_los_dos_campos() {
        val errores = ValidadorLoginProfesional.validar(correo = "", contrasena = "")

        assertContains(errores, ErrorCampoLoginProfesional.CORREO_VACIO)
        assertContains(errores, ErrorCampoLoginProfesional.CONTRASENA_VACIA)
    }

    @Test
    fun un_correo_valido_y_una_contrasena_de_ocho_caracteres_no_reportan_errores() {
        val errores = ValidadorLoginProfesional.validar(
            correo = "dr.elena.ruiz@hospital.com",
            contrasena = "salud12345",
        )

        assertTrue(errores.isEmpty())
    }

    @Test
    fun una_contrasena_corta_se_reporta_en_el_acceso() {
        val errores = ValidadorLoginProfesional.validar(
            correo = "dr.elena.ruiz@hospital.com",
            contrasena = "1234",
        )

        assertEquals(listOf(ErrorCampoLoginProfesional.CONTRASENA_CORTA), errores)
    }

    // ----------------------------------------------------------------- Registro

    private fun formularioValido(
        correo: String = "dr.carlos@hospital.com",
        contrasena: String = "salud12345",
        confirmacion: String = "salud12345",
        nombre: String = "Carlos",
        apellidos: String = "Mendez",
        tratamiento: String = "Dr.",
        cedulaProfesional: String = "87654321",
        avisoAceptado: Boolean = true,
    ) = ValidadorRegistroProfesional.validar(
        correo, contrasena, confirmacion, nombre, apellidos, tratamiento, cedulaProfesional,
        avisoAceptado,
    )

    @Test
    fun un_formulario_de_alta_completo_y_coherente_no_reporta_errores() {
        assertTrue(formularioValido().isEmpty())
    }

    @Test
    fun el_nombre_los_apellidos_y_el_tratamiento_son_obligatorios() {
        val errores = formularioValido(nombre = "", apellidos = "", tratamiento = "")

        assertContains(errores, ErrorCampoRegistroProfesional.NOMBRE_VACIO)
        assertContains(errores, ErrorCampoRegistroProfesional.APELLIDOS_VACIO)
        assertContains(errores, ErrorCampoRegistroProfesional.TRATAMIENTO_VACIO)
    }

    @Test
    fun la_cedula_profesional_vacia_se_reporta_como_faltante() {
        val errores = formularioValido(cedulaProfesional = "   ")

        assertContains(errores, ErrorCampoRegistroProfesional.CEDULA_VACIA)
    }

    @Test
    fun la_cedula_profesional_debe_ser_numerica_de_seis_a_ocho_digitos() {
        assertContains(
            formularioValido(cedulaProfesional = "123"),
            ErrorCampoRegistroProfesional.CEDULA_FORMATO,
        )
        assertContains(
            formularioValido(cedulaProfesional = "123456789"),
            ErrorCampoRegistroProfesional.CEDULA_FORMATO,
        )
        assertContains(
            formularioValido(cedulaProfesional = "1234ABCD"),
            ErrorCampoRegistroProfesional.CEDULA_FORMATO,
        )
    }

    @Test
    fun una_cedula_de_seis_digitos_en_el_limite_inferior_es_valida() {
        assertTrue(formularioValido(cedulaProfesional = "123456").isEmpty())
    }

    @Test
    fun una_contrasena_solo_de_letras_es_debil() {
        val errores = formularioValido(contrasena = "saludsalud", confirmacion = "saludsalud")

        assertEquals(listOf(ErrorCampoRegistroProfesional.CONTRASENA_DEBIL), errores)
    }

    @Test
    fun la_confirmacion_que_no_coincide_se_reporta_aunque_la_contrasena_sea_valida() {
        val errores = formularioValido(confirmacion = "otraDistinta9")

        assertEquals(listOf(ErrorCampoRegistroProfesional.CONFIRMACION_NO_COINCIDE), errores)
    }

    @Test
    fun sin_aceptar_el_aviso_de_privacidad_no_se_puede_crear_la_cuenta() {
        val errores = formularioValido(avisoAceptado = false)

        assertEquals(listOf(ErrorCampoRegistroProfesional.AVISO_NO_ACEPTADO), errores)
    }
}
