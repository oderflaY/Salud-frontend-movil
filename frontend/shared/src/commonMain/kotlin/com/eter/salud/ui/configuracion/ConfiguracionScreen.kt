package com.eter.salud.ui.configuracion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.idioma.IdiomaApp
import com.eter.salud.domain.idioma.recordarSelectorDeIdioma
import com.eter.salud.presentation.configuracion.ConfiguracionUiState
import com.eter.salud.presentation.configuracion.ConfiguracionViewModel
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.TituloDeBloque
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_accion_cerrar_sesion
import salud.shared.generated.resources.accion_cerrar_sesion
import salud.shared.generated.resources.config_avisos_medicacion
import salud.shared.generated.resources.config_avisos_medicacion_apoyo
import salud.shared.generated.resources.config_avisos_mensajes
import salud.shared.generated.resources.config_avisos_mensajes_apoyo
import salud.shared.generated.resources.config_avisos_sin_permiso
import salud.shared.generated.resources.config_biometria
import salud.shared.generated.resources.config_biometria_apoyo
import salud.shared.generated.resources.config_biometria_no_disponible
import salud.shared.generated.resources.config_bloque_apariencia
import salud.shared.generated.resources.config_bloque_avisos
import salud.shared.generated.resources.config_bloque_cuenta
import salud.shared.generated.resources.config_bloque_idioma
import salud.shared.generated.resources.config_bloque_seguridad
import salud.shared.generated.resources.config_cerrar_sesion_apoyo
import salud.shared.generated.resources.config_forzar_oscuro
import salud.shared.generated.resources.config_idioma_aviso_reinicio
import salud.shared.generated.resources.config_idioma_espanol
import salud.shared.generated.resources.config_idioma_ingles
import salud.shared.generated.resources.config_idioma_sistema
import salud.shared.generated.resources.config_modo_oscuro
import salud.shared.generated.resources.config_modo_oscuro_apoyo
import salud.shared.generated.resources.config_titulo

/**
 * Ajustes locales del paciente.
 *
 * Todo lo de aqui vive en el telefono y no en el expediente: son preferencias
 * del aparato, no datos clinicos. Un recordatorio silenciado en la tableta de
 * casa no debe callar el del movil que el paciente lleva encima.
 *
 * La pantalla se apila sobre la seccion desde la que se abre, asi que conserva
 * la flecha de retroceso y oculta la barra inferior.
 */
@Composable
fun ConfiguracionScreen(
    viewModel: ConfiguracionViewModel,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
    alCerrarSesion: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = espaciado.amplio)
            .margenInferiorSeguro(),
        verticalArrangement = Arrangement.spacedBy(espaciado.amplio),
    ) {
        Spacer(Modifier.height(espaciado.minimo))
        CabeceraGrande(
            titulo = stringResource(Res.string.config_titulo),
            accion = { BotonAtras(alPulsar = alVolver) },
        )

        // ------------------------------------------------------------ Avisos
        TituloDeBloque(stringResource(Res.string.config_bloque_avisos))
        TarjetaSalud {
            // El aviso del sistema va ARRIBA de los interruptores: activarlos
            // con las notificaciones bloqueadas por Android no haria nada, y
            // dejar que el paciente lo descubra solo es una trampa.
            if (estado.avisosBloqueadosPorElSistema) {
                Text(
                    text = stringResource(Res.string.config_avisos_sin_permiso),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoAdvertencia,
                )
                Spacer(Modifier.height(espaciado.compacto))
            }
            FilaDeAjuste(
                titulo = stringResource(Res.string.config_avisos_medicacion),
                apoyo = stringResource(Res.string.config_avisos_medicacion_apoyo),
                activo = estado.recordatoriosDeMedicacion,
                habilitado = !estado.avisosBloqueadosPorElSistema,
                alCambiar = viewModel::cambiarRecordatorios,
            )
            FilaDeAjuste(
                titulo = stringResource(Res.string.config_avisos_mensajes),
                apoyo = stringResource(Res.string.config_avisos_mensajes_apoyo),
                activo = estado.avisosDeMensajes,
                habilitado = !estado.avisosBloqueadosPorElSistema,
                alCambiar = viewModel::cambiarAvisosDeMensajes,
            )
        }

        // ------------------------------------------------------------ Idioma
        TituloDeBloque(stringResource(Res.string.config_bloque_idioma))
        TarjetaSalud {
            val selector = recordarSelectorDeIdioma()
            var elegido by remember { mutableStateOf(selector.actual) }

            IdiomaApp.entries.forEach { idioma ->
                FilaDeOpcion(
                    titulo = stringResource(idioma.recurso()),
                    elegido = idioma == elegido,
                    alPulsar = {
                        elegido = idioma
                        selector.cambiar(idioma)
                    },
                )
            }
            Spacer(Modifier.height(espaciado.compacto))
            // El aviso NO se esconde tras un toque: en iOS y en Android 12 o
            // anterior el cambio no se ve hasta reabrir, y una pantalla que
            // sigue en el idioma viejo tras elegir uno nuevo parece rota.
            Text(
                text = stringResource(Res.string.config_idioma_aviso_reinicio),
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoSecundario,
            )
        }

        // -------------------------------------------------------- Apariencia
        TituloDeBloque(stringResource(Res.string.config_bloque_apariencia))
        TarjetaSalud {
            FilaDeAjuste(
                titulo = stringResource(Res.string.config_modo_oscuro),
                apoyo = stringResource(Res.string.config_modo_oscuro_apoyo),
                activo = estado.seguirModoDelSistema,
                alCambiar = viewModel::cambiarSeguirAlSistema,
            )
            // El interruptor manual solo aparece cuando significa algo: con el
            // sistema al mando, un control que no cambia nada visible es ruido.
            if (!estado.seguirModoDelSistema) {
                FilaDeAjuste(
                    titulo = stringResource(Res.string.config_forzar_oscuro),
                    activo = estado.forzarModoOscuro,
                    alCambiar = viewModel::cambiarModoOscuro,
                )
            }
        }

        // --------------------------------------------------------- Seguridad
        TituloDeBloque(stringResource(Res.string.config_bloque_seguridad))
        TarjetaSalud {
            FilaDeAjuste(
                titulo = stringResource(Res.string.config_biometria),
                apoyo = if (estado.biometriaDisponible) {
                    stringResource(Res.string.config_biometria_apoyo)
                } else {
                    stringResource(Res.string.config_biometria_no_disponible)
                },
                activo = estado.biometriaAlAbrir,
                habilitado = estado.biometriaDisponible,
                alCambiar = viewModel::cambiarBiometria,
            )
        }

        // ------------------------------------------------------------ Cuenta
        TituloDeBloque(stringResource(Res.string.config_bloque_cuenta))
        AccionCerrarSesion(alPulsar = alCerrarSesion)
        Spacer(Modifier.height(espaciado.medio))
    }
}

/**
 * Una fila de ajuste con interruptor.
 *
 * La fila ENTERA es el area tactil, no solo el interruptor: acertar en un
 * control de 50dp de ancho es incomodo, y mas para un paciente mayor o con
 * temblor. Se marca `mergeDescendants` con el estado de conmutacion para que el
 * lector de pantalla anuncie "activado" en vez de leer titulo, apoyo e
 * interruptor como tres elementos sueltos.
 */
@Composable
private fun FilaDeAjuste(
    titulo: String,
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
    apoyo: String? = null,
    habilitado: Boolean = true,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(
                enabled = habilitado,
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = { alCambiar(!activo) },
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.media)
            .semantics(mergeDescendants = true) {
                contentDescription = if (apoyo != null) "$titulo. $apoyo" else titulo
                toggleableState = if (activo) ToggleableState.On else ToggleableState.Off
            }
            .padding(vertical = espaciado.compacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = if (habilitado) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    colores.textoSecundario
                },
            )
            if (apoyo != null) {
                Text(
                    text = apoyo,
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoSecundario,
                )
            }
        }
        Spacer(Modifier.width(espaciado.medio))
        Switch(
            checked = activo,
            onCheckedChange = null,
            enabled = habilitado,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colores.acentoAccion,
                checkedThumbColor = colores.sobreAcentoAccion,
            ),
        )
    }
}

/**
 * Fila de opcion unica (radio), para listas donde solo una vale a la vez.
 *
 * No usa `Switch` a proposito: un interruptor dice "esto se activa o se
 * desactiva", y aqui elegir un idioma APAGA el anterior. Tres interruptores
 * seguidos sugeririan que se pueden tener los tres a la vez.
 */
@Composable
private fun FilaDeOpcion(titulo: String, elegido: Boolean, alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alPulsar,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.media)
            .semantics(mergeDescendants = true) {
                contentDescription = titulo
                selected = elegido
            }
            .padding(vertical = espaciado.compacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = elegido, onClick = null)
        Spacer(Modifier.width(espaciado.medio))
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Puente del idioma a `strings.xml`. */
private fun IdiomaApp.recurso(): StringResource = when (this) {
    IdiomaApp.SISTEMA -> Res.string.config_idioma_sistema
    IdiomaApp.ESPANOL -> Res.string.config_idioma_espanol
    IdiomaApp.INGLES -> Res.string.config_idioma_ingles
}

/**
 * Salir de la cuenta SIN borrar nada.
 *
 * Antes la unica salida era "Cerrar sesion y borrar datos locales", que ademas
 * no borraba nada: solo cerraba la sesion. Ahora que el expediente vive en la
 * base del telefono, salir es lo que la persona espera -- dejar el telefono
 * listo para otra cuenta -- y sus datos siguen ahi al volver. Por eso no va en
 * rojo ni pide confirmacion: no hay nada irreversible que confirmar.
 */
@Composable
private fun AccionCerrarSesion(alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_accion_cerrar_sesion)

    TarjetaSalud {
        Text(
            text = stringResource(Res.string.config_cerrar_sesion_apoyo),
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoSecundario,
        )
        TextButton(
            onClick = alPulsar,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcion },
            shape = FormaSalud.media,
        ) {
            Text(
                text = stringResource(Res.string.accion_cerrar_sesion),
                style = MaterialTheme.typography.titleMedium,
                color = colores.acentoAccion,
                modifier = Modifier.padding(vertical = espaciado.compacto),
            )
        }
    }
}
