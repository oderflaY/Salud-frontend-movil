package com.eter.salud.presentation.emergencia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.domain.nfc.ErrorLecturaNfc
import com.eter.salud.domain.nfc.LectorTarjetaNfc
import com.eter.salud.domain.repository.FalloEmergencia
import com.eter.salud.domain.repository.MotivoFalloEmergencia
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del escaner de emergencia para personal medico.
 *
 * Gobierna la maquina de estados completa: antena apagada, espera de tarjeta,
 * consulta y perfil de supervivencia. Las decisiones de diseno responden al
 * contexto de uso, que es una atencion prehospitalaria:
 *
 *  - Un fallo fisico de lectura (la tarjeta se aleja) NO saca de la pantalla de
 *    espera: se avisa y se sigue escuchando, porque el paramedico va a volver a
 *    acercarla de inmediato.
 *  - Un fallo de la consulta SI corta, porque exige una decision distinta:
 *    reintentar, o pedir otra identificacion si la tarjeta esta revocada.
 *  - En cuanto hay perfil, la antena se apaga: la pantalla ya esta ocupada y
 *    leer otra tarjeta por accidente cambiaria el paciente en pantalla.
 *
 * No conoce Compose, ni recursos, ni el hardware: solo los contratos
 * [LectorTarjetaNfc] y [PerfilEmergenciaRepositorio].
 */
class EscanerEmergenciaViewModel(
    private val lector: LectorTarjetaNfc,
    private val repositorio: PerfilEmergenciaRepositorio,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(EscanerUiState())
    val estado: StateFlow<EscanerUiState> = _estado.asStateFlow()

    /** Enciende la antena si el dispositivo lo permite. */
    fun activarEscaneo() {
        val disponibilidad = lector.disponibilidad
        if (disponibilidad != DisponibilidadNfc.DISPONIBLE) {
            _estado.update {
                it.copy(
                    fase = FaseEscaneo.ANTENA_NO_DISPONIBLE,
                    disponibilidad = disponibilidad,
                )
            }
            return
        }

        _estado.update {
            it.copy(
                fase = FaseEscaneo.ESPERANDO_TARJETA,
                disponibilidad = disponibilidad,
                errorConsulta = null,
            )
        }
        lector.iniciar(alLeer = ::alLeerTarjeta, alFallar = ::alFallarLectura)
    }

    /** Apaga la escucha. La Vista lo llama al salir: la antena gasta bateria. */
    fun detenerEscaneo() {
        lector.detener()
        _estado.update { it.copy(fase = FaseEscaneo.INACTIVO) }
    }

    /** Descarta el perfil en pantalla y vuelve a escuchar. */
    fun escanearOtraTarjeta() {
        _estado.update {
            it.copy(perfil = null, edadPaciente = null, errorConsulta = null)
        }
        activarEscaneo()
    }

    /** La Vista avisa de que ya disparo la vibracion de confirmacion. */
    fun vibracionConsumida() {
        _estado.update { it.copy(confirmarConVibracion = false) }
    }

    private fun alLeerTarjeta(idTarjeta: String) {
        if (_estado.value.estaConsultando) return
        lector.detener()
        _estado.update { it.copy(fase = FaseEscaneo.CONSULTANDO, errorConsulta = null) }

        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.consultarPorTarjeta(idTarjeta.normalizada()) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { perfil ->
                        previo.copy(
                            fase = FaseEscaneo.PERFIL_DISPONIBLE,
                            perfil = perfil,
                            edadPaciente = CalendarioSalud.edadEnAnios(
                                fechaNacimiento = perfil.datosPersonales.fechaNacimiento,
                                hoy = reloj.fechaHoy(),
                            ),
                            errorConsulta = null,
                            confirmarConVibracion = true,
                        )
                    },
                    onFailure = { fallo ->
                        previo.copy(
                            fase = FaseEscaneo.ERROR,
                            perfil = null,
                            edadPaciente = null,
                            errorConsulta = fallo.aErrorEscaneo(),
                        )
                    },
                )
            }
        }
    }

    /**
     * Fallo de la antena, no del backend: se informa sin abandonar la espera
     * para que baste con volver a acercar la tarjeta.
     */
    private fun alFallarLectura(error: ErrorLecturaNfc) {
        _estado.update {
            it.copy(
                fase = FaseEscaneo.ESPERANDO_TARJETA,
                errorConsulta = when (error) {
                    ErrorLecturaNfc.LECTURA_INTERRUMPIDA -> ErrorEscaneo.LECTURA_INTERRUMPIDA
                    ErrorLecturaNfc.TARJETA_NO_VALIDA -> ErrorEscaneo.TARJETA_NO_VALIDA
                },
            )
        }
    }

    /** Cualquier excepcion no tipada se trata como corte de red: nunca se propaga. */
    private fun Throwable.aErrorEscaneo(): ErrorEscaneo =
        when ((this as? FalloEmergencia)?.motivo) {
            MotivoFalloEmergencia.TARJETA_DESCONOCIDA -> ErrorEscaneo.TARJETA_DESCONOCIDA
            MotivoFalloEmergencia.TARJETA_REVOCADA -> ErrorEscaneo.TARJETA_REVOCADA
            MotivoFalloEmergencia.SIN_CONEXION, null -> ErrorEscaneo.SIN_CONEXION
        }

    /** El UID llega con formato variable segun la antena; el backend lo espera en hexadecimal en mayusculas. */
    private fun String.normalizada(): String = trim().uppercase()

    override fun onCleared() {
        lector.detener()
        super.onCleared()
    }
}
