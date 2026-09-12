package com.eter.salud.data.repository

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio

/**
 * Directorio Medico simulado para desarrollo y pruebas de interfaz.
 *
 * Reutiliza el `idMedico` de la Dra. Elena Ruiz (`doc_889900A`) del portal de
 * personal medico: en un backend real es la misma cuenta la que aparece aqui y
 * la que inicia sesion como profesional.
 *
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar el
 * ViewModel, que solo depende de [DirectorioMedicoRepositorio].
 */
class DirectorioMedicoRepositorioMock(
    private val doctores: List<PerfilDoctorDirectorio> = DOCTORES_DEMO,
    /**
     * Vinculaciones de partida: paciente -> identificadores de sus medicos. Los
     * pacientes que no aparecen aqui arrancan con la semilla de siempre.
     */
    private val vinculacionesIniciales: Map<String, List<String>> = emptyMap(),
) : DirectorioMedicoRepositorio {

    private val vinculacionesPorPaciente = mutableMapOf<String, MutableList<MedicoVinculado>>()

    /**
     * Semilla de la bandeja: todo paciente arranca vinculado con la Dra. Elena
     * Ruiz. Sin ella la bandeja aparece vacia en la primera ejecucion y no hay
     * forma de juzgar el diseno ni de ensayar el chat.
     */
    private fun vinculadosDe(idPaciente: String): MutableList<MedicoVinculado> =
        vinculacionesPorPaciente.getOrPut(idPaciente) {
            val iniciales = vinculacionesIniciales[idPaciente]
            if (iniciales != null) {
                return@getOrPut iniciales.mapNotNull { idMedico ->
                    doctores.firstOrNull { it.idMedico == idMedico }?.let { doctor ->
                        MedicoVinculado(
                            idMedico = doctor.idMedico,
                            nombreCompleto = doctor.nombreCompleto,
                            especialidad = doctor.especialidad,
                            idConversacion = "conv_${idPaciente}_${doctor.idMedico}",
                        )
                    }
                }.toMutableList()
            }
            mutableListOf(
                MedicoVinculado(
                    idMedico = MEDICO_SEMILLA.idMedico,
                    nombreCompleto = MEDICO_SEMILLA.nombreCompleto,
                    especialidad = MEDICO_SEMILLA.especialidad,
                    idConversacion = "conv_${idPaciente}_${MEDICO_SEMILLA.idMedico}",
                ),
            )
        }

    override suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?> =
        Result.success(vinculadosDe(idPaciente).firstOrNull())

    override suspend fun obtenerMedicosVinculados(
        idPaciente: String,
    ): Result<List<MedicoVinculado>> = Result.success(vinculadosDe(idPaciente).toList())

    override suspend fun buscarDirectorio(
        especialidad: Especialidad?,
    ): Result<List<PerfilDoctorDirectorio>> {
        val resultado = if (especialidad == null) doctores else {
            doctores.filter { it.especialidad == especialidad }
        }
        return Result.success(resultado)
    }

    override suspend fun obtenerPerfilDeMedico(
        idMedico: String,
    ): Result<PerfilDoctorDirectorio?> =
        Result.success(doctores.firstOrNull { it.idMedico == idMedico })

    override suspend fun solicitarVinculacion(
        idPaciente: String,
        idMedico: String,
    ): Result<MedicoVinculado> {
        val doctor = doctores.firstOrNull { it.idMedico == idMedico }
            ?: return Result.failure(NoSuchElementException("Doctor no encontrado: $idMedico"))
        val vinculado = MedicoVinculado(
            idMedico = doctor.idMedico,
            nombreCompleto = doctor.nombreCompleto,
            especialidad = doctor.especialidad,
            idConversacion = "conv_${idPaciente}_${doctor.idMedico}",
        )
        val yaVinculados = vinculadosDe(idPaciente)
        // Vincularse dos veces con el mismo doctor no abre una segunda
        // conversacion: la bandeja mostraria la misma persona dos veces.
        if (yaVinculados.none { it.idMedico == idMedico }) yaVinculados += vinculado
        return Result.success(vinculado)
    }

    companion object {
        /** La cardiologa con la que todo paciente arranca vinculado. */
        val MEDICO_SEMILLA: PerfilDoctorDirectorio get() = DOCTORES_DEMO.first()

        val DOCTORES_DEMO = listOf(
            // Misma cuenta que la Dra. Elena Ruiz del portal de personal
            // medico (doc_889900A): universidad y disponibilidad tal como se
            // pidieron para probar la tarjeta del directorio.
            PerfilDoctorDirectorio(
                idMedico = "doc_889900A",
                nombreCompleto = "Dra. Elena Ruiz Santos",
                especialidad = Especialidad.CARDIOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Politecnica",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_2",
                nombreCompleto = "Dr. Carlos Mendoza",
                especialidad = Especialidad.MEDICINA_GENERAL,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Durango",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_3",
                nombreCompleto = "Dra. Sofia Torres",
                especialidad = Especialidad.PEDIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Nacional Autonoma de Mexico",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_4",
                nombreCompleto = "Dr. Andres Paredes",
                especialidad = Especialidad.DERMATOLOGIA,
                // Cedula aun en tramite: para ensayar el caso sin la insignia.
                cedulaVerificada = false,
                universidad = "Universidad de Guadalajara",
                disponibilidad = "Disponible en dos dias",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_5",
                nombreCompleto = "Dra. Paula Jimenez",
                especialidad = Especialidad.GINECOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Nuevo Leon",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_6",
                nombreCompleto = "Dr. Ivan Salcedo",
                especialidad = Especialidad.PSIQUIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Durango",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_7",
                nombreCompleto = "Dra. Lucia Navarro Pena",
                especialidad = Especialidad.ENDOCRINOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Juarez del Estado de Durango",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_8",
                nombreCompleto = "Dr. Hector Gutierrez Luna",
                especialidad = Especialidad.GERIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Nacional Autonoma de Mexico",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_9",
                nombreCompleto = "Dra. Andrea Castillo Rios",
                especialidad = Especialidad.NEUMOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad de Guadalajara",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_10",
                nombreCompleto = "Dr. Miguel Reyes Soto",
                especialidad = Especialidad.NEUROLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Nuevo Leon",
                disponibilidad = "Disponible en dos dias",
            ),
            // Segunda plantilla: tres opciones por especialidad, para que el
            // paciente de verdad ESCOJA y no acepte al unico que hay.
            PerfilDoctorDirectorio(
                idMedico = "doc_local_11",
                nombreCompleto = "Dra. Mariana Ochoa Zamora",
                especialidad = Especialidad.MEDICINA_GENERAL,
                cedulaVerificada = true,
                universidad = "Universidad Juarez del Estado de Durango",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_12",
                nombreCompleto = "Dr. Rodrigo Barraza Nevarez",
                especialidad = Especialidad.MEDICINA_GENERAL,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Coahuila",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_13",
                nombreCompleto = "Dr. Fernando Aguilar Cisneros",
                especialidad = Especialidad.CARDIOLOGIA,
                cedulaVerificada = true,
                universidad = "Instituto Politecnico Nacional",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_14",
                nombreCompleto = "Dra. Gabriela Solis Ramos",
                especialidad = Especialidad.CARDIOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad de Monterrey",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_15",
                nombreCompleto = "Dr. Julian Esparza Cordero",
                especialidad = Especialidad.PEDIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Juarez del Estado de Durango",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_16",
                nombreCompleto = "Dra. Valeria Montes Ibarra",
                especialidad = Especialidad.PEDIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Queretaro",
                disponibilidad = "Disponible en dos dias",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_17",
                nombreCompleto = "Dra. Renata Villalobos Mena",
                especialidad = Especialidad.DERMATOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Nacional Autonoma de Mexico",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_18",
                nombreCompleto = "Dr. Oscar Delgado Fuentes",
                especialidad = Especialidad.DERMATOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Chihuahua",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_19",
                nombreCompleto = "Dra. Ximena Robles Arce",
                especialidad = Especialidad.GINECOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Nuevo Leon",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_20",
                nombreCompleto = "Dr. Ricardo Palacios Olvera",
                especialidad = Especialidad.GINECOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de San Luis Potosi",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_21",
                nombreCompleto = "Dra. Daniela Cabrera Luna",
                especialidad = Especialidad.PSIQUIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Nacional Autonoma de Mexico",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_22",
                nombreCompleto = "Dr. Tomas Arellano Rivas",
                especialidad = Especialidad.PSIQUIATRIA,
                // Cedula en tramite: el directorio la muestra sin insignia.
                cedulaVerificada = false,
                universidad = "Universidad de Guadalajara",
                disponibilidad = "Disponible en dos dias",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_23",
                nombreCompleto = "Dra. Beatriz Quintero Salas",
                especialidad = Especialidad.GERIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Juarez del Estado de Durango",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_24",
                nombreCompleto = "Dr. Samuel Ortega Beltran",
                especialidad = Especialidad.GERIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Durango",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_25",
                nombreCompleto = "Dr. Emilio Carrillo Duarte",
                especialidad = Especialidad.ENDOCRINOLOGIA,
                cedulaVerificada = true,
                universidad = "Tecnologico de Monterrey",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_26",
                nombreCompleto = "Dra. Isabel Herrera Campos",
                especialidad = Especialidad.ENDOCRINOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Yucatan",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_27",
                nombreCompleto = "Dr. Alejandro Nunez Paz",
                especialidad = Especialidad.NEUMOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Sinaloa",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_28",
                nombreCompleto = "Dra. Monica Figueroa Leal",
                especialidad = Especialidad.NEUMOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Nacional Autonoma de Mexico",
                disponibilidad = "Disponible en dos dias",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_29",
                nombreCompleto = "Dra. Carolina Espinoza Trevino",
                especialidad = Especialidad.NEUROLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Nuevo Leon",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_30",
                nombreCompleto = "Dr. Hugo Mejia Saldana",
                especialidad = Especialidad.NEUROLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Durango",
                disponibilidad = "Disponible manana",
            ),
        )
    }
}
