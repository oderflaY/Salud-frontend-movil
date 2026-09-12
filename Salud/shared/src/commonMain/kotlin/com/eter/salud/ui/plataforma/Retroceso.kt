package com.eter.salud.ui.plataforma

import androidx.compose.runtime.Composable

/**
 * Intercepta el gesto o boton de retroceso del sistema.
 *
 * Existe porque esta version de Compose Multiplatform no trae un `BackHandler`
 * comun, y sin interceptar nada el boton fisico de Android CIERRA la Activity
 * desde cualquier pantalla. Ese es el bug real: estando en un chat, en el
 * cuestionario o en el escaner, pulsar Atras no devolvia a la pantalla anterior
 * -- sacaba de la app entera.
 *
 * En iOS no hay boton de retroceso del sistema, asi que su implementacion no
 * hace nada. Es una diferencia legitima de plataforma, no una carencia: alli el
 * retroceso lo da el gesto de deslizar desde el borde, que gobierna el sistema.
 */
@Composable
expect fun ManejadorDeRetroceso(habilitado: Boolean, alRetroceder: () -> Unit)

/**
 * Manda la app al fondo sin cerrarla.
 *
 * Es lo que debe pasar al pulsar Atras en la raiz de navegacion. Cerrar la
 * Activity destruye el proceso, y con el la sesion en memoria: el paciente
 * volvia a encontrarse la pantalla de acceso por haber pulsado Atras una vez de
 * mas. Minimizar deja la app viva en segundo plano, como hace cualquier app de
 * mensajeria.
 */
@Composable
expect fun recordarMinimizadorDeApp(): () -> Unit
