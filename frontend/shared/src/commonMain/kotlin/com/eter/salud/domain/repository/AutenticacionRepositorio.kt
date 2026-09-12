package com.eter.salud.domain.repository

import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.model.SesionPaciente

/**
 * Contrato de acceso hacia la API en Go. El ViewModel solo conoce esta interfaz.
 */
interface AutenticacionRepositorio {

    /**
     * Verifica las credenciales del paciente.
     * @param correo ya normalizado por la capa de presentacion.
     * @param contrasena tal como la escribio el paciente, sin recortar.
     */
    suspend fun iniciarSesion(correo: String, contrasena: String): Result<SesionPaciente>

    /**
     * Da de alta la cuenta y devuelve la sesion ya abierta, para que el paciente
     * no tenga que volver a escribir lo mismo en la pantalla de acceso.
     *
     * Vive junto a [iniciarSesion] porque comparten frontera: ambas son las
     * unicas operaciones que la app puede pedir sin token.
     */
    suspend fun crearCuenta(correo: String, contrasena: String): Result<SesionPaciente>
}

/**
 * Fallo de acceso con motivo tipado. Sin esto, la capa de presentacion tendria
 * que adivinar la causa leyendo un mensaje de texto del backend.
 */
class FalloAutenticacion(
    val motivo: MotivoFalloAutenticacion,
) : Exception(motivo.name)

/** Fallo de alta de cuenta con motivo tipado. */
class FalloRegistro(
    val motivo: MotivoFalloRegistro,
) : Exception(motivo.name)
