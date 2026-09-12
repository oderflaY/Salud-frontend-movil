package com.eter.salud.data.adjuntos

import com.eter.salud.domain.adjuntos.SelectorDeAdjuntos
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.TipoAdjunto
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * Los tres selectores de iOS: galeria ([UIImagePickerController]), selector de
 * archivos ([UIDocumentPickerViewController]) y camara de escaneo
 * ([VNDocumentCameraViewController], VisionKit -- deteccion de bordes real, sin
 * dependencia adicional: es parte del sistema desde iOS 13).
 *
 * Cada delegado se retiene como propiedad de la instancia y no como variable
 * local de la funcion que lo crea: si se soltara al terminar esa funcion, ARC
 * lo liberaria antes de que el usuario alcanzara a responder en la pantalla que
 * el propio delegado esta gobernando.
 */
@OptIn(ExperimentalForeignApi::class)
internal class SelectorDeAdjuntosIos(
    idConversacion: String,
) : SelectorDeAdjuntos {

    private val copiador = CopiadorDeAdjuntosIos(idConversacion)

    private var delegadoFoto: DelegadoFoto? = null
    private var delegadoArchivo: DelegadoArchivo? = null
    private var delegadoEscaneo: DelegadoEscaneo? = null

    override fun elegirFoto(alElegir: (Adjunto) -> Unit) {
        val controlador = UIImagePickerController()
        controlador.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        val delegado = DelegadoFoto(copiador, alElegir) { cerrar(controlador) }
        delegadoFoto = delegado
        controlador.delegate = delegado
        presentar(controlador)
    }

    override fun elegirArchivo(alElegir: (Adjunto) -> Unit) {
        val controlador = UIDocumentPickerViewController(
            documentTypes = listOf(TIPO_DOCUMENTO_CUALQUIERA),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        val delegado = DelegadoArchivo(copiador, alElegir) { cerrar(controlador) }
        delegadoArchivo = delegado
        controlador.delegate = delegado
        presentar(controlador)
    }

    override fun escanearDocumento(alElegir: (Adjunto) -> Unit) {
        val controlador = VNDocumentCameraViewController()
        val delegado = DelegadoEscaneo(copiador, alElegir) { cerrar(controlador) }
        delegadoEscaneo = delegado
        controlador.delegate = delegado
        presentar(controlador)
    }

    /**
     * `keyWindow` y no la API de escenas: la app no declara soporte de multiples
     * ventanas, y para una sola ventana sigue siendo el camino directo hacia el
     * controlador que de verdad esta en pantalla.
     */
    private fun presentar(controlador: platform.UIKit.UIViewController) {
        UIApplication.sharedApplication.keyWindow
            ?.rootViewController
            ?.presentViewController(controlador, animated = true, completion = null)
    }

    private fun cerrar(controlador: platform.UIKit.UIViewController) {
        controlador.dismissViewControllerAnimated(true, completion = null)
        delegadoFoto = null
        delegadoArchivo = null
        delegadoEscaneo = null
    }

    private companion object {
        /** `public.item`: cualquier archivo, sin restringir por tipo. */
        const val TIPO_DOCUMENTO_CUALQUIERA = "public.item"
    }
}

/** Convierte una `UIImage` a JPEG y la entrega al copiador con un nombre generado. */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.guardarComo(
    copiador: CopiadorDeAdjuntosIos,
    tipo: TipoAdjunto,
    prefijoNombre: String,
): Adjunto? {
    val datos = UIImageJPEGRepresentation(this, CALIDAD_JPEG) ?: return null
    return copiador.guardar(datos, "$prefijoNombre.jpg", "image/jpeg", tipo)
}

private const val CALIDAD_JPEG = 0.85

@OptIn(ExperimentalForeignApi::class)
private class DelegadoFoto(
    private val copiador: CopiadorDeAdjuntosIos,
    private val alElegir: (Adjunto) -> Unit,
    private val alTerminar: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val imagen = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        alTerminar()
        imagen?.guardarComo(copiador, TipoAdjunto.FOTO, "foto")?.let(alElegir)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        alTerminar()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DelegadoArchivo(
    private val copiador: CopiadorDeAdjuntosIos,
    private val alElegir: (Adjunto) -> Unit,
    private val alTerminar: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        alTerminar()
        val url = didPickDocumentsAtURLs.filterIsInstance<NSURL>().firstOrNull() ?: return
        val accedido = url.startAccessingSecurityScopedResource()
        val datos = NSData.dataWithContentsOfURL(url)
        if (accedido) url.stopAccessingSecurityScopedResource()
        if (datos == null) return

        val nombre = url.lastPathComponent ?: "archivo"
        val mime = mimeDesdeNombre(nombre)
        copiador.guardar(datos, nombre, mime, TipoAdjunto.ARCHIVO)?.let(alElegir)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        alTerminar()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DelegadoEscaneo(
    private val copiador: CopiadorDeAdjuntosIos,
    private val alElegir: (Adjunto) -> Unit,
    private val alTerminar: () -> Unit,
) : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {

    /**
     * Un solo documento por captura y no el lote entero: la conversacion del
     * chat es "aqui esta mi receta", no "aqui esta mi expediente completo".
     */
    override fun documentCameraViewController(
        controller: VNDocumentCameraViewController,
        didFinishWithScan: VNDocumentCameraScan,
    ) {
        alTerminar()
        if (didFinishWithScan.pageCount.toInt() <= 0) return
        val imagen = didFinishWithScan.imageOfPageAtIndex(0u)
        imagen.guardarComo(copiador, TipoAdjunto.ESCANEO, "escaneo")?.let(alElegir)
    }

    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
        alTerminar()
    }

    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFailWithError: NSError) {
        alTerminar()
    }
}

/** Deduccion minima de MIME por extension: lo que de verdad llega de un expediente medico. */
private fun mimeDesdeNombre(nombre: String): String = when (nombre.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    else -> "application/octet-stream"
}
