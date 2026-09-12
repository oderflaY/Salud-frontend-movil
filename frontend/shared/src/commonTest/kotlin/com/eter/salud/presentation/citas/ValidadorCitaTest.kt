package com.eter.salud.presentation.citas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reglas de los datos de contacto de la cita. Objeto puro, sin red ni UI. */
class ValidadorCitaTest {

    private fun validar(
        nombre: String = "Alfredo Valadez Gonzalez",
        telefono: String = "6181234567",
        correo: String = "alfredo@ejemplo.mx",
        motivo: String = "Dolor de cabeza desde hace tres dias",
    ) = ValidadorCita.validar(nombre, telefono, correo, motivo)

    @Test
    fun unos_datos_completos_no_producen_ningun_error() {
        assertTrue(validar().isEmpty())
    }

    @Test
    fun un_nombre_en_blanco_se_senala() {
        assertEquals(listOf(ErrorCampoCita.NOMBRE_VACIO), validar(nombre = "   "))
    }

    @Test
    fun el_telefono_admite_espacios_y_guiones_porque_asi_lo_escribe_la_gente() {
        assertTrue(validar(telefono = "618 123 45 67").isEmpty())
        assertTrue(validar(telefono = "(618) 123-4567").isEmpty())
    }

    @Test
    fun un_telefono_de_menos_de_diez_digitos_se_rechaza() {
        assertEquals(listOf(ErrorCampoCita.TELEFONO_FORMATO), validar(telefono = "618123"))
    }

    @Test
    fun un_telefono_sin_un_solo_digito_cuenta_como_vacio_y_no_como_formato() {
        // Distinguirlos importa: el mensaje que se muestra no es el mismo.
        assertEquals(listOf(ErrorCampoCita.TELEFONO_VACIO), validar(telefono = "sin numero"))
    }

    @Test
    fun un_correo_mal_formado_se_rechaza_con_la_misma_regla_que_el_acceso() {
        assertEquals(listOf(ErrorCampoCita.CORREO_FORMATO), validar(correo = "alfredo.ejemplo"))
    }

    @Test
    fun un_motivo_en_blanco_se_rechaza_porque_es_lo_que_el_medico_lee_antes_de_recibir() {
        assertEquals(listOf(ErrorCampoCita.MOTIVO_VACIO), validar(motivo = ""))
    }

    @Test
    fun varios_campos_malos_se_reportan_todos_a_la_vez() {
        val errores = validar(nombre = "", telefono = "", correo = "", motivo = "")

        assertEquals(
            listOf(
                ErrorCampoCita.NOMBRE_VACIO,
                ErrorCampoCita.TELEFONO_VACIO,
                ErrorCampoCita.CORREO_VACIO,
                ErrorCampoCita.MOTIVO_VACIO,
            ),
            errores,
        )
    }

    @Test
    fun solo_digitos_limpia_el_telefono_para_enviarlo_al_backend() {
        assertEquals("6181234567", ValidadorCita.soloDigitos("(618) 123-45-67"))
    }
}
