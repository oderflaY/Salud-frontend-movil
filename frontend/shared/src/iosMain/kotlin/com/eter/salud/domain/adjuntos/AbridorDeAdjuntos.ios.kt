package com.eter.salud.domain.adjuntos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eter.salud.domain.model.Adjunto
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
actual fun rememberAbridorDeAdjuntos(): AbridorDeAdjuntos = remember { AbridorDeAdjuntosIos() }

/**
 * Vista previa de QuickLook (`UIDocumentInteractionController`): PDF, fotos y
 * documentos de Office, con compartir e imprimir incluidos.
 */
private class AbridorDeAdjuntosIos : AbridorDeAdjuntos {

    // Se retiene: si nadie sostiene el controlador, iOS lo libera y la vista
    // previa se cierra en cuanto aparece.
    private var controlador: UIDocumentInteractionController? = null

    private val delegado = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(
            controller: UIDocumentInteractionController,
        ): UIViewController = UIApplication.sharedApplication.keyWindow?.rootViewController ?: UIViewController()
    }

    override fun abrir(adjunto: Adjunto): Boolean {
        if (!NSFileManager.defaultManager.fileExistsAtPath(adjunto.rutaLocal)) return false
        val nuevo = UIDocumentInteractionController.interactionControllerWithURL(NSURL.fileURLWithPath(adjunto.rutaLocal))
        nuevo.delegate = delegado
        controlador = nuevo
        return nuevo.presentPreviewAnimated(true)
    }
}
