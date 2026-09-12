package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Como se consiguio el archivo. Gobierna el icono y la etiqueta que ve el
 * medico, no el manejo del archivo: los tres tipos se envian y se guardan
 * exactamente igual.
 */
@Serializable
enum class TipoAdjunto {
    /** Elegida de la galeria del dispositivo. */
    FOTO,

    /** Elegido del sistema de archivos (un PDF de laboratorio, por ejemplo). */
    ARCHIVO,

    /** Capturado con la camara a traves del escaner de documentos. */
    ESCANEO,
}

/**
 * Un archivo adjunto a un mensaje del chat.
 *
 * [rutaLocal] apunta a una copia PROPIA de la app (almacenamiento privado en
 * Android, `Documents/` en iOS), nunca al `content://`/`NSURL` original que
 * entrego el selector de la galeria o del sistema de archivos: ese permiso de
 * lectura puede revocarse en cuanto la pantalla que lo pidio se cierra, y una
 * conversacion tiene que poder releerse manana. Copiar los bytes una sola vez,
 * al elegir el archivo, es lo que hace que el adjunto sobreviva.
 *
 * No lleva los bytes en memoria: un expediente con semanas de fotos y PDFs no
 * puede vivir entero en el `StateFlow` del chat. La Vista lee [rutaLocal] solo
 * cuando de verdad tiene que mostrar el archivo.
 */
data class Adjunto(
    val idAdjunto: String,
    val tipo: TipoAdjunto,
    /** Nombre para mostrar, por ejemplo `resultado_laboratorio.pdf` o `IMG_20260910.jpg`. */
    val nombre: String,
    val rutaLocal: String,
    /** Tipo MIME, por ejemplo `image/jpeg` o `application/pdf`. */
    val tipoMime: String,
)
