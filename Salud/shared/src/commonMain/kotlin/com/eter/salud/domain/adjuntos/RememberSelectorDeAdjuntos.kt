package com.eter.salud.domain.adjuntos

import androidx.compose.runtime.Composable

/**
 * Fabrica del selector de adjuntos real del dispositivo.
 *
 * `@Composable` y no una fabrica comun, por la misma razon que
 * [com.eter.salud.domain.nfc.rememberLectorTarjetaNfc]: elegir una foto o un
 * archivo en Android se registra con `rememberLauncherForActivityResult`, que
 * solo se puede llamar dentro de composicion.
 *
 * @param idConversacion namespacea donde se copian los archivos elegidos
 * (`adjuntos/<idConversacion>/...` en el almacenamiento propio de la app), para
 * que dos conversaciones nunca choquen ni se borren entre si.
 */
@Composable
expect fun rememberSelectorDeAdjuntos(idConversacion: String): SelectorDeAdjuntos
