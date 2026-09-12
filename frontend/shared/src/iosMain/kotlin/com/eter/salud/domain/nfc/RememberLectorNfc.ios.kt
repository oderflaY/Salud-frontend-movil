package com.eter.salud.domain.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eter.salud.data.nfc.LectorNfcIos

@Composable
actual fun rememberLectorTarjetaNfc(mensajeDeLaHoja: String): LectorTarjetaNfc =
    remember(mensajeDeLaHoja) { LectorNfcIos(mensajeDeLaHoja) }
