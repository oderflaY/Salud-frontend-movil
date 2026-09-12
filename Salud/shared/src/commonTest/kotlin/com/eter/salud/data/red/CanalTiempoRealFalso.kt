package com.eter.salud.data.red

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement

/**
 * Doble de prueba de [CanalTiempoReal]: emite canales a mano, sin abrir ningun
 * WebSocket. Prueba la logica de filtrado/decodificacion de
 * `CitasRepositorioRemoto.agendaDelMedico` y `ChatRepositorioRemoto.mensajesSinLeer`
 * sin depender de que `ktor-client-mock` sepa simular un socket real.
 */
class CanalTiempoRealFalso : CanalTiempoReal {
    private val entrante = MutableSharedFlow<Pair<String, JsonElement>>(extraBufferCapacity = 16)

    suspend fun emitir(canal: String, payload: JsonElement) {
        entrante.emit(canal to payload)
    }

    override fun <T> canal(nombre: String, decodificar: (JsonElement) -> T): Flow<T> =
        entrante.filter { it.first == nombre }.map { decodificar(it.second) }
}
