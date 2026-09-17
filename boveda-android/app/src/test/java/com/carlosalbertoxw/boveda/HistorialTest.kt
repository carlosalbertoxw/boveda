package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Respaldo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La disciplina de la marca de "actual" cuando el historial viene de fuera.
 *
 * El resto de la app da por hecho que cada entrada tiene exactamente una
 * contrasena marcada y que esa marca coincide con la contrasena de la
 * credencial. Dentro de la base eso lo garantizan las transacciones del DAO,
 * pero un respaldo es un archivo de texto que alguien pudo editar a mano, o que
 * puede venir de la version 1 y no traer historial en absoluto. Aqui se
 * comprueba que nada de eso entra descuadrado.
 */
class HistorialTest {

    private val credencial = Credencial(
        titulo = "Banco",
        usuario = "carlos",
        contrasena = "la de ahora",
        actualizadoEn = 1_700_000_000_000
    )

    private fun anotacion(contrasena: String, actual: Boolean = false, creadoEn: Long = 0) =
        HistorialContrasena(contrasena = contrasena, credencialId = 0, creadoEn = creadoEn, actual = actual)

    @Test
    fun `sin historial se abre uno con la contrasena de la credencial`() {
        val entrada = Respaldo.normaliza(credencial, emptyList())

        // Es el caso de todo archivo de la version 1.
        assertEquals(1, entrada.historial.size)
        assertEquals("la de ahora", entrada.historial.single().contrasena)
        assertTrue(entrada.historial.single().actual)
        assertEquals(credencial.actualizadoEn, entrada.historial.single().creadoEn)
    }

    @Test
    fun `un historial bien formado se queda como esta`() {
        val historial = listOf(anotacion("la de ahora", actual = true), anotacion("la vieja"))

        val entrada = Respaldo.normaliza(credencial, historial)

        assertEquals(listOf("la de ahora", "la vieja"), entrada.historial.map { it.contrasena })
        assertEquals(listOf(true, false), entrada.historial.map { it.actual })
    }

    @Test
    fun `con dos marcas solo sobrevive una`() {
        val historial = listOf(
            anotacion("la de ahora", actual = true),
            anotacion("la vieja", actual = true)
        )

        val entrada = Respaldo.normaliza(credencial, historial)

        assertEquals(1, entrada.historial.count { it.actual })
        assertEquals("la de ahora", entrada.historial.first { it.actual }.contrasena)
    }

    @Test
    fun `sin ninguna marca la recibe la que coincide con la credencial`() {
        val historial = listOf(anotacion("la vieja"), anotacion("la de ahora"))

        val entrada = Respaldo.normaliza(credencial, historial)

        assertEquals(2, entrada.historial.size)
        assertEquals("la de ahora", entrada.historial.single { it.actual }.contrasena)
    }

    @Test
    fun `una marca puesta en una contrasena que la credencial ya no usa se corrige`() {
        val historial = listOf(anotacion("la vieja", actual = true), anotacion("la de ahora"))

        val entrada = Respaldo.normaliza(credencial, historial)

        // La verdad la tiene la credencial, no el archivo: si discrepan, la
        // marca se mueve, no se cambia la contrasena de la entrada.
        assertEquals("la de ahora", entrada.historial.single { it.actual }.contrasena)
        assertEquals("la de ahora", entrada.credencial.contrasena)
    }

    @Test
    fun `si la contrasena de la credencial no esta en el historial se agrega`() {
        val historial = listOf(anotacion("una vieja"), anotacion("otra vieja", actual = true))

        val entrada = Respaldo.normaliza(credencial, historial)

        // Nada de lo que trae el archivo se tira: solo pierde la marca.
        assertEquals(3, entrada.historial.size)
        assertEquals(1, entrada.historial.count { it.actual })
        assertEquals("la de ahora", entrada.historial.first { it.actual }.contrasena)
        assertTrue(entrada.historial.map { it.contrasena }.containsAll(listOf("una vieja", "otra vieja")))
    }
}
