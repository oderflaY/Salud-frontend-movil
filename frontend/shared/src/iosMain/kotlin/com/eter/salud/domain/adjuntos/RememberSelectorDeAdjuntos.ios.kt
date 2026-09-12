package com.eter.salud.domain.adjuntos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eter.salud.data.adjuntos.SelectorDeAdjuntosIos

@Composable
actual fun rememberSelectorDeAdjuntos(idConversacion: String): SelectorDeAdjuntos =
    remember(idConversacion) { SelectorDeAdjuntosIos(idConversacion) }
