package com.eter.salud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eter.salud.data.red.ConfiguracionApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Antes de `setContent`: ContenedorRed lee BASE_URL al construirse, y
        // eso pasa durante la primera composicion de App().
        ConfiguracionApi.URL_COMPILADA = BuildConfig.URL_BACKEND
        ConfiguracionApi.BASE_URL = BuildConfig.URL_BACKEND
        if (BuildConfig.DEBUG) {
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
    }
}

private const val PREFERENCIAS_SERVIDOR = "salud_servidor"
private const val CLAVE_URL_SERVIDOR = "url"

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}