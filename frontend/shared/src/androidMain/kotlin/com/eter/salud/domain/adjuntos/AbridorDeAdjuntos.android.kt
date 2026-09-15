package com.eter.salud.domain.adjuntos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.eter.salud.domain.model.Adjunto
import java.io.File

@Composable
actual fun rememberAbridorDeAdjuntos(): AbridorDeAdjuntos {
    val contexto = LocalContext.current
    return remember(contexto) { AbridorDeAdjuntosAndroid(contexto) }
}

/**
 * Los adjuntos viven en el almacenamiento privado de la app
 * (`filesDir/adjuntos`), que ninguna otra app puede leer. Se entregan al visor
 * con una URI de `FileProvider` y permiso de lectura solo para esa apertura:
 * el archivo no se copia a una carpeta publica.
 */
private class AbridorDeAdjuntosAndroid(private val contexto: Context) : AbridorDeAdjuntos {

    override fun abrir(adjunto: Adjunto): Boolean {
        val archivo = File(adjunto.rutaLocal)
        if (!archivo.exists()) return false
        val uri = try {
            FileProvider.getUriForFile(contexto, "${contexto.packageName}$SUFIJO_AUTORIDAD", archivo)
        } catch (fueraDeRuta: IllegalArgumentException) {
            // El archivo no esta bajo las rutas declaradas en rutas_adjuntos.xml.
            return false
        }
        val intencion = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, adjunto.tipoMime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            contexto.startActivity(intencion)
            true
        } catch (sinVisor: ActivityNotFoundException) {
            false
        }
    }
}

/** Debe coincidir con `android:authorities` del `<provider>` en el manifiesto de androidApp. */
private const val SUFIJO_AUTORIDAD = ".adjuntos"
