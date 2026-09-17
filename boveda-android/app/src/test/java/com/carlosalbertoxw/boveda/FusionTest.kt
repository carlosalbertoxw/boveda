package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Respaldo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La regla de "esto ya lo tengo" al importar un respaldo.
 *
 * Importa mas de lo que parece: la gente importa dos veces cuando no esta
 * seguemos de si entro la primera. Sin deduplicado, ese gesto duplica la boveda
 * entera y deja al usuario con dos copias de cada contrasena que ya no sabe
 * cual borrar.
 */
class FusionTest {

    private fun credencial(
        titulo: String,
        usuario: String = "yo",
        contrasena: String = "clave"
    ) = Credencial(titulo = titulo, usuario = usuario, contrasena = contrasena)

    /** Lo que trae el archivo: una credencial con su historial detras. */
    private fun entrada(
        titulo: String,
        usuario: String = "yo",
        contrasena: String = "clave"
    ) = Respaldo.Entrada(
        credencial(titulo, usuario, contrasena),
        listOf(HistorialContrasena(credencialId = 0, contrasena = contrasena, actual = true))
    )

    @Test
    fun `sobre una boveda vacia entra todo`() {
        val entrantes = listOf(entrada("Banco"), entrada("Correo"))

        val plan = GestorRespaldo.Fusion.planifica(emptyList(), entrantes)

        assertEquals(2, plan.nuevas.size)
        assertEquals(0, plan.omitidas)
    }

    @Test
    fun `importar el mismo archivo dos veces no duplica nada`() {
        val entrantes = listOf(entrada("Banco"), entrada("Correo"))

        val plan = GestorRespaldo.Fusion.planifica(entrantes.map { it.credencial }, entrantes)

        assertEquals(0, plan.nuevas.size)
        assertEquals(2, plan.omitidas)
    }

    @Test
    fun `lo que falta entra y lo repetido se omite`() {
        val existentes = listOf(credencial("Banco"))
        val entrantes = listOf(entrada("Banco"), entrada("Correo"))

        val plan = GestorRespaldo.Fusion.planifica(existentes, entrantes)

        assertEquals(listOf("Correo"), plan.nuevas.map { it.credencial.titulo })
        assertEquals(1, plan.omitidas)
    }

    @Test
    fun `mismo titulo con otra contrasena es otra entrada`() {
        val existentes = listOf(credencial("Banco", contrasena = "vieja"))
        val entrantes = listOf(entrada("Banco", contrasena = "nueva"))

        // Es deliberado: si se omitiera, importar un respaldo mas reciente
        // perderia la contrasena actualizada sin decir nada.
        val plan = GestorRespaldo.Fusion.planifica(existentes, entrantes)

        assertEquals(1, plan.nuevas.size)
        assertEquals(0, plan.omitidas)
    }

    @Test
    fun `mismo titulo con otro usuario es otra entrada`() {
        val existentes = listOf(credencial("Correo", usuario = "uno"))
        val entrantes = listOf(entrada("Correo", usuario = "otro"))

        val plan = GestorRespaldo.Fusion.planifica(existentes, entrantes)

        assertEquals(1, plan.nuevas.size)
        assertEquals(0, plan.omitidas)
    }

    @Test
    fun `un archivo con duplicados dentro solo aporta uno`() {
        val entrantes = listOf(entrada("Banco"), entrada("Banco"), entrada("Banco"))

        val plan = GestorRespaldo.Fusion.planifica(emptyList(), entrantes)

        assertEquals(1, plan.nuevas.size)
        assertEquals(2, plan.omitidas)
    }

    @Test
    fun `lo que esta en la papelera tambien cuenta como que ya lo tengo`() {
        val borrada = credencial("Banco").copy(eliminadoEn = 1_700_000_000_000)
        val entrantes = listOf(entrada("Banco"))

        val plan = GestorRespaldo.Fusion.planifica(listOf(borrada), entrantes)

        // Si la papelera no contara, importar dejaria una copia activa al lado
        // de la que el usuario acaba de borrar: el borrado se desharia solo y
        // sin decir nada. Para recuperarla esta el boton de restaurar.
        assertEquals(0, plan.nuevas.size)
        assertEquals(1, plan.omitidas)
    }

    @Test
    fun `todo lo entrante se reparte entre lo que entra y lo que se omite`() {
        val existentes = listOf(credencial("Banco"), credencial("Correo"))
        val entrantes = listOf(
            entrada("Banco"),
            entrada("Correo"),
            entrada("Wifi"),
            entrada("Wifi"),
            entrada("Trabajo")
        )

        val plan = GestorRespaldo.Fusion.planifica(existentes, entrantes)

        // Ninguna entrada del archivo puede perderse por el camino sin quedar
        // contada en alguno de los dos lados: es lo que se le informa al usuario.
        assertEquals(entrantes.size, plan.nuevas.size + plan.omitidas)
    }
}
