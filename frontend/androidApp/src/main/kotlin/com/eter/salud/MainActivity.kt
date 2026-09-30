package com.eter.salud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eter.salud.data.red.ConfiguracionApi
import com.eter.salud.domain.avisos.EXTRA_ABRIR_CONVERSACION
import com.eter.salud.presentation.avisos.AperturaDesdeAviso

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Antes de `setContent`: ContenedorRed lee BASE_URL al construirse, y
        // eso pasa durante la primera composicion de App().
        ConfiguracionApi.URL_COMPILADA = BuildConfig.URL_BACKEND
        ConfiguracionApi.BASE_URL = BuildConfig.URL_BACKEND
        if (BuildConfig.MODO_LOCAL) {
            // Demostracion sin red: todo sale del SQLite que siembra
            // `SembradorDemo`. Se apaga tambien la busqueda del servidor, o la
            // pantalla de arranque se quedaria diez segundos buscando uno que
            // no vamos a usar.
            ConfiguracionApi.USAR_BACKEND_REMOTO = false
            ConfiguracionApi.DESCUBRIR_EN_RED = false
        } else if (BuildConfig.DEBUG) {
            ConfiguracionApi.REGISTRAR_PETICIONES = true
            // En desarrollo el backend corre en una computadora de la red cuya IP
            // cambia: se arranca con la ultima que funciono y se busca si no responde.
            val preferencias = getSharedPreferences(PREFERENCIAS_SERVIDOR, MODE_PRIVATE)
            preferencias.getString(CLAVE_URL_SERVIDOR, null)?.let { ConfiguracionApi.BASE_URL = it }
            ConfiguracionApi.DESCUBRIR_EN_RED = true
            ConfiguracionApi.alCambiarServidor = { url ->
                preferencias.edit().putString(CLAVE_URL_SERVIDOR, url).apply()
            }
        }

        setContent {
            App()
        }
        abrirConversacionDelAviso(intent)
    }

    /** Con la app ya abierta, tocar un aviso llega aqui (la Activity es `SINGLE_TOP`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abrirConversacionDelAviso(intent)
    }

    /** El aviso de un mensaje trae la conversacion: `App()` navega hasta ella. */
    private fun abrirConversacionDelAviso(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ABRIR_CONVERSACION)?.let(AperturaDesdeAviso::abrir)
        // Consumido: girar el telefono no debe volver a abrir el mismo chat.
        intent?.removeExtra(EXTRA_ABRIR_CONVERSACION)
    }
}

private const val PREFERENCIAS_SERVIDOR = "salud_servidor"
private const val CLAVE_URL_SERVIDOR = "url"

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}