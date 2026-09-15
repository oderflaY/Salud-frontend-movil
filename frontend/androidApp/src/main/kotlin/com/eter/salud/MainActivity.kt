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
        ConfiguracionApi.BASE_URL = BuildConfig.URL_BACKEND

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}