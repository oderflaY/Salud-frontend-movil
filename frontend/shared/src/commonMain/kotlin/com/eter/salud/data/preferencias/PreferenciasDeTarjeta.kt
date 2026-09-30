package com.eter.salud.data.preferencias

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.eter.salud.domain.model.DatoDeTarjeta
import com.eter.salud.domain.model.VisibilidadDeTarjeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Que muestra cada tarjeta de emergencia, segun lo decidio su paciente.
 *
 * La llave es la TARJETA y no el paciente: quien escanea solo conoce el UID de
 * la tarjeta, y asi el filtro se aplica sin tener que averiguar de quien es.
 */
interface PreferenciasDeTarjeta {
    fun visibilidad(idTarjeta: String): Flow<VisibilidadDeTarjeta>
    suspend fun guardar(idTarjeta: String, visibilidad: VisibilidadDeTarjeta)
}

/**
 * En disco, en un archivo propio: no comparte el de la sesion (que se vacia al
 * cerrarla) porque la decision de que muestra tu tarjeta no depende de si
 * tienes la sesion abierta -- la tarjeta se escanea igual.
 */
class PreferenciasDeTarjetaEnDisco(
    private val preferencias: DataStore<Preferences>,
) : PreferenciasDeTarjeta {

    /**
     * Un archivo ilegible se lee como "todo visible": ante la duda, en una
     * urgencia es mas seguro que el paramedico vea de mas que de menos.
     */
    override fun visibilidad(idTarjeta: String): Flow<VisibilidadDeTarjeta> = preferencias.data
        .catch { emit(emptyPreferences()) }
        .map { datos ->
            val ocultos = datos[llaveDe(idTarjeta)].orEmpty()
                // Un nombre que esta version no conoce (de una version futura
                // o de un archivo danado) se ignora en vez de romper la lectura.
                .mapNotNull { nombre -> DatoDeTarjeta.entries.firstOrNull { it.name == nombre } }
                .toSet()
            VisibilidadDeTarjeta(ocultos)
        }

    override suspend fun guardar(idTarjeta: String, visibilidad: VisibilidadDeTarjeta) {
        preferencias.edit { it[llaveDe(idTarjeta)] = visibilidad.ocultos.map { dato -> dato.name }.toSet() }
    }

    private fun llaveDe(idTarjeta: String) = stringSetPreferencesKey("ocultos_${idTarjeta.uppercase()}")
}

/** Para pruebas y para el modo sin disco: vive mientras viva el objeto. */
class PreferenciasDeTarjetaEnMemoria : PreferenciasDeTarjeta {
    private val porTarjeta = mutableMapOf<String, MutableStateFlow<VisibilidadDeTarjeta>>()

    private fun flujo(idTarjeta: String) =
        porTarjeta.getOrPut(idTarjeta.uppercase()) { MutableStateFlow(VisibilidadDeTarjeta.TODO_VISIBLE) }

    override fun visibilidad(idTarjeta: String): Flow<VisibilidadDeTarjeta> = flujo(idTarjeta)

    override suspend fun guardar(idTarjeta: String, visibilidad: VisibilidadDeTarjeta) {
        flujo(idTarjeta).value = visibilidad
    }
}

internal const val ARCHIVO_DE_TARJETA = "tarjeta.preferences_pb"

@Composable
expect fun recordarPreferenciasDeTarjeta(): PreferenciasDeTarjeta
