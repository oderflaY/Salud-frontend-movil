package com.eter.salud.presentation.citas

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deteccion de la intencion de agendar.
 *
 * Es un cotejo de frases, no comprension de lenguaje. Estas pruebas fijan hasta
 * donde llega: reconoce las formas comunes de pedir cita, tolera acentos y
 * mayusculas, y NO se dispara con la palabra "cita" suelta, que aparece tambien
 * al hablar de una cita pasada.
 */
class DetectorIntencionCitaTest {

    @Test
    fun reconoce_las_formas_habituales_de_pedir_una_cita() {
        listOf(
            "Quiero agendar una cita",
            "Necesito ver a un doctor",
            "Quiero una cita con usted",
            "Me gustaria pedir cita para la proxima semana",
            "Necesito un medico urgentemente",
        ).forEach { frase ->
            assertTrue(DetectorIntencionCita.quiereAgendar(frase), frase)
        }
    }

    @Test
    fun tolera_mayusculas_y_acentos() {
        assertTrue(DetectorIntencionCita.quiereAgendar("QUIERO AGENDÁR UNA CITA"))
        assertTrue(DetectorIntencionCita.quiereAgendar("Necesito ver a un médico"))
    }

    @Test
    fun la_palabra_cita_por_si_sola_no_abre_el_flujo() {
        // Si bastara, hablar de una consulta pasada abriria un formulario encima
        // de la conversacion sin que nadie lo pidiera.
        assertFalse(DetectorIntencionCita.quiereAgendar("No pude ir a mi cita del martes"))
        assertFalse(DetectorIntencionCita.quiereAgendar("Gracias por la cita de ayer"))
    }

    @Test
    fun una_consulta_clinica_normal_no_se_confunde_con_una_peticion_de_cita() {
        listOf(
            "Me duele la cabeza desde ayer",
            "Puedo tomar paracetamol con este tratamiento",
            "Sigo con fiebre",
        ).forEach { frase ->
            assertFalse(DetectorIntencionCita.quiereAgendar(frase), frase)
        }
    }

    @Test
    fun un_texto_vacio_no_dispara_nada() {
        assertFalse(DetectorIntencionCita.quiereAgendar(""))
        assertFalse(DetectorIntencionCita.quiereAgendar("   "))
    }
}
