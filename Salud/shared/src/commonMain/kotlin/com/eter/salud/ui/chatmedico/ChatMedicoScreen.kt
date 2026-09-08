package com.eter.salud.ui.chatmedico

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.chatmedico.ChatMedicoUiState
import com.eter.salud.presentation.chatmedico.ChatMedicoViewModel
import com.eter.salud.presentation.chatmedico.HistorialChatUiState
import com.eter.salud.presentation.chatmedico.MensajeVisibleChat
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.componentes.BurbujaMensaje
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.profesional.recurso
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.ColoresSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_boton_atras
import salud.shared.generated.resources.a11y_chatmedico_accion_enviar
import salud.shared.generated.resources.a11y_chatmedico_accion_expediente
import salud.shared.generated.resources.a11y_chatmedico_cabecera
import salud.shared.generated.resources.a11y_chatmedico_campo_mensaje
import salud.shared.generated.resources.a11y_chatmedico_cargando
import salud.shared.generated.resources.a11y_chatmedico_mensaje_paciente
import salud.shared.generated.resources.a11y_chatmedico_mensaje_propio
import salud.shared.generated.resources.accion_atras
import salud.shared.generated.resources.chatmedico_accion_expediente
import salud.shared.generated.resources.chatmedico_campo_placeholder
import salud.shared.generated.resources.chatmedico_estado_cargando
import salud.shared.generated.resources.chatmedico_estado_error_carga
import salud.shared.generated.resources.chatmedico_estado_error_envio
import salud.shared.generated.resources.chatmedico_sin_mensajes

/**
 * Chat clinico visto desde el perfil del medico.
 *
 * La cabecera lleva el contexto que el medico necesita sin salir de la
 * conversacion: a quien responde y con que urgencia (semaforo de riesgo), mas
 * el atajo al expediente completo.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, iconografia vectorial
 * estandar, color y espaciado solo por tokens semanticos, Modo Oscuro
 * automatico, y el campo de texto respeta los insets del teclado para no
 * quedar nunca oculto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMedicoScreen(
    viewModel: ChatMedicoViewModel,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
    alAbrirExpediente: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val listState = rememberLazyListState()

    // Auto-scroll al ultimo mensaje en cuanto llega uno nuevo.
    LaunchedEffect(estado.mensajes.size) {
        if (estado.mensajes.isNotEmpty()) {
            listState.animateScrollToItem(estado.mensajes.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colores.fondoConversacion,
        topBar = {
            CabeceraClinica(
                estado = estado,
                alVolver = alVolver,
                alAbrirExpediente = alAbrirExpediente,
            )
        },
        // El campo vive en la barra inferior, que ya aplica `imePadding()` y el
        // inset inferior del sistema: el teclado nunca lo tapa.
        bottomBar = {
            Column {
                RespuestasRapidasFila(alElegir = viewModel::usarRespuestaRapida)
                Spacer(Modifier.height(espaciado.compacto))
                BarraAccionInferior {
                    FilaEnvio(estado = estado, viewModel = viewModel)
                }
            }
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            when (val historial = estado.historial) {
                HistorialChatUiState.Cargando -> IndicadorCargando()

                is HistorialChatUiState.Error -> MensajeErrorCarga()

                is HistorialChatUiState.ConMensajes -> {
                    if (historial.mensajes.isEmpty()) {
                        MensajeSinMensajes()
                    } else {
                        LazyColumn(
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
                            items(historial.mensajes, key = { it.idMensaje }) { mensaje ->
                                MensajeDelChat(mensaje = mensaje, nombrePaciente = estado.nombrePaciente)
                            }
                        }
                    }
                }
            }

            if (estado.errorEnvio) {
                Text(
                    text = stringResource(Res.string.chatmedico_estado_error_envio),
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
}

/** Nombre del paciente, semaforo de riesgo y atajo al expediente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CabeceraClinica(
    estado: ChatMedicoUiState,
    alVolver: () -> Unit,
    alAbrirExpediente: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val etiquetaRiesgo = stringResource(estado.riesgoPaciente.recurso())
    val descripcionCabecera = stringResource(
        Res.string.a11y_chatmedico_cabecera,
        estado.nombrePaciente,
        etiquetaRiesgo,
    )
    val descripcionExpediente = stringResource(
        Res.string.a11y_chatmedico_accion_expediente,
        estado.nombrePaciente,
    )

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = descripcionCabecera
                },
            ) {
                // Semaforo sutil: un punto, no un bloque de color que compita
                // con la conversacion.
                Box(
                    Modifier
                        .size(TAMANO_PUNTO_RIESGO)
                        .background(estado.riesgoPaciente.color(colores), CircleShape),
                )
                Column {
                    Text(
                        text = estado.nombrePaciente,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = etiquetaRiesgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.textoSecundario,
                    )
                }
            }
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
            TextButton(
                onClick = alAbrirExpediente,
                modifier = Modifier
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcionExpediente },
            ) {
                Text(stringResource(Res.string.chatmedico_accion_expediente))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colores.fondoConversacion),
    )
}

/** Semaforo de riesgo: solo el alto reclama el tono critico. */
@Composable
private fun RiesgoPaciente.color(colores: ColoresSalud): Color = when (this) {
    RiesgoPaciente.ALTO -> colores.acentoCritico
    RiesgoPaciente.MEDIO -> MaterialTheme.colorScheme.onSurfaceVariant
    RiesgoPaciente.BAJO -> colores.exito
}

@Composable
private fun MensajeDelChat(mensaje: MensajeVisibleChat, nombrePaciente: String) {
    val descripcion = if (mensaje.esPropio) {
        stringResource(Res.string.a11y_chatmedico_mensaje_propio, mensaje.texto, mensaje.horaLocal)
    } else {
        stringResource(
            Res.string.a11y_chatmedico_mensaje_paciente,
            nombrePaciente,
            mensaje.texto,
            mensaje.horaLocal,
        )
    }

    BurbujaMensaje(
        texto = mensaje.texto,
        horaLocal = mensaje.horaLocal,
        esPropio = mensaje.esPropio,
        descripcionAccesible = descripcion,
    )
}

/** Campo expandible y boton de enviar. */
@Composable
private fun FilaEnvio(estado: ChatMedicoUiState, viewModel: ChatMedicoViewModel) {
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
        BotonEnviar(habilitado = estado.puedeEnviar, alPulsar = viewModel::enviarMensaje)
    }
}

/**
 * Sin `singleLine`: crece con el texto hasta un tope de lineas, como en
 * cualquier chat nativo, en vez de quedarse en una fila desde el primer
 * caracter.
 */
@Composable
private fun CampoMensaje(valor: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chatmedico_campo_mensaje)
    val marcador = stringResource(Res.string.chatmedico_campo_placeholder)

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
    val descripcion = stringResource(Res.string.a11y_chatmedico_accion_enviar)
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
    val descripcion = stringResource(Res.string.a11y_chatmedico_cargando)
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
            text = stringResource(Res.string.chatmedico_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MensajeErrorCarga() {
    Box(Modifier.fillMaxSize().padding(LocalEspaciadoSalud.current.amplio)) {
        Text(
            text = stringResource(Res.string.chatmedico_estado_error_carga),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/** Paciente vinculado que aun no escribe: el medico puede abrir la orientacion. */
@Composable
private fun MensajeSinMensajes() {
    Box(Modifier.fillMaxSize().padding(LocalEspaciadoSalud.current.amplio)) {
        Text(
            text = stringResource(Res.string.chatmedico_sin_mensajes),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresSalud.current.textoSecundario,
        )
    }
}

private val TAMANO_PUNTO_RIESGO = 10.dp
private const val MAXIMO_LINEAS_CAMPO = 5
