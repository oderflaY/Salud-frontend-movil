package com.eter.salud.data.sesion

import androidx.compose.runtime.Composable

/**
 * Crea el almacen de sesion de la plataforma.
 *
 * Necesita una ruta de archivo, y esa ruta se pide de forma distinta en cada
 * sistema: en Android sale del `Context` y en iOS del directorio de documentos
 * del contenedor de la app. Por eso es `expect` y no una clase comun.
 */
@Composable
expect fun recordarAlmacenDeSesion(): AlmacenDeSesion
