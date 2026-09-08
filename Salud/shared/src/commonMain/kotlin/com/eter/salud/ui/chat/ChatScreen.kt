package com.eter.salud.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.presentation.chat.ChatUiState
import com.eter.salud.presentation.chat.ChatViewModel
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_boton_atras
import salud.shared.generated.resources.a11y_chat_accion_enviar
import salud.shared.generated.resources.a11y_chat_banner_advertencia
import salud.shared.generated.resources.a11y_chat_campo_mensaje
import salud.shared.generated.resources.a11y_chat_cargando
import salud.shared.generated.resources.a11y_chat_estado_escribiendo
import salud.shared.generated.resources.a11y_chat_mensaje_medico
import salud.shared.generated.resources.a11y_chat_mensaje_orientacion
import salud.shared.generated.resources.a11y_chat_mensaje_paciente
import salud.shared.generated.resources.accion_atras
import salud.shared.generated.resources.a11y_cita_accion_abrir
import salud.shared.generated.resources.chat_banner_advertencia
import salud.shared.generated.resources.cita_accion_abrir
import salud.shared.generated.resources.chat_campo_placeholder
import salud.shared.generated.resources.chat_estado_cargando
import salud.shared.generated.resources.chat_estado_error_carga
import salud.shared.generated.resources.chat_estado_error_envio
import salud.shared.generated.resources.chat_estado_escribiendo
import salud.shared.generated.resources.chat_orientacion_inicial_etiqueta

/**
 * Chat de orientacion de primera vista.
 *
 * Solo existe una vez que "Mi Medico" ya resolvio una vinculacion: `App.kt` lo
 * instancia con su propio [ChatViewModel], igual que decide entre Onboarding y
 * Perfil para el paciente. Esta pantalla no sabe que el Directorio existe.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, burbujas con los tokens
 * de color exactos del diseno, banner de advertencia siempre visible, Modo
 * Oscuro automatico y el campo de texto respeta los insets del teclado para
 * nunca quedar oculto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
    /**
     * Recibe el texto que el paciente acaba de enviar, YA enviado al chat.
     *
     * Es el enganche del agendamiento: el modulo de citas mira si la frase pide
     * una cita y abre su panel. Se notifica despues de enviar, y no en lugar de
     * enviar, para que el paciente vea siempre su propio mensaje en la
     * conversacion aunque el detector se equivoque.
     */
    alEnviarMensaje: (String) -> Unit = {},
    /** Accion explicita de agendar; se oculta si esta pantalla no la ofrece. */
    alAgendarCita: (() -> Unit)? = null,
    /** Ranura para el panel de agendamiento, que se dibuja sobre la conversacion. */
    panelAgenda: @Composable () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val listState = rememberLazyListState()

    LaunchedEffect(estado.mensajes.size, estado.medicoEscribiendo) {
        val ultimoIndice = estado.mensajes.lastIndex + if (estado.medicoEscribiendo) 1 else 0
        if (ultimoIndice >= 0) listState.animateScrollToItem(ultimoIndice)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colores.fondoConversacion,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = estado.nombreMedico,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    val descripcion = stringResource(Res.string.a11y_boton_atras)
                    TextButton(
                        onClick = alVolver,
                        modifier = Modifier
                            .heightIn(min = AreaTactilMinima)
                            .semantics { contentDescription = descripcion },
                    ) {
                        Text(stringResource(Res.string.accion_atras))
                    }
                },
                actions = {
                    // La deteccion por frase no basta como unica puerta: si el
                    // paciente no acierta con las palabras, la funcion no
                    // existiria para el. Este boton la hace descubrible.
                    if (alAgendarCita != null) {
                        val descripcionAgendar = stringResource(Res.string.a11y_cita_accion_abrir)
                        TextButton(
                            onClick = alAgendarCita,
                            modifier = Modifier
                                .heightIn(min = AreaTactilMinima)
                                .semantics { contentDescription = descripcionAgendar },
                        ) {
                            Text(stringResource(Res.string.cita_accion_abrir))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colores.fondoConversacion),
            )
        },
        // El campo de texto vive en la barra inferior, que ya respeta el inset
        // del teclado (imePadding) y el safe area del sistema: nunca queda
        // tapado, tanto si el teclado esta abierto como si el telefono tiene
        // gestos en la parte de abajo.
        bottomBar = {
            BarraAccionInferior {
                FilaEnvio(
                    estado = estado,
                    viewModel = viewModel,
                    alEnviarMensaje = alEnviarMensaje,
                )
            }
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            BannerAdvertencia()

            when {
                estado.cargando -> IndicadorCargando()
                estado.errorCarga -> MensajeErrorCarga()
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = espaciado.amplio,
                        vertical = espaciado.medio,
                    ),
                    verticalArrangement = Arrangement.spacedBy(espaciado.medio),
                ) {
                    items(estado.mensajes, key = { it.idMensaje }) { mensaje ->
                        FilaMensaje(mensaje = mensaje, nombreMedico = estado.nombreMedico)
                    }
                    if (estado.medicoEscribiendo) {
                        item(key = "escribiendo") {
                            IndicadorEscribiendo(estado.nombreMedico)
                        }
                    }
                }
            }

            if (estado.errorEnvio) {
                Text(
                    text = stringResource(Res.string.chat_estado_error_envio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = espaciado.amplio, vertical = espaciado.compacto)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    panelAgenda()
}

/**
 * Aviso permanente de que esto es orientacion y no un diagnostico. Va fijo
 * arriba de la conversacion, nunca dentro de las burbujas: es una condicion
 * del servicio, no un mensaje mas.
 */
@Composable
private fun BannerAdvertencia() {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chat_banner_advertencia)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = descripcion },
        color = colores.fondoAdvertencia,
    ) {
        Text(
            text = stringResource(Res.string.chat_banner_advertencia),
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoAdvertencia,
            modifier = Modifier.padding(horizontal = espaciado.amplio, vertical = espaciado.compacto),
        )
    }
}

@Composable
private fun FilaMensaje(mensaje: MensajeChat, nombreMedico: String) {
    if (mensaje.tipo == TipoMensaje.ORIENTACION_INICIAL) {
        BloqueOrientacionInicial(mensaje)
        return
    }
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val esPaciente = mensaje.autor == AutorMensaje.PACIENTE
    val descripcion = if (esPaciente) {
        stringResource(Res.string.a11y_chat_mensaje_paciente, mensaje.texto)
    } else {
        stringResource(Res.string.a11y_chat_mensaje_medico, nombreMedico, mensaje.texto)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (esPaciente) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = ANCHO_MAXIMO_BURBUJA)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
            color = if (esPaciente) MaterialTheme.colorScheme.primary else colores.fondoBurbujaMedico,
            shape = RoundedCornerShape(espaciado.medio),
        ) {
            Text(
                text = mensaje.texto,
                style = MaterialTheme.typography.bodyLarge,
                color = if (esPaciente) MaterialTheme.colorScheme.onPrimary else colores.sobreBurbujaMedico,
                modifier = Modifier.padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
            )
        }
    }
}

/**
 * El bloque destacado de Orientacion Inicial: soporte vital inmediato de parte
 * del medico. No es una burbuja mas -- ocupa el ancho completo y lleva una
 * franja de acento, como las tarjetas criticas del modulo de emergencia.
 */
@Composable
private fun BloqueOrientacionInicial(mensaje: MensajeChat) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chat_mensaje_orientacion, mensaje.texto)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.fondoTarjeta,
        shape = RoundedCornerShape(espaciado.medio),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .width(FRANJA_ACENTO)
                    .background(colores.acentoAccion),
            )
            Column(Modifier.padding(espaciado.amplio)) {
                Text(
                    text = stringResource(Res.string.chat_orientacion_inicial_etiqueta),
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.acentoAccion,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(espaciado.compacto))
                Text(
                    text = mensaje.texto,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun IndicadorEscribiendo(nombreMedico: String) {
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    val texto = stringResource(Res.string.chat_estado_escribiendo, nombreMedico)
    val descripcion = stringResource(Res.string.a11y_chat_estado_escribiendo, nombreMedico)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = colores.fondoBurbujaMedico,
            shape = RoundedCornerShape(espaciado.medio),
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = colores.sobreBurbujaMedico,
                modifier = Modifier.padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
            )
        }
    }
}

/** Campo de texto expandible y boton de enviar, anclados a la zona segura inferior. */
@Composable
private fun FilaEnvio(
    estado: ChatUiState,
    viewModel: ChatViewModel,
    alEnviarMensaje: (String) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        CampoMensaje(
            valor = estado.textoEnCurso,
            alCambiar = viewModel::actualizarTexto,
            modifier = Modifier.weight(1f),
        )
        BotonEnviar(
            habilitado = estado.puedeEnviar,
            alPulsar = {
                // El texto se lee ANTES de enviar: `enviarMensaje` limpia el
                // campo, asi que leerlo despues daria siempre cadena vacia.
                val texto = estado.textoEnCurso
                viewModel.enviarMensaje()
                alEnviarMensaje(texto)
            },
        )
    }
}

/**
 * Campo del mensaje. Sin `singleLine`: crece con el texto hasta un tope de
 * lineas, igual que en iMessage o WhatsApp, en vez de quedarse en una sola
 * fila y obligar a scrollear dentro de si mismo desde el primer caracter.
 */
@Composable
private fun CampoMensaje(valor: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chat_campo_mensaje)
    val marcador = stringResource(Res.string.chat_campo_placeholder)

    Box(
        modifier = modifier
            .heightIn(min = AreaTactilMinima)
            .background(colores.fondoCampo, RoundedCornerShape(espaciado.generoso))
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (valor.isEmpty()) {
            Text(
                text = marcador,
                style = MaterialTheme.typography.bodyLarge,
                color = colores.textoSecundario,
            )
        }
        BasicTextField(
            value = valor,
            onValueChange = alCambiar,
            maxLines = MAXIMO_LINEAS_CAMPO,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BotonEnviar(habilitado: Boolean, alPulsar: () -> Unit) {
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chat_accion_enviar)
    val colorFondo = if (habilitado) colores.acentoAccion else colores.fondoCampo
    val colorIcono = if (habilitado) colores.sobreAcentoAccion else colores.textoSecundario

    Surface(
        modifier = Modifier
            .size(AreaTactilMinima)
            .then(if (habilitado) Modifier.clickable(onClick = alPulsar) else Modifier)
            .semantics { contentDescription = descripcion },
        color = colorFondo,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            IconoSalud(glifo = GlifoSalud.ENVIAR, lado = 20.dp, color = colorIcono)
        }
    }
}

@Composable
private fun IndicadorCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_chat_cargando)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(espaciado.amplio)
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.chat_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MensajeErrorCarga() {
    Box(Modifier.fillMaxSize().padding(LocalEspaciadoSalud.current.amplio)) {
        Text(
            text = stringResource(Res.string.chat_estado_error_carga),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

private val ANCHO_MAXIMO_BURBUJA = 280.dp
private val FRANJA_ACENTO = 4.dp
private const val MAXIMO_LINEAS_CAMPO = 5
