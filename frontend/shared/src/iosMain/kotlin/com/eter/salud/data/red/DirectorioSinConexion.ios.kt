package com.eter.salud.data.red

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Application Support: privado, y a diferencia de Caches iOS no lo vacia solo. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDirectorioSinConexion(): String = remember {
    val soporte = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    soporte?.URLByAppendingPathComponent("sin_conexion")?.path ?: "sin_conexion"
}
