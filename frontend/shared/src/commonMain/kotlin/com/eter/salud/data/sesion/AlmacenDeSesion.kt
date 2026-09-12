package com.eter.salud.data.sesion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.presentation.sesion.SesionUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Sesion guardada en disco.
 *
 * Es lo que hace que cerrar la app -- o que el sistema mate el proceso por falta
 * de memoria -- no expulse al paciente a la pantalla de acceso. El
 * `SesionViewModel` ya sobrevivia a girar el telefono; esto cubre el caso que
 * un ViewModel no puede cubrir.
 *
 * ## Por que DataStore y no SQLite
 *
 * Son cuatro claves: quien, con que papel, y el token. Montar una base de datos
 * relacional para eso costaria un dia de trabajo y no guardaria mejor un
 * booleano. SQLite entra cuando haya expediente e historial que consultar.
 *
 * ## Que NO se guarda aqui
 *
 * Ningun dato clinico. `DataStore Preferences` no esta cifrado: en un telefono
 * con root, su archivo se lee. El token de acceso ya es sensible y va aqui por
 * necesidad -- sin el no hay sesion que restaurar -- pero alergias, diagnosticos
 * o medicacion no entran, y cuando exista cifrado de verdad el token se mueve.
 */
/**
 * Contrato de la sesion duradera.
 *
 * Existe para que [com.eter.salud.presentation.sesion.SesionViewModel] no
 * dependa de DataStore: asi su logica de restauracion -- que es la que evita que
 * el paciente acabe en la pantalla de acceso -- se puede probar sin tocar disco,
 * y cambiar DataStore por SQLite mas adelante no toca el ViewModel.
 */
interface FuenteDeSesion {
    val sesion: Flow<SesionUiState>
    suspend fun guardarPaciente(sesion: SesionPaciente)
    suspend fun guardarProfesional(sesion: SesionProfesional)
    suspend fun borrar()
}

class AlmacenDeSesion(
    private val preferencias: DataStore<Preferences>,
) : FuenteDeSesion {

    /**
     * Sesion persistida, en vivo.
     *
     * Un error de lectura se traduce a "no hay sesion" en vez de propagarse: si
     * el archivo esta corrupto, lo correcto es pedir acceso otra vez, no
     * impedir que la app arranque.
     */
    override val sesion: Flow<SesionUiState> = preferencias.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { guardadas -> guardadas.aSesion() }

    override suspend fun guardarPaciente(sesion: SesionPaciente) {
        preferencias.edit { datos ->
            datos.clear()
            datos[CLAVE_PAPEL] = PAPEL_PACIENTE
            datos[CLAVE_ID] = sesion.idPaciente
            datos[CLAVE_TOKEN] = sesion.token
            datos[CLAVE_REQUIERE_ONBOARDING] = sesion.requiereOnboarding
        }
    }

    override suspend fun guardarProfesional(sesion: SesionProfesional) {
        preferencias.edit { datos ->
            datos.clear()
            datos[CLAVE_PAPEL] = PAPEL_PROFESIONAL
            datos[CLAVE_ID] = sesion.idMedico
            datos[CLAVE_TOKEN] = sesion.token
            datos[CLAVE_NOMBRE] = sesion.nombre
            datos[CLAVE_TRATAMIENTO] = sesion.tratamiento
            datos[CLAVE_APELLIDOS] = sesion.apellidos
            datos[CLAVE_VERIFICACION] = sesion.estadoVerificacion.name
        }
    }

    /**
     * Borra la sesion entera.
     *
     * `clear()` y no borrar clave por clave: si manana se anade un campo y
     * alguien olvida sumarlo aqui, quedaria un resto de la sesion anterior al
     * alcance de la siguiente persona que abra la app.
     */
    override suspend fun borrar() {
        preferencias.edit { it.clear() }
    }

    private fun Preferences.aSesion(): SesionUiState {
        val id = this[CLAVE_ID] ?: return SesionUiState()
        return when (this[CLAVE_PAPEL]) {
            PAPEL_PACIENTE -> SesionUiState(
                paciente = SesionPaciente(
                    idPaciente = id,
                    token = this[CLAVE_TOKEN].orEmpty(),
                    requiereOnboarding = this[CLAVE_REQUIERE_ONBOARDING] ?: false,
                ),
            )

            PAPEL_PROFESIONAL -> SesionUiState(
                profesional = SesionProfesional(
                    idMedico = id,
                    token = this[CLAVE_TOKEN].orEmpty(),
                    nombre = this[CLAVE_NOMBRE].orEmpty(),
                    apellidos = this[CLAVE_APELLIDOS].orEmpty(),
                    tratamiento = this[CLAVE_TRATAMIENTO].orEmpty(),
                    estadoVerificacion = estadoDesde(this[CLAVE_VERIFICACION]),
                ),
            )

            // Papel desconocido: se trata como si no hubiera sesion. Es lo que
            // pasaria tras una actualizacion que cambiara los valores, y adivinar
            // uno abriria un portal equivocado con datos del otro.
            else -> SesionUiState()
        }
    }

    private companion object {
        val CLAVE_PAPEL = stringPreferencesKey("sesion_papel")
        val CLAVE_ID = stringPreferencesKey("sesion_id")
        val CLAVE_TOKEN = stringPreferencesKey("sesion_token")
        val CLAVE_REQUIERE_ONBOARDING = booleanPreferencesKey("sesion_requiere_onboarding")
        val CLAVE_NOMBRE = stringPreferencesKey("sesion_nombre")
        val CLAVE_TRATAMIENTO = stringPreferencesKey("sesion_tratamiento")
        val CLAVE_APELLIDOS = stringPreferencesKey("sesion_apellidos")
        val CLAVE_VERIFICACION = stringPreferencesKey("sesion_verificacion")

        const val PAPEL_PACIENTE = "paciente"
        const val PAPEL_PROFESIONAL = "profesional"
    }
}

/**
 * Traduce el estado de cedula guardado.
 *
 * Un valor desconocido cae a PENDIENTE, nunca a APROBADO: si una actualizacion
 * cambia los nombres del enum, el fallo seguro es pedir verificacion otra vez,
 * no dar por habilitado a un profesional cuya cedula nadie comprobo.
 */
private fun estadoDesde(guardado: String?): EstadoVerificacionCedula =
    EstadoVerificacionCedula.entries.firstOrNull { it.name == guardado }
        ?: EstadoVerificacionCedula.PENDIENTE

/** Nombre del archivo en disco. Igual en las dos plataformas. */
internal const val ARCHIVO_DE_SESION = "sesion.preferences_pb"
