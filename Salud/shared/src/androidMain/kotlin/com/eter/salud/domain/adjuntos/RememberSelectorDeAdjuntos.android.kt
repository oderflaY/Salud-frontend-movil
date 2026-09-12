package com.eter.salud.domain.adjuntos

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.eter.salud.data.adjuntos.CopiadorDeAdjuntosAndroid
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.TipoAdjunto
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Los tres selectores registrados con `rememberLauncherForActivityResult`:
 * tiene que hacerse aqui, en composicion, porque el sistema exige inscribir el
 * contrato antes de que la Activity termine de crearse.
 *
 * La devolucion de llamada de cada uno se guarda en una variable de estado
 * (`alElegirFoto`, etc.) en vez de resolverse en el momento del `launch`,
 * porque entre pedir la foto y que el usuario elija pasan segundos en los que
 * la composicion puede volver a ejecutarse; guardar el callback en `remember`
 * es lo que le permite sobrevivir hasta que el selector de sistema responde.
 */
@Composable
actual fun rememberSelectorDeAdjuntos(idConversacion: String): SelectorDeAdjuntos {
    val contexto = LocalContext.current
    val actividad = contexto as Activity
    val copiador = remember(idConversacion) { CopiadorDeAdjuntosAndroid(contexto, idConversacion) }

    var alElegirFoto by remember { mutableStateOf<((Adjunto) -> Unit)?>(null) }
    var alElegirArchivo by remember { mutableStateOf<((Adjunto) -> Unit)?>(null) }
    var alTerminarEscaneo by remember { mutableStateOf<((Adjunto) -> Unit)?>(null) }

    val lanzadorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val callback = alElegirFoto
        alElegirFoto = null
        if (uri != null && callback != null) {
            copiador.copiar(uri, TipoAdjunto.FOTO)?.let(callback)
        }
    }

    val lanzadorArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = alElegirArchivo
        alElegirArchivo = null
        if (uri != null && callback != null) {
            copiador.copiar(uri, TipoAdjunto.ARCHIVO)?.let(callback)
        }
    }

    val lanzadorEscaner = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { resultado ->
        val callback = alTerminarEscaneo
        alTerminarEscaneo = null
        if (resultado.resultCode == Activity.RESULT_OK && callback != null) {
            val paginas = GmsDocumentScanningResult.fromActivityResultIntent(resultado.data)?.pages
            paginas?.firstOrNull()?.let { pagina ->
                copiador.copiar(pagina.imageUri, TipoAdjunto.ESCANEO)?.let(callback)
            }
        }
    }

    return remember(idConversacion) {
        object : SelectorDeAdjuntos {
            override fun elegirFoto(alElegir: (Adjunto) -> Unit) {
                alElegirFoto = alElegir
                lanzadorGaleria.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }

            override fun elegirArchivo(alElegir: (Adjunto) -> Unit) {
                alElegirArchivo = alElegir
                lanzadorArchivo.launch(arrayOf(CUALQUIER_TIPO))
            }

            /**
             * Un solo documento por captura y no un lote: la conversacion del
             * chat es "aqui esta mi receta", no "aqui esta mi expediente
             * completo" -- un lote de veinte paginas pertenece al modulo de
             * historial, no a un mensaje.
             */
            override fun escanearDocumento(alElegir: (Adjunto) -> Unit) {
                alTerminarEscaneo = alElegir
                val opciones = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(false)
                    .setPageLimit(UNA_SOLA_PAGINA)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(opciones)
                    .getStartScanIntent(actividad)
                    .addOnSuccessListener { intentSender ->
                        lanzadorEscaner.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener {
                        // Play Services aun instalando el modulo, o sin camara
                        // disponible: se descarta como una cancelacion mas.
                        alTerminarEscaneo = null
                    }
            }
        }
    }
}

private const val CUALQUIER_TIPO = "*/*"
private const val UNA_SOLA_PAGINA = 1
