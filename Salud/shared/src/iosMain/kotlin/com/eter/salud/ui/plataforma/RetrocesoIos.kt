package com.eter.salud.ui.plataforma

import androidx.compose.runtime.Composable

/**
 * iOS no tiene boton de retroceso del sistema: el gesto de deslizar desde el
 * borde lo gobierna el propio sistema y no se intercepta desde la app. No hacer
 * nada aqui es la implementacion correcta, no una pendiente.
 */
@Composable
actual fun ManejadorDeRetroceso(habilitado: Boolean, alRetroceder: () -> Unit) = Unit

/**
 * iOS prohibe expresamente que una app se cierre o se mande al fondo por su
 * cuenta (App Store Review Guideline 2.5.4). El usuario minimiza con el gesto de
 * inicio, y solo el.
 */
@Composable
actual fun recordarMinimizadorDeApp(): () -> Unit = {}
