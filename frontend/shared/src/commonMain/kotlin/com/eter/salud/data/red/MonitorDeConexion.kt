package com.eter.salud.data.red

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Si el backend esta al alcance, segun lo que de verdad paso con las
 * peticiones, no segun el icono de wifi: con wifi puede no haber servidor
 * (cambio la IP de la computadora) y sin wifi puede haber datos moviles.
 *
 *  - Cualquier respuesta del servidor, aunque sea un error, cuenta como
 *    "en linea". Una peticion que no llega (sin red, tiempo agotado) cuenta
 *    como "sin conexion".
 *  - Al pasar de sin conexion a en linea se emite [reconexiones]: las
 *    pantallas abiertas recargan solas lo que mostraban de la copia guardada.
 */
class MonitorDeConexion {

    private val _enLinea = MutableStateFlow(true)
    val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    private val _reconexiones = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reconexiones: SharedFlow<Unit> = _reconexiones.asSharedFlow()

    fun registrarRespuesta() {
        if (!_enLinea.value) {
            _enLinea.value = true
            _reconexiones.tryEmit(Unit)
        }
    }

    fun registrarFalloDeRed() {
        _enLinea.value = false
    }

    /**
     * Mientras no haya conexion, pregunta cada [intervaloMs] si el servidor ya
     * responde; no gasta nada mientras todo va bien. Corre hasta que se cancele
     * el alcance que la lanzo.
     */
    suspend fun vigilarRecuperacion(intervaloMs: Long = INTERVALO_SONDEO_MS, servidorResponde: suspend () -> Boolean) {
        enLinea.collectLatest { enLinea ->
            if (enLinea) return@collectLatest
            while (true) {
                delay(intervaloMs)
                if (servidorResponde()) {
                    registrarRespuesta()
                    return@collectLatest
                }
            }
        }
    }

    private companion object {
        const val INTERVALO_SONDEO_MS = 4_000L
    }
}

/** El estado de conexion de toda la app: un solo backend, un solo estado. */
val EstadoDeConexion = MonitorDeConexion()
