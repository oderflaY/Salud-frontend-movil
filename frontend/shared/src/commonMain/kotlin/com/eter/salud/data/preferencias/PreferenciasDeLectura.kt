package com.eter.salud.data.preferencias

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Como quiere leer esta persona, guardado en SU telefono.
 *
 * Hoy solo la traduccion automatica del chat. Es una preferencia del aparato y
 * no del expediente: el mismo paciente puede querer leer traducido en su
 * telefono y en original en la tableta de casa, y nada de esto es dato clinico
 * que deba viajar al servidor.
 *
 * Se persiste (a diferencia de los ajustes de [com.eter.salud.presentation.configuracion.ConfiguracionViewModel],
 * que aun viven en memoria) porque es una decision que se toma UNA vez: quien
 * no habla el idioma de su medico no deberia tener que volver a activarla cada
 * vez que abre la app.
 */
interface PreferenciasDeLectura {

    /** Traducir sin preguntar los mensajes de la otra parte al idioma de la app. */
    val traducirSiempre: Flow<Boolean>

    suspend fun cambiarTraducirSiempre(activo: Boolean)
}

class PreferenciasDeLecturaEnDisco(
    private val preferencias: DataStore<Preferences>,
) : PreferenciasDeLectura {

    /**
     * Un archivo ilegible se lee como "apagado" en vez de propagar el error:
     * ante la duda se muestra lo que la persona escribio de verdad, y volver a
     * encender el interruptor cuesta un toque.
     */
    override val traducirSiempre: Flow<Boolean> = preferencias.data
        .catch { emit(emptyPreferences()) }
        .map { it[CLAVE_TRADUCIR_SIEMPRE] ?: false }

    override suspend fun cambiarTraducirSiempre(activo: Boolean) {
        preferencias.edit { it[CLAVE_TRADUCIR_SIEMPRE] = activo }
    }

    private companion object {
        val CLAVE_TRADUCIR_SIEMPRE = booleanPreferencesKey("traducir_siempre")
    }
}

/**
 * Archivo propio, separado del de la sesion: al cerrar sesion ese se vacia
 * entero, y perder la preferencia de idioma al salir seria un efecto colateral
 * invisible.
 */
internal const val ARCHIVO_DE_PREFERENCIAS = "preferencias.preferences_pb"

@Composable
expect fun recordarPreferenciasDeLectura(): PreferenciasDeLectura
