package com.eter.salud.domain.adjuntos

import com.eter.salud.domain.model.Adjunto

/**
 * Las tres formas de conseguir un archivo para el chat, vistas desde la capa de
 * presentacion.
 *
 * Es una interfaz de dominio y no un `expect class`, por la misma razon que
 * [com.eter.salud.domain.nfc.LectorTarjetaNfc]: el `ChatViewModel` -- y
 * cualquier prueba suya -- solo necesita saber que existen estas tres acciones
 * y que cada una entrega un [Adjunto] ya resuelto; no necesita arrastrar un
 * selector de Android ni una sesion de VisionKit para probarse.
 *
 * Cada metodo entrega el resultado por callback y no por valor de retorno
 * `suspend`: elegir una foto, un archivo o escanear un documento abre pantalla
 * de sistema y el usuario puede tardar minutos o cancelar sin avisar. Un
 * `alElegir` que simplemente nunca se llama es una cancelacion silenciosa que
 * no exige un segundo camino de error en el ViewModel.
 */
interface SelectorDeAdjuntos {

    /** Abre la galeria del dispositivo y deja elegir una sola imagen. */
    fun elegirFoto(alElegir: (Adjunto) -> Unit)

    /** Abre el selector de archivos del sistema, sin restringir el tipo. */
    fun elegirArchivo(alElegir: (Adjunto) -> Unit)

    /**
     * Abre la camara en modo escaner: detecta los bordes de la hoja, la
     * recorta y endereza. Pensado para una receta o un resultado de
     * laboratorio en papel, no para una foto casual.
     */
    fun escanearDocumento(alElegir: (Adjunto) -> Unit)
}
