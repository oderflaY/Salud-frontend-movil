package com.eter.salud.data.sesion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

@Composable
actual fun recordarAlmacenDeSesion(): AlmacenDeSesion {
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) {
        AlmacenDeSesion(
            PreferenceDataStoreFactory.createWithPath {
                // `filesDir` y no la cache: la cache la borra el sistema cuando
                // necesita espacio, y perder la sesion por eso seria el mismo
                // bug que estamos arreglando.
                contexto.filesDir.resolve(ARCHIVO_DE_SESION).absolutePath.toPath()
            },
        )
    }
}
