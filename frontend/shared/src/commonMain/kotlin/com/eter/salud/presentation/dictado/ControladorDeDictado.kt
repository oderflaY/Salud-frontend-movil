package com.eter.salud.presentation.dictado

import com.eter.salud.domain.diario.ResultadoTriage
import com.eter.salud.domain.diario.TriageDelDiario
import com.eter.salud.domain.dictado.ErrorDictado
import com.eter.salud.domain.dictado.EscuchaDeDictado
import com.eter.salud.domain.dictado.EtiquetasResumen
import com.eter.salud.domain.dictado.ProcesadorDeDictado
import com.eter.salud.domain.dictado.ReconocedorDeVoz
import com.eter.salud.domain.dictado.ResumenClinico
import com.eter.salud.domain.dictado.ResumenDelDictado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class FaseDictado {
    INACTIVO,

    /** Pidiendo permisos o abriendo el micrófono. */
    PREPARANDO,
    ESCUCHANDO,

    /** Se pidió detener; falta el último resultado del reconocedor. */
    FINALIZANDO,
}

data class DictadoUiState(
    val fase: FaseDictado = FaseDictado.INACTIVO,
    /** 0..1 en pasos de 0.1: el botón no se recompone por variaciones que no se ven. */
    val nivelDeVoz: Float = 0f,
    val error: ErrorDictado? = null,
    /** Datos clínicos detectados en lo dictado, en vivo. */
    val resumen: ResumenClinico = ResumenClinico(),
    /** Triage del texto completo, en vivo: el mismo que usa el diario. */
    val triage: ResultadoTriage = ResultadoTriage.VACIO,
    /** Agregar el bloque de resumen al terminar. */
    val agregarResumen: Boolean = true,
) {
    val activo: Boolean get() = fase != FaseDictado.INACTIVO
}

/**
 * Une el micrófono con un campo de texto.
 *
 * Mientras se dicta, el campo muestra lo que había antes MÁS lo dictado, ya
 * procesado (puntuación, cifras, unidades) y en vivo con cada resultado parcial.
 * Al terminar se cierra el texto (mayúsculas, punto final) y, si se detectaron
 * datos clínicos, se agrega el resumen.
 *
 * Cada segmento terminado se procesa UNA vez y se guarda ya procesado: en cada
 * resultado parcial solo se procesa el fragmento en curso, así el costo no
 * crece con la duración del dictado.
 *
 * @param alCambiarTexto recibe el texto completo del campo; es el mismo setter
 * que usa el teclado (`actualizarBorrador`, `actualizarTexto`).
 */
class ControladorDeDictado(
    private val reconocedor: ReconocedorDeVoz,
    private val etiquetas: EtiquetasResumen = EtiquetasResumen(),
    private val alCambiarTexto: (String) -> Unit,
) : EscuchaDeDictado {

    private val _estado = MutableStateFlow(DictadoUiState())
    val estado: StateFlow<DictadoUiState> = _estado.asStateFlow()

    val disponible: Boolean get() = reconocedor.disponible

    private var base = ""
    private val segmentos = mutableListOf<String>()
    private var parcial = ""
    private var alTerminarDeDictar: (() -> Unit)? = null

    /** Empieza a dictar a continuación de [textoActual]. */
    fun iniciar(textoActual: String) {
        if (_estado.value.activo) return
        base = ResumenDelDictado.quitarResumen(textoActual)
        segmentos.clear()
        parcial = ""
        alTerminarDeDictar = null
        _estado.update { DictadoUiState(fase = FaseDictado.PREPARANDO, agregarResumen = it.agregarResumen) }
        reconocedor.iniciar(this)
    }

    /** Deja de escuchar; el texto final llega cuando el reconocedor entrega lo último. */
    fun detener() {
        if (_estado.value.fase !in setOf(FaseDictado.PREPARANDO, FaseDictado.ESCUCHANDO)) return
        _estado.update { it.copy(fase = FaseDictado.FINALIZANDO, nivelDeVoz = 0f) }
        reconocedor.detener()
    }

    /**
     * Detiene y, en cuanto el texto final está en el campo, envía. Si no se estaba
     * dictando, envía directo.
     */
    fun detenerYEnviar(alEnviar: () -> Unit) {
        if (!_estado.value.activo) {
            alEnviar()
            return
        }
        alTerminarDeDictar = alEnviar
        detener()
    }

    /** Descarta lo dictado y deja el campo como estaba antes de empezar. */
    fun cancelar() {
        if (!_estado.value.activo) return
        alTerminarDeDictar = null
        reconocedor.cancelar()
        alCambiarTexto(base)
        _estado.update { it.copy(fase = FaseDictado.INACTIVO, nivelDeVoz = 0f, resumen = ResumenClinico(), triage = ResultadoTriage.VACIO) }
    }

    /** Suelta el micrófono al salir de la pantalla, conservando lo dictado. */
    fun soltar() {
        if (!_estado.value.activo) return
        alTerminarDeDictar = null
        reconocedor.cancelar()
        if (hayDictado()) alCambiarTexto(textoFinal())
        _estado.update { it.copy(fase = FaseDictado.INACTIVO, nivelDeVoz = 0f) }
    }

    fun alternarResumen() {
        _estado.update { it.copy(agregarResumen = !it.agregarResumen) }
    }

    fun descartarError() {
        _estado.update { it.copy(error = null) }
    }

    // ------------------------------------------------------------------ escucha

    override fun alListo() {
        _estado.update { if (it.fase == FaseDictado.PREPARANDO) it.copy(fase = FaseDictado.ESCUCHANDO, error = null) else it }
    }

    override fun alParcial(texto: String) {
        if (!_estado.value.activo) return
        parcial = ProcesadorDeDictado.procesar(texto)
        publicar()
    }

    override fun alSegmento(texto: String) {
        if (!_estado.value.activo) return
        val procesado = ProcesadorDeDictado.procesar(texto)
        if (procesado.isNotBlank()) segmentos += procesado
        parcial = ""
        publicar()
    }

    override fun alNivel(nivel: Float) {
        val escalonado = (nivel.coerceIn(0f, 1f) * 10).toInt() / 10f
        _estado.update { if (it.activo && it.nivelDeVoz != escalonado) it.copy(nivelDeVoz = escalonado) else it }
    }

    override fun alTerminar() {
        if (!_estado.value.activo) return
        if (hayDictado()) alCambiarTexto(textoFinal())
        _estado.update { it.copy(fase = FaseDictado.INACTIVO, nivelDeVoz = 0f) }
        val accion = alTerminarDeDictar
        alTerminarDeDictar = null
        accion?.invoke()
    }

    override fun alFallar(error: ErrorDictado) {
        if (!_estado.value.activo) return
        // Lo dictado antes del error no se pierde, pero tampoco se envía solo:
        // la persona tiene que ver que algo falló antes de mandarlo.
        alTerminarDeDictar = null
        if (hayDictado()) alCambiarTexto(textoFinal())
        _estado.update { it.copy(fase = FaseDictado.INACTIVO, nivelDeVoz = 0f, error = error) }
    }

    // ------------------------------------------------------------------ texto

    private fun hayDictado(): Boolean = segmentos.isNotEmpty() || parcial.isNotBlank()

    private fun publicar() {
        val texto = ProcesadorDeDictado.vista(base, segmentos, parcial)
        alCambiarTexto(texto)
        _estado.update { it.copy(resumen = ResumenDelDictado.extraer(texto), triage = TriageDelDiario.evaluar(texto)) }
    }

    private fun textoFinal(): String {
        val cuerpo = ProcesadorDeDictado.cerrar(ProcesadorDeDictado.vista(base, segmentos, parcial))
        val resumen = ResumenDelDictado.extraer(cuerpo)
        return if (_estado.value.agregarResumen) ResumenDelDictado.conResumen(cuerpo, resumen, etiquetas) else cuerpo
    }
}
