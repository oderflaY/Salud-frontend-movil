package com.eter.salud.data.preferencias

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

@Composable
actual fun recordarPreferenciasDeLectura(): PreferenciasDeLectura {
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) {
        PreferenciasDeLecturaEnDisco(
            PreferenceDataStoreFactory.createWithPath {
                contexto.filesDir.resolve(ARCHIVO_DE_PREFERENCIAS).absolutePath.toPath()
            },
        )
    }
}
