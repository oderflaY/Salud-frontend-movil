package com.eter.salud.ui.navegacion

import com.eter.salud.presentation.navegacion.Destino
import com.eter.salud.ui.componentes.GlifoSalud
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.agenda_titulo
import salud.shared.generated.resources.nav_agenda_paciente
import salud.shared.generated.resources.nav_bandeja
import salud.shared.generated.resources.nav_historial
import salud.shared.generated.resources.nav_inicio
import salud.shared.generated.resources.nav_mi_medico
import salud.shared.generated.resources.nav_panel
import salud.shared.generated.resources.profesional_home_accion_escanear

/**
 * Puente entre las secciones de navegacion y lo que la barra inferior dibuja.
 *
 * Vive en la Vista y no en [Destino] a proposito: el destino es una posicion de
 * la app, no un icono ni una palabra. Si el dominio conociera su etiqueta,
 * traducir la app obligaria a tocar el modelo de navegacion.
 */
internal fun Destino.Seccion.etiqueta(): StringResource = when (this) {
    Destino.Inicio -> Res.string.nav_inicio
    Destino.AgendaPaciente -> Res.string.nav_agenda_paciente
    Destino.MiMedico -> Res.string.nav_mi_medico
    Destino.Historial -> Res.string.nav_historial
    Destino.PanelProfesional -> Res.string.nav_panel
    Destino.BandejaClinica -> Res.string.nav_bandeja
    Destino.Agenda -> Res.string.agenda_titulo
    Destino.Escaner -> Res.string.profesional_home_accion_escanear
}

internal fun Destino.Seccion.glifo(): GlifoSalud = when (this) {
    Destino.Inicio -> GlifoSalud.INICIO
    Destino.AgendaPaciente -> GlifoSalud.CALENDARIO
    Destino.MiMedico -> GlifoSalud.MEDICOS
    Destino.Historial -> GlifoSalud.DIARIO
    Destino.PanelProfesional -> GlifoSalud.INICIO
    Destino.BandejaClinica -> GlifoSalud.CONVERSACIONES
    Destino.Agenda -> GlifoSalud.CALENDARIO
    Destino.Escaner -> GlifoSalud.ONDAS_NFC
}
