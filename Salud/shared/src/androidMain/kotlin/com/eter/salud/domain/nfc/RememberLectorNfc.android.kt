package com.eter.salud.domain.nfc

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eter.salud.data.nfc.LectorNfcAndroid

/**
 * La Activity en curso viene del contexto de composicion: es la misma que
 * declara el modo lector con `enableReaderMode`, asi que la lectura queda
 * ligada al ciclo de vida de la pantalla que la solicito.
 */
@Composable
actual fun rememberLectorTarjetaNfc(mensajeDeLaHoja: String): LectorTarjetaNfc {
    val actividad = LocalContext.current as Activity
    return remember(actividad) { LectorNfcAndroid(actividad) }
}
