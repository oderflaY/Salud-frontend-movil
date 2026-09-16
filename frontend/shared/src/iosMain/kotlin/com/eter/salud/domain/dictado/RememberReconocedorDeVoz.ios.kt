package com.eter.salud.domain.dictado

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.eter.salud.data.dictado.ReconocedorDeVozIos

/** Los permisos (reconocimiento de voz y micrófono) los pide el propio reconocedor al iniciar. */
@Composable
actual fun rememberReconocedorDeVoz(): ReconocedorDeVoz {
    val reconocedor = remember { ReconocedorDeVozIos() }
    DisposableEffect(reconocedor) {
        onDispose { reconocedor.cancelar() }
    }
    return reconocedor
}
