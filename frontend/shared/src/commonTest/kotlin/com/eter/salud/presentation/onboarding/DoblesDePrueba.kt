package com.eter.salud.presentation.onboarding

import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.repository.PacienteRepositorio
import com.eter.salud.domain.time.RelojSalud

/** Reloj determinista: congela el tiempo para que las pruebas no dependan de hoy. */
class RelojFijo(
    private val fecha: String = "2026-09-05",
    private val instante: String = "2026-09-05T08:00:00Z",
    private val hora: String = "08:00",
) : RelojSalud {
    override fun fechaHoy(): String = fecha
    override fun instanteActual(): String = instante

    /**
     * Devuelve una hora fija: convertir a la zona horaria real haria que las
     * pruebas pasaran o fallaran segun la maquina donde corren.
     */
    override fun horaLocal(instanteIso: String): String =
        if (instanteIso.isBlank()) "" else hora
}

/** Repositorio falso: captura el DTO enviado y permite simular fallos de red. */
class PacienteRepositorioFalso(
    private val resultado: Result<String> = Result.success("pac_01H8X9A"),
) : PacienteRepositorio {

    var invocaciones: Int = 0
        private set

    var ultimoDtoEnviado: PacienteDto? = null
        private set

    override suspend fun registrarPaciente(paciente: PacienteDto): Result<String> {
        invocaciones++
        ultimoDtoEnviado = paciente
        return resultado
    }
}
