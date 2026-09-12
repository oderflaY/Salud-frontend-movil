package com.eter.salud.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.eter.salud.data.db.BaseSalud

@Composable
actual fun recordarContenedorSalud(): ContenedorSalud = remember {
    // NativeSqliteDriver guarda el archivo en el directorio de la app que iOS
    // conserva entre ejecuciones.
    ContenedorCompartido.obtener { NativeSqliteDriver(BaseSalud.Schema, ARCHIVO_DE_BASE) }
}
