package com.eter.salud.data.sesion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun recordarAlmacenDeSesion(): AlmacenDeSesion = remember {
    AlmacenDeSesion(
        PreferenceDataStoreFactory.createWithPath {
            // Directorio de Documentos del contenedor de la app: es el que iOS
            // respalda y conserva entre ejecuciones, a diferencia de Caches, que
            // el sistema puede vaciar cuando le falta espacio.
            val documentos = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            requireNotNull(documentos?.path) { "Sin directorio de documentos" }
                .plus("/$ARCHIVO_DE_SESION")
                .toPath()
        },
    )
}
