package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Respaldo
import com.carlosalbertoxw.boveda.datos.enPapelera
import com.carlosalbertoxw.boveda.seguridad.Kdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El formato del respaldo se prueba a fondo en el nucleo (`boveda-nucleo`,
 * RespaldoTest), pero alli corre contra la libreria org.json. En el telefono
 * corre contra el org.json que trae Android, que es otra implementacion: esta
 * suite repite lo esencial con la de Android, via Robolectric, para que una
 * diferencia entre las dos no rompa los respaldos solo en el telefono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RespaldoAndroidTest {

    private val entradas = listOf(
        Respaldo.Entrada(
            Credencial(
                titulo = "Banco",
                usuario = "carlos@ejemplo.mx",
                contrasena = "N0-la-adivinas #2026",
                sitio = "banco.mx",
                notas = "Cuenta de nómina",
                creadoEn = 1_700_000_000_000,
                actualizadoEn = 1_700_000_500_000
            ),
            listOf(
                HistorialContrasena(credencialId = 0, contrasena = "N0-la-adivinas #2026", creadoEn = 1_700_000_500_000, actual = true),
                HistorialContrasena(credencialId = 0, contrasena = "la vieja", creadoEn = 1_600_000_000_000)
            )
        ),
        Respaldo.Entrada(
            Credencial(
                titulo = "Correo",
                usuario = "carlos",
                contrasena = "otra",
                creadoEn = 1_700_000_100_000,
                actualizadoEn = 1_700_000_100_000,
                eliminadoEn = 1_700_090_000_000
            )
        )
    )

    private fun clave() = "contraseña del respaldo 🔐".toCharArray()

    @Test
    fun `ida y vuelta completa con el org json de Android`() {
        val archivo = Respaldo.escribe(entradas, clave())
        val lectura = Respaldo.lee(archivo, clave())

        assertTrue("$lectura", lectura is Respaldo.Lectura.Ok)
        val leidas = (lectura as Respaldo.Lectura.Ok).entradas.associateBy { it.credencial.titulo }
        val banco = leidas.getValue("Banco")
        assertEquals("Cuenta de nómina", banco.credencial.notas)
        assertEquals(listOf("N0-la-adivinas #2026", "la vieja"), banco.historial.map { it.contrasena })
        assertTrue(leidas.getValue("Correo").credencial.enPapelera)
    }

    @Test
    fun `el respaldo de referencia escrito con Node se lee completo`() {
        // Viene de boveda-nucleo/src/test/resources (ver app/build.gradle.kts).
        val archivo = javaClass.getResourceAsStream("/respaldo-referencia-v2.json")!!.use { it.readBytes() }

        val lectura = Respaldo.lee(archivo, clave())

        assertTrue("$lectura", lectura is Respaldo.Lectura.Ok)
        val titulos = (lectura as Respaldo.Lectura.Ok).entradas.map { it.credencial.titulo }.toSet()
        assertEquals(setOf("Banco", "Correo"), titulos)
    }

    @Test
    fun `la cabecera sigue autenticada con el org json de Android`() {
        val original = String(Respaldo.escribe(entradas, clave()), Charsets.UTF_8)
        val manipulado = original.replace("\"iteraciones\": ${Kdf.ITERACIONES}", "\"iteraciones\": 1")
        assertFalse(original == manipulado)

        val lectura = Respaldo.lee(manipulado.toByteArray(Charsets.UTF_8), clave())
        assertTrue(lectura is Respaldo.Lectura.ContrasenaIncorrecta)
    }
}
