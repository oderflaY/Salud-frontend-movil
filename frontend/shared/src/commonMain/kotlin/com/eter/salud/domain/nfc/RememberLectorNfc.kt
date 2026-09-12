package com.eter.salud.domain.nfc

import androidx.compose.runtime.Composable

/**
 * Fabrica del lector NFC real del dispositivo.
 *
 * Es un `expect` en lugar de un `expect fun` sin `@Composable` (como
 * [com.eter.salud.domain.time.relojDelSistema]) porque Android necesita la
 * Activity en curso, que solo se obtiene dentro de composicion
 * (`LocalContext.current`); no hay forma de pedirla desde una fabrica comun sin
 * pasar por Compose.
 *
 * @param mensajeDeLaHoja texto ya traducido para la hoja de sistema de CoreNFC
 * en iOS. Android no lo usa: su lectura ocurre dentro de la propia pantalla.
 */
@Composable
expect fun rememberLectorTarjetaNfc(mensajeDeLaHoja: String): LectorTarjetaNfc
