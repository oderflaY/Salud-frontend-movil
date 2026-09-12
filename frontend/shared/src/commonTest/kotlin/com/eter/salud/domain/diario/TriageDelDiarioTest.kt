package com.eter.salud.domain.diario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Motor de triage del diario.
 *
 * Estas pruebas no comprueban una pantalla: fijan el criterio con el que se
 * ordena la bandeja de un medico. Lo que mas importa aqui es la asimetria de los
 * errores -- ante la duda, mas grave -- y que el cotejo no se dispare con
 * palabras que solo CONTIENEN un termino vigilado.
 */
class TriageDelDiarioTest {

    @Test
    fun un_dia_bueno_sale_en_verde() {
        listOf(
            "Hoy me senti bien, sali a caminar media hora.",
            "Dormi de corrido y desayune normal.",
            "",
        ).forEach { texto ->
            assertEquals(SeveridadDiario.VERDE, TriageDelDiario.clasificar(texto), texto)
        }
    }

    @Test
    fun un_solo_sintoma_moderado_sube_a_ambar_pero_no_a_rojo() {
        assertEquals(SeveridadDiario.AMBAR, TriageDelDiario.clasificar("Tuve algo de mareo al levantarme"))
    }

    @Test
    fun un_solo_termino_de_urgencia_ya_es_rojo() {
        // Diccionario v2: la categoria "urgencia" no espera a que se acumulen
        // hallazgos. Un solo "sangre" o "desmayo" ya ordena la bandeja arriba.
        assertEquals(SeveridadDiario.ROJO, TriageDelDiario.clasificar("Vi un poco de sangre"))
        assertEquals(SeveridadDiario.ROJO, TriageDelDiario.clasificar("Ayer tuve un desmayo"))
    }

    @Test
    fun dos_terminos_urgentes_disparan_rojo() {
        val texto = "Siento opresion en el pecho y no puedo respirar bien"

        assertEquals(SeveridadDiario.ROJO, TriageDelDiario.clasificar(texto))
    }

    @Test
    fun tres_sintomas_moderados_tambien_dibujan_un_cuadro_rojo() {
        val texto = "Tengo fiebre, mareo y vomito desde anoche"

        assertEquals(SeveridadDiario.ROJO, TriageDelDiario.clasificar(texto))
    }

    @Test
    fun el_cotejo_es_por_palabra_completa_y_no_por_subcadena() {
        // Sin esta regla, "tos" marcaria "estos" y "todos", y el diario entero
        // saldria en ambar.
        assertEquals(SeveridadDiario.VERDE, TriageDelDiario.clasificar("Todos estos dias han sido buenos"))
    }

    @Test
    fun tolera_acentos_y_mayusculas() {
        assertEquals(
            SeveridadDiario.ROJO,
            TriageDelDiario.clasificar("OPRESIÓN en el pecho y VISIÓN BORROSA"),
        )
    }

    @Test
    fun un_termino_moderado_dentro_de_uno_urgente_no_cuenta_dos_veces() {
        // "dolor en el pecho" no debe sumar ademas "dolor": una sola frase
        // valdria por dos hallazgos y saltaria a rojo sola.
        val resultado = TriageDelDiario.evaluar("Tengo dolor en el pecho")

        assertEquals(listOf("dolor en el pecho"), resultado.palabrasDetectadas)
    }

    @Test
    fun los_terminos_detectados_se_pueden_mostrar_al_medico() {
        // El semaforo ordena, pero el medico tiene que poder ver POR QUE.
        val detectados = TriageDelDiario.terminosDetectados("Fiebre alta y mucho mareo")

        assertTrue("fiebre" in detectados)
        assertTrue("mareo" in detectados)
    }

    // ------------------------------------------------ Diccionario v2 (categorias)

    @Test
    fun la_urgencia_mental_es_roja_y_pide_la_linea_de_crisis() {
        val resultado = TriageDelDiario.evaluar("A veces pienso en el suicidio")

        assertEquals(SeveridadDiario.ROJO, resultado.severidad)
        assertTrue(CategoriaTriage.URGENCIA_MENTAL in resultado.categorias)
        assertTrue(resultado.requiereLineaDeCrisis)
    }

    @Test
    fun la_precaucion_mental_es_ambar_sin_linea_de_crisis() {
        val resultado = TriageDelDiario.evaluar("Tengo mucha ansiedad por las noches")

        assertEquals(SeveridadDiario.AMBAR, resultado.severidad)
        assertEquals(setOf(CategoriaTriage.PRECAUCION_MENTAL), resultado.categorias)
        assertFalse(resultado.requiereLineaDeCrisis)
    }

    @Test
    fun devuelve_las_palabras_exactas_en_su_forma_legible() {
        val resultado = TriageDelDiario.evaluar("Senti OPRESION y luego mareo")

        assertEquals(listOf("opresión", "mareo"), resultado.palabrasDetectadas)
        assertEquals(
            setOf(CategoriaTriage.URGENCIA_FISICA, CategoriaTriage.PRECAUCION_FISICA),
            resultado.categorias,
        )
    }

    @Test
    fun la_palabra_completa_protege_tambien_a_los_terminos_nuevos() {
        // "morir" no puede disparar con "amortiguar" ni "pánico" con "panicos"
        // inventados: solo la palabra entera cuenta.
        listOf("Estos dias uso un cojin para amortiguar", "Me gusta el fiebrero de mi pueblo").forEach {
            assertEquals(SeveridadDiario.VERDE, TriageDelDiario.clasificar(it), it)
        }
    }

    @Test
    fun un_termino_contenido_en_otro_ya_hallado_no_se_repite() {
        val resultado = TriageDelDiario.evaluar("Tuve un ataque de pánico")

        assertEquals(listOf("ataque de pánico"), resultado.palabrasDetectadas)
        assertEquals(SeveridadDiario.AMBAR, resultado.severidad)
    }

    @Test
    fun el_codigo_persistido_es_estable_y_un_codigo_raro_se_lee_rojo() {
        assertEquals(0, SeveridadDiario.VERDE.codigo)
        assertEquals(1, SeveridadDiario.AMBAR.codigo)
        assertEquals(2, SeveridadDiario.ROJO.codigo)
        assertEquals(SeveridadDiario.ROJO, SeveridadDiario.desdeCodigo(99))
    }
}
