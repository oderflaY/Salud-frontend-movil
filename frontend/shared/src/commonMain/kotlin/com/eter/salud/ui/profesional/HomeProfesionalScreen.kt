// Hallmark - redesign 2026-09-09 - genero: modern-minimal - macroestructura: Workbench
// critica pre-emision: P5 H5 E4 S5 R5 V5
// tema: Biotech Premium (tokens propios) - acento: Sapphire / NeonSky
// semaforo de triage: Emerald / Amber / Crimson, SIEMPRE acompanado de palabra
package com.eter.salud.ui.profesional

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.profesional.HomeProfesionalUiState
import com.eter.salud.presentation.profesional.HomeProfesionalViewModel
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.CabeceraPortada
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.PuntoDeTriage
import com.eter.salud.ui.componentes.bordeDeTarjeta
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.diario.recurso
import com.eter.salud.ui.diario.tinta
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_accion_ajustes
import salud.shared.generated.resources.a11y_profesional_home_accion_agenda
import salud.shared.generated.resources.a11y_profesional_home_accion_escanear
import salud.shared.generated.resources.a11y_profesional_home_cargando
import salud.shared.generated.resources.a11y_profesional_home_paciente_abrir
import salud.shared.generated.resources.a11y_profesional_home_paciente_completo
import salud.shared.generated.resources.a11y_profesional_paciente_diario
import salud.shared.generated.resources.a11y_profesional_paciente_sin_diario
import salud.shared.generated.resources.accion_ajustes
import salud.shared.generated.resources.profesional_home_accion_agenda
import salud.shared.generated.resources.profesional_home_accion_escanear
import salud.shared.generated.resources.profesional_home_cedula_pendiente
import salud.shared.generated.resources.profesional_home_cedula_verificada
import salud.shared.generated.resources.profesional_home_estado_cargando
import salud.shared.generated.resources.profesional_home_estado_error
import salud.shared.generated.resources.profesional_home_pacientes_conteo
import salud.shared.generated.resources.profesional_home_pacientes_sin_datos
import salud.shared.generated.resources.profesional_home_pacientes_titulo
import salud.shared.generated.resources.profesional_home_saludo

/**
 * Panel principal del profesional de la salud.
 *
 * ## La macroestructura: Workbench
 *
 * Un banco de trabajo tiene una herramienta dominante y, al lado, la lista de
 * lo que hay que atender. Aqui la herramienta es el escaneo de emergencia y la
 * lista es la cartera de pacientes, ordenada por triage.
 *
 * El escaneo ocupa un bloque entero en el color de accion y no comparte fila ni
 * peso con nada. Es el unico control que se pulsa con un paciente inconsciente
 * delante, y en esa situacion no puede haber que elegir entre dos tarjetas
 * parecidas. Por eso tampoco se convirtio en un boton flotante: un flotante es
 * una accion secundaria que no quiere robar sitio, y esta es exactamente la
 * contraria.
 *
 * ## La lista, limpia
 *
 * Los pacientes se listan sin tarjeta y sin una sola linea divisoria: solo aire
 * y la columna continua de los avatares. Una lista clinica que hay que recorrer
 * bajo presion no necesita bordes, necesita que nada estorbe entre una cara y
 * la siguiente.
 *
 * ## Puerta unica al escaner
 *
 * Este boton sigue siendo la unica forma de instanciar el escaner de emergencia,
 * y solo se llega a esta pantalla tras autenticarse como personal medico.
 */
@Composable
fun HomeProfesionalScreen(
    viewModel: HomeProfesionalViewModel,
    modifier: Modifier = Modifier,
    alEscanearTarjeta: () -> Unit = {},
    alAbrirAgenda: () -> Unit = {},
    alAbrirChat: (PacienteVinculado) -> Unit = {},
    alAbrirAjustes: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val margen = Modifier.padding(horizontal = espaciado.amplio)

    LaunchedEffect(estado.idMedico) { viewModel.cargar() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentPadding = PaddingValues(
            top = espaciado.medio,
            bottom = espaciado.respiro,
        ),
    ) {
        item(key = "cabecera") {
            Cabecera(estado, alAbrirAjustes, margen)
            Spacer(Modifier.height(espaciado.generoso))
        }
        item(key = "escaneo") {
            AccionEscaneo(alEscanearTarjeta, margen)
            Spacer(Modifier.height(espaciado.medio))
        }
        item(key = "agenda") {
            AccionAgenda(alAbrirAgenda, margen)
            Spacer(Modifier.height(espaciado.generoso))
        }

        item(key = "titulo_pacientes") {
            CabeceraDeCartera(estado, margen)
            Spacer(Modifier.height(espaciado.compacto))
        }

        when {
            estado.cargando -> item(key = "cargando") { IndicadorCargando() }
            estado.errorCarga -> item(key = "error") {
                BloqueDeError(
                    mensaje = stringResource(Res.string.profesional_home_estado_error),
                    alReintentar = viewModel::cargar,
                    modifier = margen,
                )
            }

            estado.pacientesVinculados.isEmpty() -> item(key = "vacio") { SinPacientes(margen) }
            else -> items(estado.pacientesVinculados, key = { it.idPaciente }) { paciente ->
                FilaPaciente(
                    paciente = paciente,
                    severidadDelDiario = estado.severidadDelDiario[paciente.idPaciente],
                    alAbrir = { alAbrirChat(paciente) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Cabecera

@Composable
private fun Cabecera(
    estado: HomeProfesionalUiState,
    alAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    val verificada = estado.cedulaVerificada
    val textoCedula = stringResource(
        if (verificada) Res.string.profesional_home_cedula_verificada
        else Res.string.profesional_home_cedula_pendiente,
    )
    val tinta = if (verificada) colores.exito else colores.textoAdvertencia

    Column(modifier) {
        CabeceraPortada(
            saludo = stringResource(
                Res.string.profesional_home_saludo,
                estado.tratamiento,
                estado.apellidos,
            ),
            titulo = "${estado.tratamiento} ${estado.apellidos}".trim(),
            // El portal profesional no tenia forma de llegar a Configuracion:
            // la pantalla existia y era inalcanzable para un medico.
            accion = {
                val descripcion = stringResource(Res.string.a11y_accion_ajustes)
                TextButton(
                    onClick = alAbrirAjustes,
                    modifier = Modifier
                        .heightIn(min = AreaTactilMinima)
                        .semantics { contentDescription = descripcion },
                ) {
                    Text(
                        text = stringResource(Res.string.accion_ajustes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            },
        )
        Spacer(Modifier.height(espaciado.medio))
        Surface(
            color = if (verificada) colores.fondoCitaConfirmada else colores.fondoAdvertencia,
            shape = FormaSalud.pastilla,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = textoCedula
            },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = espaciado.medio,
                    vertical = espaciado.compacto,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoSalud(
                    glifo = if (verificada) GlifoSalud.VERIFICADO else GlifoSalud.HISTORIAL,
                    lado = MedidaSalud.glifoEnPildora,
                    color = tinta,
                )
                Spacer(Modifier.width(espaciado.compacto))
                Text(text = textoCedula, style = MaterialTheme.typography.labelLarge, color = tinta)
            }
        }
    }
}

// ------------------------------------------------------------------ Acciones

/**
 * La accion de emergencia. Bloque entero en el color de accion, sin nada que
 * compita: es lo que se pulsa con un paciente inconsciente delante.
 */
@Composable
private fun AccionEscaneo(alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_profesional_home_accion_escanear)
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_ACCION_ESCANEO)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alPulsar,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.destacada)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.acentoAccion,
        shape = FormaSalud.destacada,
    ) {
        Column(
            modifier = Modifier.padding(espaciado.generoso),
            verticalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            IconoSalud(
                glifo = GlifoSalud.ONDAS_NFC,
                lado = TAMANO_ICONO_ESCANEO,
                color = colores.sobreAcentoAccion,
            )
            Text(
                text = stringResource(Res.string.profesional_home_accion_escanear),
                style = MaterialTheme.typography.headlineSmall,
                color = colores.sobreAcentoAccion,
            )
        }
    }
}

/**
 * Entrada a la agenda: superficie normal, para no competir con la de emergencia.
 *
 * El glifo se posa DIRECTO sobre la tarjeta. Antes iba dentro de su propia
 * superficie con velo -- una tarjeta dentro de otra tarjeta --, que es uno de
 * los tells mas reconocibles de interfaz generada: dos rectangulos redondeados
 * concentricos que no aportan jerarquia, solo ruido.
 */
@Composable
private fun AccionAgenda(alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_profesional_home_accion_agenda)
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alPulsar,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.grande)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.fondoTarjeta,
        shape = FormaSalud.grande,
        border = bordeDeTarjeta(),
    ) {
        Row(
            modifier = Modifier.padding(espaciado.amplio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoSalud(
                glifo = GlifoSalud.CALENDARIO,
                lado = LADO_GLIFO_ACCION,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(espaciado.medio))
            Text(
                text = stringResource(Res.string.profesional_home_accion_agenda),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ------------------------------------------------------------------ Pacientes

/** Titulo de la cartera con su conteo, alineados por su linea base inferior. */
@Composable
private fun CabeceraDeCartera(estado: HomeProfesionalUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(Res.string.profesional_home_pacientes_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (estado.pacientesVinculados.isNotEmpty()) {
            Text(
                text = stringResource(
                    Res.string.profesional_home_pacientes_conteo,
                    estado.pacientesVinculados.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = LocalColoresSalud.current.textoSecundario,
            )
        }
    }
}

/**
 * Un paciente de la cartera.
 *
 * ## El semaforo de triage
 *
 * El nivel de riesgo es un punto con halo posado sobre el avatar, no un texto
 * de color ni una franja lateral. La razon es como se usa esta lista: el medico
 * la recorre buscando a quien atender primero, y en ese recorrido no lee --
 * barre. Un punto de 12dp en la misma posicion de cada fila forma una columna
 * de color que se interpreta sin fijar la vista en ninguna fila concreta.
 *
 * El halo no es adorno: el punto se posa sobre un disco de color, y sin el
 * degradado que lo despega se leeria como parte del avatar.
 *
 * ## Dos senales, y no son lo mismo
 *
 * El PUNTO lleva el riesgo clinico: quien es este paciente, un dato estable que
 * viene del expediente. La PALABRA de debajo del nombre lleva la severidad de su
 * ultima anotacion en el diario: que le pasa AHORA, algo que el propio paciente
 * escribio esta manana.
 *
 * Antes las dos eran puntos, uno encima del otro sobre el mismo avatar, y eso
 * obligaba al medico a recordar cual de los dos circulos significaba que. Ahora
 * la del expediente se pinta y la del diario se escribe: dos canales distintos
 * para dos hechos distintos.
 *
 * ## Nada viaja solo en color
 *
 * El nivel de riesgo va escrito bajo el nombre ademas de pintado. Un semaforo
 * clinico que solo se distingue por tono es inservible para quien no percibe el
 * rojo y el verde, e invisible para un lector de pantalla -- y es justo el dato
 * por el que un medico abriria una conversacion antes que otra.
 */
@Composable
private fun FilaPaciente(
    paciente: PacienteVinculado,
    severidadDelDiario: SeveridadDiario?,
    alAbrir: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val riesgo = stringResource(paciente.riesgo.recurso())
    // Un paciente SIN entradas no esta en verde: no ha escrito. Distinguirlo
    // evita que el medico lea "todo bien" donde solo hay silencio.
    val textoDiario = if (severidadDelDiario != null) {
        stringResource(
            Res.string.a11y_profesional_paciente_diario,
            stringResource(severidadDelDiario.recurso()),
        )
    } else {
        stringResource(Res.string.a11y_profesional_paciente_sin_diario)
    }
    val descripcion = stringResource(
        Res.string.a11y_profesional_home_paciente_completo,
        stringResource(
            Res.string.a11y_profesional_home_paciente_abrir,
            paciente.nombreCompleto,
            riesgo,
        ),
        riesgo,
        textoDiario,
    )
    val tinta = paciente.riesgo.tinta()
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alAbrir,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.grande)
            .padding(horizontal = espaciado.amplio, vertical = espaciado.medio)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .padding(DESPLAZAMIENTO_PUNTO)
                    .size(MedidaSalud.disco)
                    .background(paciente.riesgo.fondo(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = paciente.nombreCompleto.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = tinta,
                )
            }
            PuntoDeTriage(color = tinta)
        }
        Spacer(Modifier.width(espaciado.medio))

        Column(Modifier.weight(1f)) {
            Text(
                text = paciente.nombreCompleto,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = riesgo,
                    style = MaterialTheme.typography.labelMedium,
                    color = tinta,
                )
                if (severidadDelDiario != null) {
                    Text(
                        text = SEPARADOR_SENALES,
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.separador,
                        modifier = Modifier.padding(horizontal = espaciado.compacto),
                    )
                    Text(
                        text = stringResource(severidadDelDiario.recurso()),
                        style = MaterialTheme.typography.labelMedium,
                        color = severidadDelDiario.tinta(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Auxiliares

@Composable
private fun SinPacientes(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    Text(
        text = stringResource(Res.string.profesional_home_pacientes_sin_datos),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalColoresSalud.current.textoSecundario,
        modifier = modifier.padding(vertical = espaciado.medio),
    )
}

@Composable
private fun IndicadorCargando() {
    val descripcion = stringResource(Res.string.a11y_profesional_home_cargando)
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(espaciado.respiro)
            // La region viva sin descripcion no anuncia nada: TalkBack necesita
            // QUE leer, no solo saber que algo cambio.
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colores.acentoAccion)
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.profesional_home_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

@Composable
private fun RiesgoPaciente.tinta() = when (this) {
    RiesgoPaciente.ALTO -> LocalColoresSalud.current.acentoCritico
    RiesgoPaciente.MEDIO -> LocalColoresSalud.current.textoAdvertencia
    RiesgoPaciente.BAJO -> LocalColoresSalud.current.exito
}

@Composable
private fun RiesgoPaciente.fondo() = when (this) {
    RiesgoPaciente.ALTO -> LocalColoresSalud.current.fondoCritico
    RiesgoPaciente.MEDIO -> LocalColoresSalud.current.fondoAdvertencia
    RiesgoPaciente.BAJO -> LocalColoresSalud.current.fondoCitaConfirmada
}

/**
 * Con el turquesa anterior, 168dp de relleno funcionaban. Con el zafiro
 * `#0A4C86` -- mucho mas oscuro -- ese mismo bloque se comia el panel. A 132dp
 * sigue siendo, con diferencia, el elemento dominante sin llegar a aplastar.
 */
private val ALTO_ACCION_ESCANEO = 132.dp
private val TAMANO_ICONO_ESCANEO = 40.dp
private val LADO_GLIFO_ACCION = 24.dp

/**
 * Cuanto se aparta el disco de la esquina para dejar sitio al halo del punto.
 *
 * Es la mitad del halo (12dp x 2.6 / 2 redondeado): sin este margen, el
 * degradado se recortaria contra el borde del `Box` y el punto se veria como un
 * cuarto de circulo pegado a la esquina.
 */
private val DESPLAZAMIENTO_PUNTO = 8.dp

/** Punto medio tipografico, no un guion: separa dos senales sin sugerir resta. */
private const val SEPARADOR_SENALES = "·"
