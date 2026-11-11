package com.carlosalbertoxw.boveda.datos

/**
 * Una contrasena que esta entrada tuvo alguna vez, incluida la de ahora.
 *
 * Guardar contrasenas viejas suena a lo contrario de lo que hace un gestor, y
 * hay que justificarlo: la contrasena vieja no se vuelve inofensiva al
 * cambiarla. Sigue abriendo las sesiones que quedaron iniciadas, los respaldos
 * hechos con ella y las cuentas donde el cambio no llego a aplicarse. Tenerla
 * a mano —dentro del mismo archivo cifrado, no en una libreta— es lo que
 * permite recuperar una cuenta cuando el cambio se hizo a medias.
 *
 * Por eso mismo el historial se puede podar entrada por entrada: si una
 * contrasena vieja ya no le sirve a nadie, lo mejor que se puede hacer con
 * ella es que deje de existir: cada app ofrece borrar una por una.
 *
 * [actual] es la marca de "esta es la que vale hoy". La verdad sobre cual usa
 * la cuenta la sigue teniendo [Credencial.contrasena]; la marca dice cual de
 * las filas del historial le corresponde, y las dos se mueven juntas dentro de
 * la misma transaccion para que no puedan discrepar. [Respaldo.normaliza] es
 * quien lo garantiza para lo que llega de fuera.
 */
data class HistorialContrasena(
    val id: Long = 0,
    val credencialId: Long,
    val contrasena: String,
    /** Cuando esta contrasena empezo a ser la de la entrada. */
    val creadoEn: Long = System.currentTimeMillis(),
    val actual: Boolean = false
)
