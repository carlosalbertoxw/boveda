package com.carlosalbertoxw.boveda.seguridad

import java.security.SecureRandom

/**
 * Generador de contrasenas.
 *
 * Usa SecureRandom y no Random: el segundo es predecible si se conoce la
 * semilla, y una contrasena adivinable no sirve de nada. El sorteo se hace con
 * [SecureRandom.nextInt] sobre el tamano del alfabeto, sin modulo sobre un byte,
 * para no darle mas probabilidad a los primeros caracteres del conjunto.
 */
object Generador {

    private const val MINUSCULAS = "abcdefghijkmnopqrstuvwxyz"
    private const val MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITOS = "23456789"
    private const val SIMBOLOS = "!#$%&*+-=?@_"
    // Sin l, I, 1, 0 ni O: se confunden al leerlas de la pantalla para teclearlas.

    private val azar = SecureRandom()

    data class Ajustes(
        val largo: Int = 20,
        val mayusculas: Boolean = true,
        val digitos: Boolean = true,
        val simbolos: Boolean = true
    )

    fun genera(ajustes: Ajustes): String {
        val grupos = buildList {
            add(MINUSCULAS)
            if (ajustes.mayusculas) add(MAYUSCULAS)
            if (ajustes.digitos) add(DIGITOS)
            if (ajustes.simbolos) add(SIMBOLOS)
        }
        val alfabeto = grupos.joinToString("")
        val largo = ajustes.largo.coerceAtLeast(grupos.size)

        // Un caracter obligatorio de cada grupo, para que activar "simbolos" no
        // pueda salir sin ni un simbolo por mala suerte...
        val salida = grupos.map { it[azar.nextInt(it.length)] }.toMutableList()
        repeat(largo - grupos.size) { salida.add(alfabeto[azar.nextInt(alfabeto.length)]) }

        // ...y se barajan, porque si no el orden de los grupos seria fijo y
        // acortaria la busqueda para quien conozca el generador.
        for (i in salida.indices.reversed()) {
            val j = azar.nextInt(i + 1)
            salida[i] = salida[j].also { salida[j] = salida[i] }
        }
        return salida.joinToString("")
    }
}
