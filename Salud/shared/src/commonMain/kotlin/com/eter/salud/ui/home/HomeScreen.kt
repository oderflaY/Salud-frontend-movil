// Hallmark - redesign 2026-09-10 (segunda corrida) - genero: modern-minimal, tono: soft
// macroestructura: Narrative Workflow vertical ("que te toca ahora" -> "todo el dia" -> "a donde ir")
// tema: custom "Clinica Serena" (ver SaludTheme.kt) - acento: azul clinico
// audiencia: personas mayores - texto >= 17sp, acciones de 72dp, cada estado con icono + palabra
// sin carruseles (nada escondido fuera de la pantalla), sin anillos, sin gestos
package com.eter.salud.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.avisos.TextosAviso
import com.eter.salud.domain.avisos.recordarAvisosClinicos
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.RecordatorioMedicacion
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.home.AccesoRapido
import com.eter.salud.presentation.home.HomeUiState
import com.eter.salud.presentation.home.HomeViewModel
import com.eter.salud.ui.agenda.fechaLarga
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.theme.AlturaAccionMayor
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.CifraSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_accion_ajustes
import salud.shared.generated.resources.a11y_home_abrir_agenda
import salud.shared.generated.resources.a11y_home_acceso_completar_perfil
import salud.shared.generated.resources.a11y_home_acceso_historial
import salud.shared.generated.resources.a11y_home_acceso_medicos
import salud.shared.generated.resources.a11y_home_acceso_rfid
import salud.shared.generated.resources.a11y_home_acceso_sintomas
import salud.shared.generated.resources.a11y_home_cargando
import salud.shared.generated.resources.a11y_home_resumen
import salud.shared.generated.resources.a11y_home_rfid
import salud.shared.generated.resources.a11y_home_toma
import salud.shared.generated.resources.a11y_home_toma_omitir
import salud.shared.generated.resources.a11y_home_toma_tomado
import salud.shared.generated.resources.accion_ajustes
import salud.shared.generated.resources.aviso_medicacion_cuerpo
import salud.shared.generated.resources.aviso_medicacion_titulo
import salud.shared.generated.resources.home_acceso_historial_corto
import salud.shared.generated.resources.home_acceso_medicos
import salud.shared.generated.resources.home_acceso_rfid
import salud.shared.generated.resources.home_acceso_sintomas
import salud.shared.generated.resources.home_accion_no_tome
import salud.shared.generated.resources.home_accion_tomar
import salud.shared.generated.resources.home_accion_ya_tome
import salud.shared.generated.resources.home_agenda_descripcion
import salud.shared.generated.resources.home_agenda_titulo
import salud.shared.generated.resources.home_conteo_omitidas
import salud.shared.generated.resources.home_conteo_pendientes
import salud.shared.generated.resources.home_conteo_tomadas
import salud.shared.generated.resources.home_estado_cargando
import salud.shared.generated.resources.home_estado_error
import salud.shared.generated.resources.home_perfil_accion_corta
import salud.shared.generated.resources.home_perfil_pendiente_descripcion
import salud.shared.generated.resources.home_perfil_pendiente_titulo
import salud.shared.generated.resources.home_proxima_a_las
import salud.shared.generated.resources.home_proxima_titulo
import salud.shared.generated.resources.home_resumen_faltan
import salud.shared.generated.resources.home_resumen_listo
import salud.shared.generated.resources.home_resumen_sin
import salud.shared.generated.resources.home_resumen_titulo
import salud.shared.generated.resources.home_rfid_activa
import salud.shared.generated.resources.home_rfid_inactiva
import salud.shared.generated.resources.home_saludo
import salud.shared.generated.resources.home_seccion_accesos
import salud.shared.generated.resources.home_todas_titulo
import salud.shared.generated.resources.home_toma_error
import salud.shared.generated.resources.home_toma_estado_omitido
import salud.shared.generated.resources.home_toma_estado_pendiente
import salud.shared.generated.resources.home_toma_estado_tomado
import salud.shared.generated.resources.home_toma_estado_tomado_tarde
import salud.shared.generated.resources.saludo_manana
import salud.shared.generated.resources.saludo_noche
import salud.shared.generated.resources.saludo_tarde

/**
 * Panel principal del paciente, pensado para personas mayores.
 *
 * ## Una pantalla que se lee de arriba abajo, como una carta
 *
 *  1. **Quien eres y que dia es.** "Buenos dias, Juan" y la fecha escrita en
 *     palabras. Orientarse en el tiempo es lo primero que se pierde con la
 *     edad o con una enfermedad, y es lo primero que la pantalla contesta.
 *  2. **Como vas hoy.** Una frase -- "Te faltan 2 de 4" -- y una barra con un
 *     tramo por medicina. Una frase se entiende sin interpretar un grafico.
 *  3. **Que te toca AHORA.** La proxima medicina, sola, con la hora grande y un
 *     boton de 72dp que dice exactamente lo que hace: "Ya me la tome".
 *  4. **Todo el dia.** La lista completa, en vertical: nada queda escondido
 *     fuera de la pantalla esperando un deslizamiento que no se adivina.
 *  5. **A donde ir.** La agenda y los accesos, como botones grandes con su
 *     nombre escrito y una flecha que dice "esto abre otra pantalla".
 *
 * ## Reglas que se cumplen en toda la pantalla
 *
 *  - Ningun color habla solo: cada estado lleva icono y palabra.
 *  - Ningun control es solo un icono: todos llevan su nombre.
 *  - Ningun texto de lectura baja de 17sp, ninguna accion de 60dp de alto.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    alCompletarPerfil: () -> Unit = {},
    alAbrirAcceso: (AccesoRapido) -> Unit = {},
    alAbrirAjustes: () -> Unit = {},
    alAbrirAgenda: () -> Unit = {},
    reloj: RelojSalud = relojDelSistema(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val margen = Modifier.padding(horizontal = MARGEN_PANTALLA)
    val proxima = estado.tomasDelDia.firstOrNull { it.estado == EstadoToma.PENDIENTE }

    // Sin esto el panel no pide nada y se queda en "sin medicinas" aunque el
    // paciente tenga tratamiento: el ViewModel no carga en su `init` para que
    // sus pruebas controlen cuando ocurre la lectura.
    LaunchedEffect(estado.idPaciente) { viewModel.cargar() }

    ProgramarRecordatorios(estado)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentPadding = PaddingValues(top = espaciado.compacto, bottom = espaciado.respiro),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        item(key = "cabecera") {
            Cabecera(estado, reloj, alAbrirAjustes, margen)
            Spacer(Modifier.height(espaciado.compacto))
        }

        if (estado.errorCarga) {
            item(key = "error") {
                BloqueDeError(
                    mensaje = stringResource(Res.string.home_estado_error),
                    alReintentar = viewModel::cargar,
                    modifier = margen,
                )
            }
            return@LazyColumn
        }

        if (estado.cargando) {
            item(key = "cargando") { Cargando(margen) }
            return@LazyColumn
        }

        if (estado.perfilEmergenciaPendiente) {
            item(key = "perfil_pendiente") { AvisoPerfilPendiente(alCompletarPerfil, margen) }
        }

        item(key = "resumen") { ResumenDelDia(estado, margen) }

        if (proxima != null) {
            item(key = "proxima") {
                ProximaToma(
                    toma = proxima,
                    habilitada = estado.tomaEnCurso == null,
                    alTomar = { viewModel.marcarTomada(proxima.idToma) },
                    alOmitir = { viewModel.omitirToma(proxima.idToma) },
                    modifier = margen,
                )
            }
        }

        if (estado.errorRegistroToma) {
            item(key = "error_toma") { AvisoErrorDeToma(margen) }
        }

        if (estado.tomasDelDia.isNotEmpty()) {
            item(key = "titulo_todas") {
                Spacer(Modifier.height(espaciado.compacto))
                TituloDeSeccion(stringResource(Res.string.home_todas_titulo), margen)
            }
            items(estado.tomasDelDia, key = { it.idToma }) { toma ->
                FilaDeToma(
                    toma = toma,
                    habilitada = estado.tomaEnCurso == null,
                    alTomar = { viewModel.marcarTomada(toma.idToma) },
                    modifier = margen,
                )
            }
        }

        item(key = "agenda") {
            Spacer(Modifier.height(espaciado.compacto))
            BotonDeDestino(
                glifo = GlifoSalud.CALENDARIO,
                titulo = stringResource(Res.string.home_agenda_titulo),
                descripcion = stringResource(Res.string.home_agenda_descripcion),
                descripcionAccesible = stringResource(Res.string.a11y_home_abrir_agenda),
                destacado = true,
                alPulsar = alAbrirAgenda,
                modifier = margen,
            )
        }

        item(key = "titulo_accesos") {
            Spacer(Modifier.height(espaciado.compacto))
            TituloDeSeccion(stringResource(Res.string.home_seccion_accesos), margen)
        }
        items(estado.accesosRapidos, key = { it.name }) { acceso ->
            BotonDeDestino(
                glifo = acceso.glifo(),
                titulo = stringResource(acceso.recurso()),
                descripcion = null,
                descripcionAccesible = stringResource(acceso.recursoAccesible()),
                destacado = false,
                alPulsar = { alAbrirAcceso(acceso) },
                modifier = margen,
            )
        }
    }
}

/**
 * Programa en el sistema operativo los recordatorios de las tomas pendientes.
 *
 * Vive en la Vista porque los textos del aviso salen de `strings.xml`. Es
 * idempotente: reprogramar una toma REEMPLAZA su alarma, y las tomas ya
 * registradas se cancelan.
 */
@Composable
private fun ProgramarRecordatorios(estado: HomeUiState) {
    val avisos = recordarAvisosClinicos()
    val titulo = stringResource(Res.string.aviso_medicacion_titulo)

    val pendientes = estado.tomasPorRecordar.map { toma ->
        RecordatorioMedicacion(
            idToma = toma.idToma,
            medicamento = toma.medicamento,
            dosis = toma.dosis,
            fecha = estado.fechaSeleccionada,
            horaProgramada = toma.horaProgramada,
        ) to TextosAviso(
            titulo = titulo,
            cuerpo = stringResource(Res.string.aviso_medicacion_cuerpo, toma.medicamento, toma.dosis),
        )
    }
    val yaRegistradas = estado.tomasDelDia
        .filter { it.estado.estaRegistrada }
        .map { it.idToma }

    LaunchedEffect(pendientes, yaRegistradas, avisos.permitidos) {
        if (!avisos.permitidos) return@LaunchedEffect
        pendientes.forEach { (recordatorio, textos) ->
            avisos.programarRecordatorio(recordatorio, textos)
        }
        yaRegistradas.forEach(avisos::cancelarRecordatorio)
    }
}

// ------------------------------------------------------------------ Cabecera

/**
 * "Buenos dias," + el nombre + la fecha en palabras, y los ajustes con su
 * nombre escrito (un engrane solo no dice nada a quien no lo ha aprendido).
 */
@Composable
private fun Cabecera(
    estado: HomeUiState,
    reloj: RelojSalud,
    alAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val nombre = estado.nombrePaciente.trim()
    val saludo = stringResource(saludoSegunLaHora(reloj))

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = espaciado.compacto)) {
                // Sin nombre (perfil a medio llenar) el saludo se queda solo y
                // sube a titular; nunca "Buenos dias, Inicio".
                if (nombre.isNotEmpty()) {
                    Text(text = saludo, style = CifraSalud.saludo, color = colores.textoSecundario)
                }
                Text(
                    text = nombre.ifEmpty { saludo.trimEnd(',') },
                    style = CifraSalud.nombre,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.width(espaciado.compacto))
            BotonAjustes(alAbrirAjustes)
        }
        Spacer(Modifier.height(espaciado.compacto))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoSalud(glifo = GlifoSalud.CALENDARIO, lado = LADO_ICONO_LINEA, color = colores.textoSecundario)
            Spacer(Modifier.width(espaciado.compacto))
            Text(
                text = fechaLarga(reloj.fechaHoy()),
                style = MaterialTheme.typography.titleMedium,
                color = colores.textoSecundario,
            )
        }
        if (!estado.cargando) {
            Spacer(Modifier.height(espaciado.compacto))
            EstadoTarjeta(activa = estado.tarjetaRfidActiva)
        }
    }
}

/** Engrane con la palabra "Ajustes" debajo, en un area de 64dp. */
@Composable
private fun BotonAjustes(alPulsar: () -> Unit) {
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_accion_ajustes)
    val fuente = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .widthIn(min = ANCHO_BOTON_AJUSTES)
            .heightIn(min = ANCHO_BOTON_AJUSTES)
            .clip(FormaSalud.grande)
            .clickable(interactionSource = fuente, indication = null, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.grande)
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
            .padding(LocalEspaciadoSalud.current.minimo),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconoSalud(glifo = GlifoSalud.AJUSTES, lado = LADO_ICONO_AJUSTES, color = colores.acentoAccion)
        Text(
            text = stringResource(Res.string.accion_ajustes),
            style = MaterialTheme.typography.labelMedium,
            color = colores.acentoAccion,
            maxLines = 1,
        )
    }
}

/** La tarjeta de emergencia como una linea: icono + frase, en su color. */
@Composable
private fun EstadoTarjeta(activa: Boolean) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val texto = stringResource(if (activa) Res.string.home_rfid_activa else Res.string.home_rfid_inactiva)
    val tinta = if (activa) colores.exito else colores.textoAdvertencia
    val descripcion = stringResource(Res.string.a11y_home_rfid, texto)

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoSalud(
            glifo = if (activa) GlifoSalud.VERIFICADO else GlifoSalud.TARJETA,
            lado = LADO_ICONO_LINEA,
            color = tinta,
        )
        Spacer(Modifier.width(espaciado.compacto))
        Text(text = texto, style = MaterialTheme.typography.titleSmall, color = tinta)
    }
}

// ---------------------------------------------------------------- Resumen

/**
 * "Te faltan 2 de 4" y una barra con un tramo por medicina.
 *
 * La barra no es un porcentaje abstracto: cada tramo ES una medicina, y el
 * paciente puede contarlos. Verde = tomada, ambar = omitida, gris = pendiente;
 * y debajo, las tres cantidades escritas, para que el color nunca hable solo.
 */
@Composable
private fun ResumenDelDia(estado: HomeUiState, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val tomas = estado.tomasDelDia
    val tomadas = tomas.count { it.estado == EstadoToma.TOMADO || it.estado == EstadoToma.TOMADO_TARDE }
    val omitidas = tomas.count { it.estado == EstadoToma.OMITIDO }
    val pendientes = tomas.count { it.estado == EstadoToma.PENDIENTE }

    val frase = when {
        tomas.isEmpty() -> stringResource(Res.string.home_resumen_sin)
        pendientes == 0 -> stringResource(Res.string.home_resumen_listo)
        else -> stringResource(Res.string.home_resumen_faltan, pendientes, tomas.size)
    }
    val descripcion = stringResource(Res.string.a11y_home_resumen, frase, tomadas, pendientes, omitidas)

    Tarjeta(modifier.semantics(mergeDescendants = true) { contentDescription = descripcion }) {
        Text(
            text = stringResource(Res.string.home_resumen_titulo),
            style = MaterialTheme.typography.titleSmall,
            color = colores.textoSecundario,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = frase,
            style = CifraSalud.dato,
            color = if (tomas.isNotEmpty() && pendientes == 0) colores.exito else MaterialTheme.colorScheme.onSurface,
        )
        if (tomas.isNotEmpty()) {
            Spacer(Modifier.height(espaciado.medio))
            Row(horizontalArrangement = Arrangement.spacedBy(TRAMO_SEPARACION)) {
                tomas.forEach { toma ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(ALTO_TRAMO)
                            .background(toma.estado.colorTramo(), FormaSalud.pastilla),
                    )
                }
            }
            Spacer(Modifier.height(espaciado.medio))
            Row(horizontalArrangement = Arrangement.spacedBy(espaciado.medio)) {
                Conteo(stringResource(Res.string.home_conteo_tomadas, tomadas), colores.exito, GlifoSalud.VERIFICADO)
                Conteo(stringResource(Res.string.home_conteo_pendientes, pendientes), colores.textoSecundario, GlifoSalud.PENDIENTE)
            }
            if (omitidas > 0) {
                Spacer(Modifier.height(espaciado.compacto))
                Conteo(stringResource(Res.string.home_conteo_omitidas, omitidas), colores.textoAdvertencia, GlifoSalud.CANCELADO)
            }
        }
    }
}

@Composable
private fun Conteo(texto: String, tinta: Color, glifo: GlifoSalud) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconoSalud(glifo = glifo, lado = LADO_ICONO_CONTEO, color = tinta)
        Spacer(Modifier.width(LocalEspaciadoSalud.current.minimo + 2.dp))
        Text(text = texto, style = MaterialTheme.typography.labelLarge, color = tinta, maxLines = 1)
    }
}

// -------------------------------------------------------- La proxima toma

/**
 * La proxima medicina, sola, sobre azul suave: es LO que hay que hacer ahora.
 *
 * Dos botones, uno bajo el otro y a todo lo ancho, con frases completas en
 * primera persona ("Ya me la tome", "No me la tome"): la persona confirma lo
 * que HIZO, no tiene que traducir un "Registrar" o un "Omitir".
 *
 * El registro es optimista: al tocar, la tarjeta pasa a la siguiente medicina
 * al instante, sin indicador de carga. Si el servidor lo rechaza, la toma
 * vuelve y aparece el aviso de error.
 */
@Composable
private fun ProximaToma(
    toma: TomaDelDia,
    habilitada: Boolean,
    alTomar: () -> Unit,
    alOmitir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colores.acentoSuave, FormaSalud.carril)
            .padding(espaciado.amplio),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoSalud(glifo = GlifoSalud.PASTILLA, lado = LADO_ICONO_LINEA, color = colores.acentoAccion)
            Spacer(Modifier.width(espaciado.compacto))
            Text(
                text = stringResource(Res.string.home_proxima_titulo),
                style = MaterialTheme.typography.titleSmall,
                color = colores.acentoAccion,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.home_proxima_a_las, toma.horaProgramada),
            style = CifraSalud.hora,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = toma.medicamento,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = toma.dosis,
            style = MaterialTheme.typography.bodyLarge,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.amplio))
        BotonGrande(
            texto = stringResource(Res.string.home_accion_ya_tome),
            glifo = GlifoSalud.CONFIRMAR,
            descripcion = stringResource(Res.string.a11y_home_toma_tomado, toma.medicamento),
            relleno = true,
            habilitado = habilitada,
            alPulsar = alTomar,
        )
        Spacer(Modifier.height(espaciado.compacto))
        BotonGrande(
            texto = stringResource(Res.string.home_accion_no_tome),
            glifo = null,
            descripcion = stringResource(Res.string.a11y_home_toma_omitir, toma.medicamento),
            relleno = false,
            habilitado = habilitada,
            alPulsar = alOmitir,
        )
    }
}

/**
 * Boton de 72dp a todo lo ancho, con texto y (opcional) icono.
 *
 * Relleno: azul clinico con texto blanco (7:1). Sin relleno: fondo de tarjeta
 * con borde azul de 2dp -- un borde de 1dp se pierde para una vista cansada y el
 * boton parece texto suelto. Deshabilitado lleva tres senales a la vez:
 * opacidad, ausencia de accion y el estado que `clickable` anuncia.
 */
@Composable
private fun BotonGrande(
    texto: String,
    glifo: GlifoSalud?,
    descripcion: String,
    relleno: Boolean,
    habilitado: Boolean,
    alPulsar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuente = remember { MutableInteractionSource() }
    val tinta = if (relleno) colores.sobreAcentoAccion else colores.acentoAccion

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AlturaAccionMayor)
            .alpha(if (habilitado) 1f else OPACIDAD_DESHABILITADA)
            .clip(FormaSalud.destacada)
            .background(if (relleno) colores.acentoAccion else colores.fondoTarjeta)
            .then(
                if (relleno) Modifier else Modifier.border(GROSOR_BORDE_BOTON, colores.acentoAccion, FormaSalud.destacada),
            )
            .clickable(interactionSource = fuente, indication = null, enabled = habilitado, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.destacada)
            .semantics { contentDescription = descripcion }
            .padding(horizontal = espaciado.medio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glifo != null) {
            IconoSalud(glifo = glifo, lado = LADO_ICONO_BOTON, color = tinta)
            Spacer(Modifier.width(espaciado.compacto))
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.titleMedium,
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------ Todas las de hoy

/**
 * Una medicina del dia: hora grande a la izquierda, nombre y dosis, y a la
 * derecha su estado escrito con icono -- o un boton "Tomar" si aun falta.
 */
@Composable
private fun FilaDeToma(
    toma: TomaDelDia,
    habilitada: Boolean,
    alTomar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val estadoTexto = stringResource(toma.estado.recurso())
    val descripcion = stringResource(
        Res.string.a11y_home_toma,
        toma.medicamento,
        toma.dosis,
        toma.horaProgramada,
        estadoTexto,
    )

    Tarjeta(modifier, relleno = espaciado.medio + espaciado.minimo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = descripcion },
            ) {
                Text(text = toma.horaProgramada, style = CifraSalud.hora, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(espaciado.minimo))
                Text(
                    text = toma.medicamento,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = toma.dosis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(espaciado.compacto))
                EtiquetaDeEstado(toma.estado, estadoTexto)
            }
            if (toma.estado == EstadoToma.PENDIENTE) {
                Spacer(Modifier.width(espaciado.medio))
                BotonTomar(toma.medicamento, habilitada, alTomar)
            }
        }
    }
}

/** El estado como icono + palabra, sobre su fondo suave. */
@Composable
private fun EtiquetaDeEstado(estado: EstadoToma, texto: String) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val (tinta, fondo, glifo) = when (estado) {
        EstadoToma.TOMADO, EstadoToma.TOMADO_TARDE -> Triple(colores.exito, colores.fondoExito, GlifoSalud.VERIFICADO)
        EstadoToma.OMITIDO -> Triple(colores.textoAdvertencia, colores.fondoAdvertencia, GlifoSalud.CANCELADO)
        EstadoToma.PENDIENTE -> Triple(colores.textoSecundario, colores.fondoCampo, GlifoSalud.PENDIENTE)
    }
    Row(
        modifier = Modifier
            .background(fondo, FormaSalud.pastilla)
            .padding(horizontal = espaciado.compacto + espaciado.minimo, vertical = espaciado.minimo + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoSalud(glifo = glifo, lado = LADO_ICONO_CONTEO, color = tinta)
        Spacer(Modifier.width(espaciado.minimo + 2.dp))
        Text(text = texto, style = MaterialTheme.typography.labelMedium, color = tinta, maxLines = 1)
    }
}

/** "Tomar": 60dp de alto, borde azul de 2dp, al lado derecho de una fila pendiente. */
@Composable
private fun BotonTomar(medicamento: String, habilitado: Boolean, alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuente = remember { MutableInteractionSource() }
    val descripcion = stringResource(Res.string.a11y_home_toma_tomado, medicamento)

    Row(
        modifier = Modifier
            .heightIn(min = ALTO_BOTON_TOMAR)
            .alpha(if (habilitado) 1f else OPACIDAD_DESHABILITADA)
            .clip(FormaSalud.grande)
            .border(GROSOR_BORDE_BOTON, colores.acentoAccion, FormaSalud.grande)
            .clickable(interactionSource = fuente, indication = null, enabled = habilitado, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.grande)
            .semantics { contentDescription = descripcion }
            .padding(horizontal = espaciado.medio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoSalud(glifo = GlifoSalud.CONFIRMAR, lado = LADO_ICONO_CONTEO, color = colores.acentoAccion)
        Spacer(Modifier.width(espaciado.minimo + 2.dp))
        Text(
            text = stringResource(Res.string.home_accion_tomar),
            style = MaterialTheme.typography.labelLarge,
            color = colores.acentoAccion,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Destinos

/**
 * Un destino de la app como boton grande: icono en su circulo, nombre escrito,
 * descripcion opcional y una flecha a la derecha que dice "abre otra pantalla".
 *
 * [destacado] pinta el fondo en azul suave: se usa solo para la agenda, que es
 * el destino que una persona mayor consulta a diario.
 */
@Composable
private fun BotonDeDestino(
    glifo: GlifoSalud,
    titulo: String,
    descripcion: String?,
    descripcionAccesible: String,
    destacado: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuente = remember { MutableInteractionSource() }
    val fondo = if (destacado) colores.acentoSuave else colores.fondoTarjeta

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_DESTINO)
            .clip(FormaSalud.destacada)
            .background(fondo)
            .then(if (destacado) Modifier else Modifier.border(1.dp, colores.separador, FormaSalud.destacada))
            .clickable(interactionSource = fuente, indication = null, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.destacada)
            .semantics(mergeDescendants = true) { contentDescription = descripcionAccesible }
            .padding(horizontal = espaciado.medio + espaciado.minimo, vertical = espaciado.medio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(DIAMETRO_CIRCULO_ICONO)
                .background(if (destacado) colores.fondoTarjeta else colores.acentoSuave, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconoSalud(glifo = glifo, lado = LADO_ICONO_DESTINO, color = colores.acentoAccion)
        }
        Spacer(Modifier.width(espaciado.medio))
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (descripcion != null) {
                Text(text = descripcion, style = MaterialTheme.typography.bodyMedium, color = colores.textoSecundario)
            }
        }
        Spacer(Modifier.width(espaciado.compacto))
        IconoSalud(glifo = GlifoSalud.SIGUIENTE, lado = LADO_ICONO_DESTINO, color = colores.acentoAccion)
    }
}

// ---------------------------------------------------------------- Auxiliares

/** Superficie de tarjeta del sistema: blanca, con borde suave y esquinas amplias. */
@Composable
private fun Tarjeta(
    modifier: Modifier = Modifier,
    relleno: Dp = LocalEspaciadoSalud.current.amplio,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val colores = LocalColoresSalud.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FormaSalud.destacada)
            .background(colores.fondoTarjeta)
            .border(1.dp, colores.separador, FormaSalud.destacada)
            .padding(relleno),
        content = contenido,
    )
}

@Composable
private fun TituloDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun AvisoPerfilPendiente(alCompletarPerfil: () -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colores.fondoAdvertencia, FormaSalud.destacada)
            .padding(espaciado.amplio),
        verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoSalud(glifo = GlifoSalud.TARJETA, lado = LADO_ICONO_LINEA, color = colores.textoAdvertencia)
            Spacer(Modifier.width(espaciado.compacto))
            Text(
                text = stringResource(Res.string.home_perfil_pendiente_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = colores.textoAdvertencia,
            )
        }
        Text(
            text = stringResource(Res.string.home_perfil_pendiente_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(espaciado.compacto))
        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.home_perfil_accion_corta),
            alPulsar = alCompletarPerfil,
            descripcionAccesible = stringResource(Res.string.a11y_home_acceso_completar_perfil),
        )
    }
}

/**
 * El registro fallo y la toma volvio a pendiente. Region viva asertiva: cambia
 * lo que la persona tiene que hacer a continuacion.
 */
@Composable
private fun AvisoErrorDeToma(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colores.fondoCritico, FormaSalud.grande)
            .padding(espaciado.medio)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoSalud(glifo = GlifoSalud.CANCELADO, lado = LADO_ICONO_LINEA, color = colores.acentoCritico)
        Spacer(Modifier.width(espaciado.compacto))
        Text(
            text = stringResource(Res.string.home_toma_error),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.acentoCritico,
        )
    }
}

/** Carga: dos tarjetas vacias con la forma de lo que viene, y la frase escrita. */
@Composable
private fun Cargando(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_home_cargando)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = descripcion
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        Text(
            text = stringResource(Res.string.home_estado_cargando),
            style = MaterialTheme.typography.bodyLarge,
            color = colores.textoSecundario,
        )
        repeat(2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ALTO_ESQUELETO)
                    .background(colores.fondoCampo, FormaSalud.destacada),
            )
        }
    }
}

@Composable
private fun EstadoToma.colorTramo(): Color {
    val colores = LocalColoresSalud.current
    return when (this) {
        EstadoToma.TOMADO, EstadoToma.TOMADO_TARDE -> colores.senalEstable
        EstadoToma.OMITIDO -> colores.senalVigilancia
        EstadoToma.PENDIENTE -> colores.separador
    }
}

/** El saludo cambia con la hora local: a las 21:00 "Buenos dias" desorienta. */
private fun saludoSegunLaHora(reloj: RelojSalud): StringResource {
    val hora = reloj.horaLocal(reloj.instanteActual()).substringBefore(':').toIntOrNull()
    return when (hora) {
        null -> Res.string.home_saludo
        in HORA_MANANA until HORA_TARDE -> Res.string.saludo_manana
        in HORA_TARDE until HORA_NOCHE -> Res.string.saludo_tarde
        else -> Res.string.saludo_noche
    }
}

// --------------------------------------------------------- Puentes a strings

private fun EstadoToma.recurso(): StringResource = when (this) {
    EstadoToma.PENDIENTE -> Res.string.home_toma_estado_pendiente
    EstadoToma.TOMADO -> Res.string.home_toma_estado_tomado
    EstadoToma.TOMADO_TARDE -> Res.string.home_toma_estado_tomado_tarde
    EstadoToma.OMITIDO -> Res.string.home_toma_estado_omitido
}

private fun AccesoRapido.recurso(): StringResource = when (this) {
    AccesoRapido.TARJETA_RFID -> Res.string.home_acceso_rfid
    AccesoRapido.DIARIO_SINTOMAS -> Res.string.home_acceso_sintomas
    AccesoRapido.MIS_MEDICOS -> Res.string.home_acceso_medicos
    AccesoRapido.HISTORIAL -> Res.string.home_acceso_historial_corto
}

private fun AccesoRapido.recursoAccesible(): StringResource = when (this) {
    AccesoRapido.TARJETA_RFID -> Res.string.a11y_home_acceso_rfid
    AccesoRapido.DIARIO_SINTOMAS -> Res.string.a11y_home_acceso_sintomas
    AccesoRapido.MIS_MEDICOS -> Res.string.a11y_home_acceso_medicos
    AccesoRapido.HISTORIAL -> Res.string.a11y_home_acceso_historial
}

private fun AccesoRapido.glifo(): GlifoSalud = when (this) {
    AccesoRapido.TARJETA_RFID -> GlifoSalud.TARJETA
    AccesoRapido.DIARIO_SINTOMAS -> GlifoSalud.DIARIO
    AccesoRapido.MIS_MEDICOS -> GlifoSalud.MEDICOS
    AccesoRapido.HISTORIAL -> GlifoSalud.HISTORIAL
}

/** 20dp de margen lateral: mas estrecho que 24 para que las frases largas quepan. */
private val MARGEN_PANTALLA = 20.dp
private val ANCHO_BOTON_AJUSTES = 64.dp
private val LADO_ICONO_AJUSTES = 28.dp
private val LADO_ICONO_LINEA = 24.dp
private val LADO_ICONO_CONTEO = 20.dp
private val LADO_ICONO_BOTON = 28.dp
private val LADO_ICONO_DESTINO = 28.dp
private val DIAMETRO_CIRCULO_ICONO = 52.dp
private val ALTO_DESTINO = 80.dp
private val ALTO_BOTON_TOMAR = 60.dp
private val ALTO_TRAMO = 16.dp
private val TRAMO_SEPARACION = 6.dp
private val ALTO_ESQUELETO = 160.dp
private val GROSOR_BORDE_BOTON = 2.dp
private const val OPACIDAD_DESHABILITADA = 0.5f
private const val HORA_MANANA = 5
private const val HORA_TARDE = 12
private const val HORA_NOCHE = 19
