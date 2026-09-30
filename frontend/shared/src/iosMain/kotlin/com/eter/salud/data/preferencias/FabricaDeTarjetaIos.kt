package com.eter.salud.data.preferencias

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun recordarPreferenciasDeTarjeta(): PreferenciasDeTarjeta = remember {
    PreferenciasDeTarjetaEnDisco(
        PreferenceDataStoreFactory.createWithPath {
            val documentos = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            requireNotNull(documentos?.path) { "Sin directorio de documentos" }
                .plus("/$ARCHIVO_DE_TARJETA")
                .toPath()
        },
    )
}
