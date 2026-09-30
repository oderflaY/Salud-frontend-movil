package com.eter.salud.presentation.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.data.expediente.ExpedienteCompartidoRepositorio
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.BloqueDeExpediente
import com.eter.salud.domain.model.CategoriaDocumento
import com.eter.salud.domain.model.DocumentoClinico
import com.eter.salud.domain.model.ExpedienteCompartido
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de "Lo que ve tu medico" dentro del historial del paciente. */
data class ExpedienteCompartidoUiState(
    val cargando: Boolean = true,
    /** El expediente completo: de aqui sale el resumen de cada bloque. */
    val paciente: PacienteDto? = null,
    val compartido: ExpedienteCompartido = ExpedienteCompartido(),
    /**
     * Archivo recien elegido que espera a que el paciente diga que es
     * (radiografia, laboratorio...). Todavia no esta en el expediente.
     */
    val documentoPorClasificar: Adjunto? = null,
) {
    /** Documentos del mas reciente al mas antiguo, visibles o no. */
    val documentos: List<DocumentoClinico>
        get() = compartido.documentos.sortedByDescending { it.fecha }
}

/**
 * El paciente decide que parte de su expediente ve su medico y adjunta
 * estudios (radiografias escaneadas, laboratorios, fotos).
 *
 * Cada cambio se guarda al instante, igual que los de la tarjeta de
 * emergencia: es una decision sobre su privacidad, y un boton de "guardar"
 * olvidado dejaria visible lo que creia haber ocultado.
 */
class ExpedienteCompartidoViewModel(
    private val historial: HistorialMedicoRepositorio,
    private val compartido: ExpedienteCompartidoRepositorio,
    private val idPaciente: String,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(ExpedienteCompartidoUiState())
    val estado: StateFlow<ExpedienteCompartidoUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val paciente = ejecutarSeguro { historial.obtenerPaciente(idPaciente) }.getOrNull()
            _estado.update { it.copy(paciente = paciente) }
        }
        viewModelScope.launch {
            compartido.de(idPaciente).collect { actual ->
                _estado.update { it.copy(cargando = false, compartido = actual) }
            }
        }
    }

    fun cambiarBloque(bloque: BloqueDeExpediente, visible: Boolean) {
        viewModelScope.launch { compartido.cambiarBloque(idPaciente, bloque, visible) }
    }

    /**
     * Llega el archivo que se eligio (galeria, archivo o escaner). No se guarda
     * todavia: primero se pregunta que es, porque "IMG_2026.jpg" no le dice
     * nada al medico y "Radiografia de torax" si.
     */
    fun prepararDocumento(adjunto: Adjunto) {
        _estado.update { it.copy(documentoPorClasificar = adjunto) }
    }

    fun cancelarDocumento() {
        _estado.update { it.copy(documentoPorClasificar = null) }
    }

    fun guardarDocumento(categoria: CategoriaDocumento, titulo: String) {
        val adjunto = _estado.value.documentoPorClasificar ?: return
        val documento = DocumentoClinico(
            idDocumento = "doc_${adjunto.idAdjunto}",
            titulo = titulo.trim().ifBlank { adjunto.nombre },
            categoria = categoria,
            nombreArchivo = adjunto.nombre,
            rutaLocal = adjunto.rutaLocal,
            tipoMime = adjunto.tipoMime,
            fecha = reloj.fechaHoy(),
        )
        _estado.update { it.copy(documentoPorClasificar = null) }
        viewModelScope.launch { compartido.agregarDocumento(idPaciente, documento) }
    }

    fun cambiarVisibilidad(idDocumento: String, visible: Boolean) {
        viewModelScope.launch { compartido.cambiarVisibilidad(idPaciente, idDocumento, visible) }
    }

    fun eliminarDocumento(idDocumento: String) {
        viewModelScope.launch { compartido.eliminarDocumento(idPaciente, idDocumento) }
    }

    companion object {
        /** La categoria que se sugiere segun como se consiguio el archivo. */
        fun categoriaSugerida(adjunto: Adjunto): CategoriaDocumento = when (adjunto.tipo) {
            // El escaner es lo que se usa para una radiografia o un estudio en papel.
            TipoAdjunto.ESCANEO -> CategoriaDocumento.RADIOGRAFIA
            TipoAdjunto.FOTO -> CategoriaDocumento.FOTO
            TipoAdjunto.ARCHIVO ->
                if (adjunto.tipoMime == "application/pdf") CategoriaDocumento.LABORATORIO else CategoriaDocumento.OTRO
        }
    }
}
