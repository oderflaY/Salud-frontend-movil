package com.eter.salud.domain.dictado

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lo que la persona dicta tiene que llegar al medico legible y SIN cambiarle el
 * sentido. Por eso casi la mitad de estas pruebas son de lo que NO se debe
 * convertir: "a punto de vomitar" no lleva un punto en medio, y "entro en coma"
 * no es una coma.
 */
class ProcesadorDeDictadoTest {

    private fun cerrado(texto: String) = ProcesadorDeDictado.cerrar(ProcesadorDeDictado.procesar(texto))

    @Test
    fun la_puntuacion_dictada_se_vuelve_signos() {
        assertEquals(
            "Me duele la cabeza, tengo nauseas. Desde ayer.",
            cerrado("me duele la cabeza coma tengo nauseas punto desde ayer"),
        )
        assertEquals("Tomo metformina; a veces se me olvida.", cerrado("tomo metformina punto y coma a veces se me olvida"))
        assertEquals("Sintomas: mareo y sed.", cerrado("sintomas dos puntos mareo y sed"))
    }

    @Test
    fun punto_y_aparte_abre_un_parrafo_nuevo() {
        assertEquals(
            "Hoy dormi mal.\n\nMañana tengo cita.",
            ProcesadorDeDictado.cerrar(ProcesadorDeDictado.procesar("hoy dormi mal punto y aparte mañana tengo cita")),
        )
    }

    @Test
    fun una_pregunta_dictada_lleva_signos_de_apertura_y_cierre() {
        assertEquals("¿Debo tomarla con el desayuno?", cerrado("debo tomarla con el desayuno signo de interrogación"))
        assertEquals("Gracias. ¿Me puede llamar?", cerrado("gracias punto me puede llamar signo de pregunta"))
    }

    @Test
    fun punto_y_coma_no_se_convierten_cuando_son_palabras_con_sentido() {
        assertEquals("Estoy a punto de vomitar.", cerrado("estoy a punto de vomitar"))
        assertEquals("Mi abuelo entro en coma el lunes.", cerrado("mi abuelo entro en coma el lunes"))
        assertEquals("Tengo un punto de dolor aqui.", cerrado("tengo un punto de dolor aqui"))
        assertEquals("My period is late.", cerrado("my period is late"))
    }

    @Test
    fun los_numeros_con_medida_pasan_a_cifras_y_unidades() {
        assertEquals("Tengo 38.5 °C de fiebre.", cerrado("tengo treinta y ocho y medio grados de fiebre"))
        assertEquals("Mi presion esta en 120/80.", cerrado("mi presion esta en ciento veinte sobre ochenta"))
        assertEquals("Oxigenacion 93 %.", cerrado("oxigenacion noventa y tres por ciento"))
        assertEquals("Me tome 500 mg.", cerrado("me tome quinientos miligramos"))
        assertEquals("Temperatura 37.8 °C.", cerrado("temperatura treinta y siete punto ocho grados"))
        assertEquals("Pulso de 110 lpm.", cerrado("pulso de ciento diez latidos por minuto"))
    }

    @Test
    fun las_cifras_que_ya_trae_el_reconocedor_tambien_llevan_unidad() {
        assertEquals("Tengo 39 °C.", cerrado("tengo 39 grados"))
        assertEquals("Presion 135/90.", cerrado("presion 135 sobre 90"))
    }

    @Test
    fun los_numeros_chicos_sin_medida_se_quedan_en_letra() {
        assertEquals("Tengo dos hijos y un perro.", cerrado("tengo dos hijos y un perro"))
        assertEquals("Llevo tres dias asi.", cerrado("llevo tres dias asi"))
        assertEquals("Tengo 42 años.", cerrado("tengo cuarenta y dos años"))
    }

    @Test
    fun se_quitan_muletillas_y_tartamudeo() {
        assertEquals("Me duele la rodilla.", cerrado("eh me me duele la mmm rodilla"))
    }

    @Test
    fun cerrar_pone_mayusculas_y_punto_final_una_sola_vez() {
        assertEquals("Hola. Ya estoy mejor.", ProcesadorDeDictado.cerrar("hola. ya estoy mejor"))
        assertEquals("¿Todo bien?", ProcesadorDeDictado.cerrar("¿todo bien?"))
        assertEquals("", ProcesadorDeDictado.cerrar("   "))
        assertEquals("Fiebre de 38.5 °C.", ProcesadorDeDictado.cerrar("fiebre de 38.5 °C"))
    }

    @Test
    fun una_pausa_larga_entre_frases_cierra_la_oracion() {
        assertEquals(
            "Me duele la cabeza. Tambien tengo sed",
            ProcesadorDeDictado.capitalizar(ProcesadorDeDictado.unirSegmentos(listOf("me duele la cabeza", "tambien tengo sed"))),
        )
        // Una o dos palabras no son una oracion: "Hola" + "doctor" no lleva punto.
        assertEquals("Hola doctor", ProcesadorDeDictado.capitalizar(ProcesadorDeDictado.unirSegmentos(listOf("hola", "doctor"))))
    }

    @Test
    fun la_vista_continua_el_texto_que_ya_estaba_escrito() {
        assertEquals("Buenos dias. Hoy me siento mejor", ProcesadorDeDictado.vista("Buenos dias.", listOf("hoy me siento mejor")))
        assertEquals("Nota\nSigo igual", ProcesadorDeDictado.vista("Nota\n", listOf("sigo igual")))
        assertEquals("Ya escrito", ProcesadorDeDictado.vista("Ya escrito", emptyList()))
        assertEquals("Me duele", ProcesadorDeDictado.vista("", listOf("me"), parcial = "duele"))
    }
}
