package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.GestorRespaldo.Fusion
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Respaldo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regla de "esta cuenta ya la tengo" al importar un respaldo, y que hacer
 * cuando la tengo con otros datos.
 *
 * Importa mas de lo que parece: la gente importa dos veces cuando no esta
 * segura de si entro la primera, y pasa respaldos entre el telefono y la
 * computadora despues de cambiar contrasenas en uno de los dos. Ninguno de esos
 * gestos debe duplicar cuentas ni perder una contrasena.
 *
 * Este archivo es identico en la app de Android y en la de escritorio.
 */
class FusionTest {

    private val antes = 1_700_000_000_000L
    private val despues = 1_700_000_900_000L

    /** Una entrada de la boveda local, con id y su historial. */
    private fun local(
        id: Long,
        titulo: String,
        usuario: String = "yo",
        contrasena: String = "clave",
        actualizadoEn: Long = antes,
        viejas: List<String> = emptyList()
    ) = Respaldo.Entrada(
        Credencial(
            id = id,
            titulo = titulo,
            usuario = usuario,
            contrasena = contrasena,
            creadoEn = antes,
            actualizadoEn = actualizadoEn
        ),
        listOf(HistorialContrasena(id = id * 10, credencialId = id, contrasena = contrasena, creadoEn = actualizadoEn, actual = true)) +
            viejas.mapIndexed { i, vieja ->
                HistorialContrasena(id = id * 10 + i + 1, credencialId = id, contrasena = vieja, creadoEn = antes - 1000 - i, actual = false)
            }
    )

    /** Lo que trae el archivo: sin ids, con su historial detras. */
    private fun entrada(
        titulo: String,
        usuario: String = "yo",
        contrasena: String = "clave",
        actualizadoEn: Long = antes,
        notas: String = "",
        viejas: List<String> = emptyList()
    ) = Respaldo.Entrada(
        Credencial(
            titulo = titulo,
            usuario = usuario,
            contrasena = contrasena,
            notas = notas,
            creadoEn = antes,
            actualizadoEn = actualizadoEn
        ),
        listOf(HistorialContrasena(credencialId = 0, contrasena = contrasena, creadoEn = actualizadoEn, actual = true)) +
            viejas.map { HistorialContrasena(credencialId = 0, contrasena = it, creadoEn = antes - 5000, actual = false) }
    )

    private fun actual(entrada: Respaldo.Entrada) = entrada.historial.single { it.actual }.contrasena

    private fun Respaldo.Entrada.enPapelera(desde: Long) =
        copy(credencial = credencial.copy(eliminadoEn = desde))

    // --- Lo que no choca --------------------------------------------------

    @Test
    fun `sobre una boveda vacia entra todo`() {
        val plan = Fusion.planifica(emptyList(), listOf(entrada("Banco"), entrada("Correo")))

        assertEquals(2, plan.nuevas.size)
        assertEquals(0, plan.actualizadas.size)
        assertEquals(0, plan.omitidas)
    }

    @Test
    fun `importar el mismo archivo dos veces no duplica ni reescribe nada`() {
        val existentes = listOf(local(1, "Banco"), local(2, "Correo"))

        val plan = Fusion.planifica(existentes, listOf(entrada("Banco"), entrada("Correo")))

        assertEquals(0, plan.nuevas.size)
        assertEquals(0, plan.actualizadas.size)
        assertEquals(2, plan.omitidas)
    }

    @Test
    fun `lo que falta entra y lo repetido se omite`() {
        val plan = Fusion.planifica(listOf(local(1, "Banco")), listOf(entrada("Banco"), entrada("Correo")))

        assertEquals(listOf("Correo"), plan.nuevas.map { it.credencial.titulo })
        assertEquals(1, plan.omitidas)
    }

    @Test
    fun `mismo titulo con otro usuario es otra cuenta`() {
        // Dos "Correo" —el personal y el del trabajo— no son la misma cuenta:
        // por eso el titulo solo no sirve de clave.
        val plan = Fusion.planifica(listOf(local(1, "Correo", usuario = "personal")), listOf(entrada("Correo", usuario = "trabajo")))

        assertEquals(1, plan.nuevas.size)
        assertEquals(0, plan.actualizadas.size)
    }

    @Test
    fun `mayusculas y espacios de sobra no hacen otra cuenta`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco", usuario = "Carlos@Ejemplo.mx")),
            listOf(entrada("  banco ", usuario = "carlos@ejemplo.mx"))
        )

        assertEquals(0, plan.nuevas.size)
    }

    @Test
    fun `solo cambiar las fechas no cuenta como conflicto`() {
        val plan = Fusion.planifica(listOf(local(1, "Banco")), listOf(entrada("Banco", actualizadoEn = despues)))

        assertEquals(0, plan.actualizadas.size)
        assertEquals(1, plan.omitidas)
    }

    // --- Conflictos -------------------------------------------------------

    @Test
    fun `si el archivo es mas reciente gana su contrasena y la local pasa al historial`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco", contrasena = "vieja", actualizadoEn = antes)),
            listOf(entrada("Banco", contrasena = "nueva", actualizadoEn = despues))
        )

        assertEquals(0, plan.nuevas.size)
        val combinada = plan.actualizadas.single()
        assertEquals(1L, combinada.credencial.id)
        assertEquals("nueva", combinada.credencial.contrasena)
        assertEquals(despues, combinada.credencial.actualizadoEn)
        assertEquals("nueva", actual(combinada))
        assertEquals(setOf("nueva", "vieja"), combinada.historial.map { it.contrasena }.toSet())
    }

    @Test
    fun `si la local es mas reciente se queda y la del archivo pasa al historial`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco", contrasena = "la de hoy", actualizadoEn = despues)),
            listOf(entrada("Banco", contrasena = "la de antes", actualizadoEn = antes))
        )

        val combinada = plan.actualizadas.single()
        assertEquals("la de hoy", combinada.credencial.contrasena)
        assertEquals("la de hoy", actual(combinada))
        // No se pierde: puede ser la que sigue abriendo una sesion en el otro equipo.
        assertTrue(combinada.historial.any { it.contrasena == "la de antes" && !it.actual })
    }

    @Test
    fun `las notas tambien salen de la version mas reciente`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco")),
            listOf(entrada("Banco", notas = "codigo de recuperacion: 1234", actualizadoEn = despues))
        )

        assertEquals("codigo de recuperacion: 1234", plan.actualizadas.single().credencial.notas)
    }

    @Test
    fun `las contrasenas viejas del archivo se suman al historial sin repetirse`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco", viejas = listOf("de 2024"))),
            listOf(entrada("Banco", viejas = listOf("de 2024", "de 2023")))
        )

        val combinada = plan.actualizadas.single()
        assertEquals(listOf("clave", "de 2024", "de 2023"), combinada.historial.map { it.contrasena })
        assertEquals(1, combinada.historial.count { it.actual })
    }

    @Test
    fun `un empate de fechas lo gana la local`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco", contrasena = "local")),
            listOf(entrada("Banco", contrasena = "archivo"))
        )

        assertEquals("local", plan.actualizadas.single().credencial.contrasena)
    }

    // --- Papelera y duplicados --------------------------------------------

    @Test
    fun `lo que esta en la papelera tambien cuenta como que ya lo tengo`() {
        val plan = Fusion.planifica(listOf(local(1, "Banco").enPapelera(despues)), listOf(entrada("Banco")))

        // Si la papelera no contara, importar dejaria una copia activa al lado
        // de la que el usuario acaba de borrar: el borrado se desharia solo.
        assertEquals(0, plan.nuevas.size)
        assertEquals(1, plan.omitidas)
    }

    @Test
    fun `combinar con algo de la papelera no lo saca de la papelera`() {
        val plan = Fusion.planifica(
            listOf(local(1, "Banco").enPapelera(despues)),
            listOf(entrada("Banco", contrasena = "nueva", actualizadoEn = despues + 1))
        )

        val combinada = plan.actualizadas.single()
        assertEquals(despues, combinada.credencial.eliminadoEn)
        assertEquals("nueva", combinada.credencial.contrasena)
    }

    @Test
    fun `un archivo con la misma cuenta repetida aporta una sola, la mas reciente`() {
        val plan = Fusion.planifica(
            emptyList(),
            listOf(
                entrada("Banco", contrasena = "primera", actualizadoEn = antes),
                entrada("Banco", contrasena = "segunda", actualizadoEn = despues),
                entrada("Banco", contrasena = "primera", actualizadoEn = antes)
            )
        )

        val unica = plan.nuevas.single()
        assertEquals(2, plan.omitidas)
        assertEquals("segunda", unica.credencial.contrasena)
        assertTrue(unica.historial.any { it.contrasena == "primera" })
    }

    @Test
    fun `si la boveda ya tenia la cuenta repetida se combina con la que coincide`() {
        val existentes = listOf(
            local(1, "Banco", contrasena = "otra", actualizadoEn = despues),
            local(2, "Banco", contrasena = "clave", actualizadoEn = antes)
        )

        val plan = Fusion.planifica(existentes, listOf(entrada("Banco", viejas = listOf("de 2023"))))

        // La que tiene la misma contrasena es la misma cuenta; la otra no se toca.
        assertEquals(2L, plan.actualizadas.single().credencial.id)
    }

    @Test
    fun `todo lo entrante se reparte entre nuevas, actualizadas y omitidas`() {
        val existentes = listOf(local(1, "Banco"), local(2, "Correo"))
        val entrantes = listOf(
            entrada("Banco"),
            entrada("Correo", contrasena = "cambiada", actualizadoEn = despues),
            entrada("Wifi"),
            entrada("Wifi"),
            entrada("Trabajo")
        )

        val plan = Fusion.planifica(existentes, entrantes)

        // Ninguna entrada del archivo puede perderse sin quedar contada: es lo
        // que se le informa al usuario.
        assertEquals(entrantes.size, plan.nuevas.size + plan.actualizadas.size + plan.omitidas)
        assertEquals(2, plan.nuevas.size)
        assertEquals(1, plan.actualizadas.size)
    }

    @Test
    fun `combinar lo identico no devuelve nada que escribir`() {
        assertNull(Fusion.combina(local(1, "Banco"), entrada("Banco")))
        assertNotNull(Fusion.combina(local(1, "Banco"), entrada("Banco", contrasena = "otra")))
    }
}
