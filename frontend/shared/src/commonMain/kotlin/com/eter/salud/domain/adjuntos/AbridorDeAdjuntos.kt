package com.eter.salud.domain.adjuntos

import androidx.compose.runtime.Composable
import com.eter.salud.domain.model.Adjunto

/**
 * Abre un adjunto del chat con el visor del sistema: la receta en PDF con el
 * lector de PDF, la foto con la galeria. La app no dibuja el archivo por su
 * cuenta -- no hay libreria de imagenes ni de PDF en el proyecto, y el visor
 * del sistema ya sabe hacer zoom, compartir e imprimir.
 */
interface AbridorDeAdjuntos {
    /**
     * `false` si no se pudo: el archivo aun no termino de bajar, o no hay en el
     * telefono ninguna app para ese tipo. La Vista lo dice en vez de quedarse
     * sin responder al toque.
     */
    fun abrir(adjunto: Adjunto): Boolean
}

@Composable
expect fun rememberAbridorDeAdjuntos(): AbridorDeAdjuntos
