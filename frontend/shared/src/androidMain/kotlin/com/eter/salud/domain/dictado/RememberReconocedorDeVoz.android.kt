package com.eter.salud.domain.dictado

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.eter.salud.data.dictado.ReconocedorDeVozAndroid

/**
 * El permiso de micrófono se pide en el momento de tocar el botón, no al abrir
 * la pantalla: quien nunca dicta nunca ve la pregunta. La escucha pendiente se
 * guarda mientras el diálogo del sistema está abierto.
 */
@Composable
actual fun rememberReconocedorDeVoz(): ReconocedorDeVoz {
    val contexto = LocalContext.current
    val real = remember(contexto) { ReconocedorDeVozAndroid(contexto) }
    var pendiente by remember { mutableStateOf<EscuchaDeDictado?>(null) }

    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        val escucha = pendiente ?: return@rememberLauncherForActivityResult
        pendiente = null
        if (concedido) real.iniciar(escucha) else escucha.alFallar(ErrorDictado.SIN_PERMISO)
    }

    DisposableEffect(real) {
        onDispose { real.destruir() }
    }

    return remember(real, lanzador) {
        object : ReconocedorDeVoz {
            override val disponible: Boolean get() = real.disponible

            override fun iniciar(escucha: EscuchaDeDictado) {
                val tienePermiso = ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (tienePermiso) {
                    real.iniciar(escucha)
                } else {
                    pendiente = escucha
                    lanzador.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            override fun detener() {
                pendiente?.let { escucha ->
                    pendiente = null
                    escucha.alTerminar()
                    return
                }
                real.detener()
            }

            override fun cancelar() {
                pendiente = null
                real.cancelar()
            }
        }
    }
}
