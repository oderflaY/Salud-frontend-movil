package com.eter.salud.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.eter.salud.data.db.BaseSalud

@Composable
actual fun recordarContenedorSalud(): ContenedorSalud {
    // El contexto de la APLICACION, no el de la Activity: el contenedor vive
    // mas que cualquier Activity y retener una recreada seria una fuga.
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) {
        ContenedorCompartido.obtener { AndroidSqliteDriver(BaseSalud.Schema, contexto, ARCHIVO_DE_BASE) }
    }
}
