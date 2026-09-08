package com.eter.salud.presentation.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.mapper.aBorradorPaciente
import com.eter.salud.domain.mapper.aPacienteDto
import com.eter.salud.domain.model.Cirugia
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.onboarding.BorradorPaciente
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la Fase 2: ajustes y completado del historial medico.
 *
 * Diferencias de fondo con la Fase 1:
 *  - No es un flujo guiado. Cada seccion se abre, valida y guarda por separado,
 *    porque el paciente entra a Ajustes a corregir un dato suelto.
 *  - Edita un expediente que ya existe: se descarga primero y se envia completo,
 *    de modo que guardar una seccion nunca borre las demas.
 *
 * No conoce Compose, ni recursos, ni red: solo el contrato
 * [HistorialMedicoRepositorio].
 */
class PerfilMedicoViewModel(
    private val repositorio: HistorialMedicoRepositorio,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState(fechaDeHoy = reloj.fechaHoy()))
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    // ------------------------------------------------------------------ Carga

    /** Descarga el expediente y lo convierte en borrador editable. */
    fun cargarPerfil(idPaciente: String) {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorGuardado = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.obtenerPaciente(idPaciente) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { dto ->
                        val borrador = dto.aBorradorPaciente()
                        previo.copy(
                            cargando = false,
                            idPaciente = idPaciente,
                            borrador = borrador,
                            seccionesCompletas = ValidadorPerfil.seccionesCompletas(
                                borrador = borrador,
                                hoy = reloj.fechaHoy(),
                            ),
                        )
                    },
                    onFailure = {
                        previo.copy(cargando = false, errorGuardado = ErrorGuardado.SIN_CONEXION)
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------- Navegacion

    fun abrirSeccion(seccion: SeccionPerfil) {
        _estado.update {
            it.copy(
                seccionAbierta = seccion,
                erroresSeccion = emptyList(),
                erroresFormulario = emptyList(),
                errorGuardado = null,
                seccionGuardada = null,
            )
        }
    }

    fun cerrarSeccion() {
        _estado.update {
            it.copy(
                seccionAbierta = null,
                erroresSeccion = emptyList(),
                erroresFormulario = emptyList(),
            )
        }
    }

    /** Baja la confirmacion de guardado una vez que la Vista la anuncio. */
    fun descartarConfirmacion() {
        _estado.update { it.copy(seccionGuardada = null, errorGuardado = null) }
    }

    // ------------------------------------------------- Seccion 1 y 2: identificaciones

    fun actualizarCurp(valor: String) = editarBorrador { it.copy(curp = valor) }

    fun actualizarNss(valor: String) = editarBorrador { it.copy(nss = valor) }

    fun actualizarAseguradora(valor: String) = editarBorrador { it.copy(aseguradora = valor) }

    // ------------------------------------------------- Seccion 3: donacion de organos

    /** Tri-estado: `null` es "aun no contesta", que no es lo mismo que "no". */
    fun marcarDonadorOrganos(donador: Boolean?) =
        editarBorrador { it.copy(donadorOrganos = donador) }

    // ------------------------------------------------ Seccion 4 y 5: metricas vitales

    fun actualizarPeso(valor: String) = editarBorrador { it.copy(pesoKg = valor) }

    fun actualizarAltura(valor: String) = editarBorrador { it.copy(alturaCm = valor) }

    fun actualizarPresionSistolica(valor: String) =
        editarBorrador { it.copy(presionSistolica = valor) }

    fun actualizarPresionDiastolica(valor: String) =
        editarBorrador { it.copy(presionDiastolica = valor) }

    // ------------------------------------------------------------ Seccion 6: cirugias

    fun agregarCirugia(procedimiento: String, fecha: String, notas: String) {
        val errores = ValidadorPerfil.validarCirugia(
            procedimiento = procedimiento,
            fecha = fecha,
            hoy = reloj.fechaHoy(),
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresFormulario = errores) }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                cirugias = borrador.cirugias + Cirugia(
                    procedimiento = procedimiento.trim(),
                    fecha = fecha.trim(),
                    notas = notas.trim().ifBlank { null },
                ),
                sinCirugias = false,
            )
        }
    }

    fun eliminarCirugia(indice: Int) = editarBorrador { borrador ->
        borrador.copy(cirugias = borrador.cirugias.sinElementoEn(indice))
    }

    fun marcarSinCirugias(sinCirugias: Boolean) = editarBorrador {
        it.copy(
            sinCirugias = sinCirugias,
            cirugias = if (sinCirugias) emptyList() else it.cirugias,
        )
    }

    // ------------------------------------------------------- Seccion 7: antecedentes

    fun agregarAntecedente(antecedente: String) {
        if (antecedente.isBlank()) {
            _estado.update {
                it.copy(erroresFormulario = listOf(ErrorCampoPerfil.ANTECEDENTE_VACIO))
            }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                antecedentesHeredofamiliares =
                    borrador.antecedentesHeredofamiliares + antecedente.trim(),
                sinAntecedentes = false,
            )
        }
    }

    fun eliminarAntecedente(indice: Int) = editarBorrador { borrador ->
        borrador.copy(
            antecedentesHeredofamiliares =
                borrador.antecedentesHeredofamiliares.sinElementoEn(indice),
        )
    }

    fun marcarSinAntecedentes(sinAntecedentes: Boolean) = editarBorrador {
        it.copy(
            sinAntecedentes = sinAntecedentes,
            antecedentesHeredofamiliares =
                if (sinAntecedentes) emptyList() else it.antecedentesHeredofamiliares,
        )
    }

    // --------------------------------------------------------------- Guardado

    /**
     * Valida la seccion abierta y, solo si esta correcta, envia el expediente
     * completo. Se envia entero a proposito: el backend recibe el estado final
     * del paciente y ninguna seccion pisa a otra con campos en blanco.
     */
    fun guardarSeccion() {
        val actual = _estado.value
        if (actual.guardando) return
        val seccion = actual.seccionAbierta ?: return

        val errores = ValidadorPerfil.validarSeccion(
            seccion = seccion,
            borrador = actual.borrador,
            hoy = reloj.fechaHoy(),
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresSeccion = errores) }
            return
        }

        val idPaciente = actual.idPaciente
        if (idPaciente == null) {
            _estado.update { it.copy(errorGuardado = ErrorGuardado.PERFIL_NO_CARGADO) }
            return
        }

        _estado.update { it.copy(guardando = true, errorGuardado = null, erroresSeccion = emptyList()) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.actualizarHistorial(construirDto(idPaciente)) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = {
                        previo.copy(
                            guardando = false,
                            seccionAbierta = null,
                            seccionGuardada = seccion,
                            seccionesCompletas = previo.seccionesCompletas + seccion,
                            errorGuardado = null,
                        )
                    },
                    onFailure = {
                        previo.copy(guardando = false, errorGuardado = ErrorGuardado.SIN_CONEXION)
                    },
                )
            }
        }
    }

    /** Proyeccion del borrador al contrato de red del DM_HistorialMedico.md. */
    fun construirDto(idPaciente: String): PacienteDto =
        _estado.value.borrador.aPacienteDto(reloj = reloj, idPaciente = idPaciente)

    // ------------------------------------------------------------- Utilidades

    /** Punto unico de escritura del borrador: limpia el ruido de la captura previa. */
    private fun editarBorrador(bloque: (BorradorPaciente) -> BorradorPaciente) {
        _estado.update { previo ->
            previo.copy(
                borrador = bloque(previo.borrador),
                erroresFormulario = emptyList(),
                erroresSeccion = emptyList(),
            )
        }
    }

    private fun <T> List<T>.sinElementoEn(indice: Int): List<T> =
        if (indice in indices) filterIndexed { posicion, _ -> posicion != indice } else this
}
