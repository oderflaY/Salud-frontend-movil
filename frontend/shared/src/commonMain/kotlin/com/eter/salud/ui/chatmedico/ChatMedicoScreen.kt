package com.eter.salud.ui.chatmedico

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eter.salud.domain.adjuntos.rememberAbridorDeAdjuntos
import com.eter.salud.domain.adjuntos.rememberSelectorDeAdjuntos
import com.eter.salud.ui.chat.BotonAdjuntar
import com.eter.salud.ui.chat.ChipDeAdjunto
import com.eter.salud.ui.chat.HojaAdjuntar
import com.eter.salud.ui.chat.VistaPreviaAdjunto
import salud.shared.generated.resources.a11y_chat_mensaje_con_adjunto
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.chatmedico.ChatMedicoUiState
import com.eter.salud.presentation.chatmedico.ChatMedicoViewModel
import com.eter.salud.presentation.chatmedico.EstadoResumenIa
import com.eter.salud.presentation.chatmedico.HistorialChatUiState
import com.eter.salud.presentation.chatmedico.MensajeVisibleChat
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.presentation.comun.EstadoTraduccion
import com.eter.salud.ui.componentes.AccionTraducir
import com.eter.salud.ui.componentes.EstadoBurbujaTraducida
import com.eter.salud.ui.componentes.MensajeTraducido
import com.eter.salud.ui.componentes.TranslatedMessageBubble
import com.eter.salud.ui.componentes.BurbujaMensaje
import com.eter.salud.ui.componentes.ClinicalAiSummaryCard
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.profesional.recurso
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.ColoresSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.componentes.MientrasSeVe
import androidx.compose.foundation.lazy.itemsIndexed
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.ui.componentes.EstadoVacioDeChat
import com.eter.salud.ui.componentes.MenuDeOpciones
import com.eter.salud.ui.componentes.OpcionDeMenu
import com.eter.salud.ui.componentes.SeparadorDeFecha
import com.eter.salud.ui.componentes.TituloDeChat
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.dictado.BotonDictar
import com.eter.salud.ui.dictado.PanelDeDictado
import com.eter.salud.ui.dictado.rememberControladorDeDictado
import salud.shared.generated.resources.dictado_accion_detener_y_enviar
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.chat_vacio_medico
import salud.shared.generated.resources.a11y_chat_accion_traduccion_automatica
import salud.shared.generated.resources.chat_accion_ver_originales
import salud.shared.generated.resources.chat_accion_traducir_todo
import salud.shared.generated.resources.a11y_chatmedico_accion_enviar
import salud.shared.generated.resources.a11y_chatmedico_accion_expediente
import salud.shared.generated.resources.a11y_chatmedico_cabecera
import salud.shared.generated.resources.a11y_chatmedico_campo_mensaje
import salud.shared.generated.resources.a11y_chatmedico_cargando
import salud.shared.generated.resources.a11y_chatmedico_mensaje_paciente
import salud.shared.generated.resources.a11y_chatmedico_mensaje_propio
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
    val hoy = remember { relojDelSistema().fechaHoy() }

    // Mensajes nuevos (del telefono de la otra parte o del panel web) sin salir del chat.
    MientrasSeVe(viewModel) { viewModel.mantenerAlDia() }

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
                alCambiarTraduccion = viewModel::cambiarTraduccionAutomatica,
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

                is HistorialChatUiState.Error -> BloqueDeError(
                    mensaje = stringResource(Res.string.chatmedico_estado_error_carga),
                    alReintentar = viewModel::cargar,
                )

                is HistorialChatUiState.ConMensajes -> {
                    if (historial.mensajes.isEmpty()) {
                        EstadoVacioDeChat(
                            descripcion = stringResource(Res.string.chat_vacio_medico, estado.nombrePaciente),
                            modifier = Modifier.weight(1f),
                        )
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
                            itemsIndexed(historial.mensajes, key = { _, mensaje -> mensaje.idMensaje }) { indice, mensaje ->
                                if (mensaje.fechaLocal != historial.mensajes.getOrNull(indice - 1)?.fechaLocal) {
                                    SeparadorDeFecha(
                                        fechaLocal = mensaje.fechaLocal,
                                        hoy = hoy,
                                        modifier = Modifier.padding(bottom = espaciado.medio),
                                    )
                                }
                                MensajeDelChat(
                                    mensaje = mensaje,
                                    nombrePaciente = estado.nombrePaciente,
                                    resumenIa = estado.resumenesIa[mensaje.idMensaje],
                                    originalExpandido = mensaje.idMensaje in estado.originalExpandido,
                                    alAlternarOriginal = { viewModel.alternarOriginal(mensaje.idMensaje) },
                                    traduccion = estado.traducciones[mensaje.idMensaje],
                                    // Solo lo que escribio el paciente: el medico
                                    // no necesita traducir sus propias respuestas.
                                    puedeTraducir = estado.puedeTraducir && !mensaje.esPropio,
                                    alTraducir = { viewModel.traducir(mensaje.idMensaje) },
                                    alAlternarTraduccion = { viewModel.alternarTraduccion(mensaje.idMensaje) },
                                )
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
    alCambiarTraduccion: (Boolean) -> Unit = {},
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
            TituloDeChat(
                nombre = estado.nombrePaciente,
                subtitulo = etiquetaRiesgo,
                colorDeEstado = estado.riesgoPaciente.color(colores),
                modifier = Modifier.semantics { contentDescription = descripcionCabecera },
            )
        },
        navigationIcon = { BotonAtras(alPulsar = alVolver) },
        actions = {
            TextButton(
                onClick = alAbrirExpediente,
                modifier = Modifier
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcionExpediente },
            ) {
                Text(stringResource(Res.string.chatmedico_accion_expediente), maxLines = 1)
            }
            // Traducir va en el menu: como segundo boton de texto le quitaba
            // al nombre del paciente el ancho de la barra.
            MenuDeOpciones(
                buildList {
                    if (estado.puedeTraducir) {
                        val activa = estado.traduccionAutomatica
                        add(
                            OpcionDeMenu(
                                etiqueta = stringResource(
                                    if (activa) Res.string.chat_accion_ver_originales else Res.string.chat_accion_traducir_todo,
                                ),
                                alElegir = { alCambiarTraduccion(!activa) },
                            ),
                        )
                    }
                },
            )
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

/**
 * Si el mensaje del paciente ya tiene (o esta esperando) un resumen de IA,
 * se pinta la [ClinicalAiSummaryCard] en vez de la burbuja normal: son
 * mutuamente excluyentes, nunca las dos a la vez para el mismo mensaje.
 */
@Composable
private fun MensajeDelChat(
    mensaje: MensajeVisibleChat,
    nombrePaciente: String,
    resumenIa: EstadoResumenIa?,
    originalExpandido: Boolean,
    alAlternarOriginal: () -> Unit,
    traduccion: EstadoTraduccion? = null,
    puedeTraducir: Boolean = false,
    alTraducir: () -> Unit = {},
    alAlternarTraduccion: () -> Unit = {},
) {
    // Traducido: la burbuja con traduccion reemplaza a la normal y deja el
    // original del paciente a un toque.
    if (traduccion is EstadoTraduccion.Lista) {
        TranslatedMessageBubble(
            estado = if (traduccion.mostrandoOriginal) {
                EstadoBurbujaTraducida.Original(MensajeTraducido(traduccion.texto, mensaje.texto))
            } else {
                EstadoBurbujaTraducida.Traducido(MensajeTraducido(traduccion.texto, mensaje.texto))
            },
            horaLocal = mensaje.horaLocal,
            esPropio = mensaje.esPropio,
            esDelMedico = mensaje.autor == AutorMensaje.MEDICO,
            alAlternar = alAlternarTraduccion,
        )
        return
    }
    if (resumenIa != null) {
        ClinicalAiSummaryCard(
            estado = resumenIa,
            original = originalExpandido,
            alAlternarOriginal = alAlternarOriginal,
        )
        return
    }

    val descripcionBase = if (mensaje.esPropio) {
        stringResource(Res.string.a11y_chatmedico_mensaje_propio, mensaje.texto, mensaje.horaLocal)
    } else {
        stringResource(
            Res.string.a11y_chatmedico_mensaje_paciente,
            nombrePaciente,
            mensaje.texto,
            mensaje.horaLocal,
        )
    }
    val adjunto = mensaje.adjunto
    val descripcion = if (adjunto != null) {
        descripcionBase + " " + stringResource(Res.string.a11y_chat_mensaje_con_adjunto, adjunto.nombre)
    } else {
        descripcionBase
    }
    val abridor = rememberAbridorDeAdjuntos()

    val colores = LocalColoresSalud.current
    Column(verticalArrangement = Arrangement.spacedBy(LocalEspaciadoSalud.current.minimo)) {
        BurbujaMensaje(
            texto = mensaje.texto,
            horaLocal = mensaje.horaLocal,
            esPropio = mensaje.esPropio,
            // Aqui coinciden -- el medico ES quien mira -- pero se pasan por
            // separado: son dos preguntas distintas y fundirlas volveria a atar el
            // color a la propiedad del mensaje.
            esDelMedico = mensaje.autor == AutorMensaje.MEDICO,
            descripcionAccesible = descripcion,
            adjunto = adjunto?.let { archivo ->
                { colorTexto -> ChipDeAdjunto(adjunto = archivo, colorTexto = colorTexto, abridor = abridor) }
            },
        )
        // Fuera de la burbuja: la del paciente es gris y un boton dentro
        // competiria con el texto clinico, que es lo que hay que leer.
        if (puedeTraducir && mensaje.texto.isNotBlank()) {
            AccionTraducir(estado = traduccion, colorTexto = colores.textoSecundario, alTraducir = alTraducir)
        }
    }
}

/**
 * Vista previa del adjunto, boton de adjuntar (foto, archivo o escaner), campo
 * expandible y boton de enviar: lo mismo que el chat del paciente, porque el
 * medico tambien manda recetas, ordenes de laboratorio y estudios.
 */
@Composable
private fun FilaEnvio(estado: ChatMedicoUiState, viewModel: ChatMedicoViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val selectorDeAdjuntos = rememberSelectorDeAdjuntos(estado.idConversacion)
    var mostrandoHojaAdjuntar by remember { mutableStateOf(false) }
    val dictado = rememberControladorDeDictado(viewModel::actualizarTexto)

    Column {
        PanelDeDictado(
            controlador = dictado,
            alEnviar = viewModel::enviarMensaje,
            etiquetaEnviar = Res.string.dictado_accion_detener_y_enviar,
            modifier = Modifier.padding(bottom = espaciado.compacto),
        )
        estado.adjuntoEnCurso?.let { adjunto ->
            VistaPreviaAdjunto(adjunto = adjunto, alQuitar = viewModel::quitarAdjunto)
            Spacer(Modifier.height(espaciado.compacto))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            BotonAdjuntar(alPulsar = { mostrandoHojaAdjuntar = true })
            CampoMensaje(
                valor = estado.textoEnCurso,
                alCambiar = viewModel::actualizarTexto,
                modifier = Modifier.weight(1f),
            )
            BotonDictar(dictado, textoActual = { viewModel.estado.value.textoEnCurso })
            BotonEnviar(habilitado = estado.puedeEnviar, alPulsar = { dictado.detenerYEnviar(viewModel::enviarMensaje) })
        }
    }

    if (mostrandoHojaAdjuntar) {
        HojaAdjuntar(
            selector = selectorDeAdjuntos,
            alAdjuntar = viewModel::adjuntar,
            onDismissRequest = { mostrandoHojaAdjuntar = false },
        )
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
            .background(colores.fondoCampo, FormaSalud.destacada)
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



private val TAMANO_PUNTO_RIESGO = 10.dp
private const val MAXIMO_LINEAS_CAMPO = 5
