package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.seguridad.Kdf
import com.carlosalbertoxw.boveda.seguridad.PreferenciasBoveda
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * Lo unico que la app escribe fuera de la base cifrada.
 *
 * No hay secretos aqui, pero si las tres piezas sin las cuales la boveda no se
 * puede abrir nunca mas. Lo que se comprueba es sobre todo que esas tres viajen
 * siempre juntas, y que lo escrito sobreviva a cerrar la app.
 */
class PreferenciasBovedaTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var prefs: PreferenciasBoveda

    @Before
    fun limpia() {
        prefs = PreferenciasBoveda(carpeta.root.toPath())
    }

    private fun releidas() = PreferenciasBoveda(carpeta.root.toPath())

    @Test
    fun `una boveda recien instalada no esta configurada`() {
        assertFalse(prefs.estaConfigurada)
        assertNull(prefs.sal)
        assertNull(prefs.sobreMaestra)
    }

    @Test
    fun `guardar la envoltura deja las tres piezas puestas a la vez`() {
        val sal = ByteArray(16) { it.toByte() }
        val sobre = ByteArray(48) { (it * 3).toByte() }

        prefs.guardaEnvoltura(sal, 12_345, sobre)

        val leidas = releidas()
        assertTrue(leidas.estaConfigurada)
        assertArrayEquals(sal, leidas.sal)
        assertArrayEquals(sobre, leidas.sobreMaestra)
        assertEquals(12_345, leidas.iteraciones)
    }

    @Test
    fun `guardar la envoltura otra vez reemplaza las tres, no mezcla`() {
        prefs.guardaEnvoltura(ByteArray(16) { 1 }, 1_000, ByteArray(48) { 1 })
        prefs.guardaEnvoltura(ByteArray(16) { 2 }, 2_000, ByteArray(48) { 2 })

        val leidas = releidas()
        assertArrayEquals(ByteArray(16) { 2 }, leidas.sal)
        assertArrayEquals(ByteArray(48) { 2 }, leidas.sobreMaestra)
        assertEquals(2_000, leidas.iteraciones)
    }

    @Test
    fun `sin nada guardado las iteraciones son las de hoy`() {
        assertEquals(Kdf.ITERACIONES, prefs.iteraciones)
    }

    @Test
    fun `los ajustes traen valores por omision razonables`() {
        assertEquals(1, prefs.minutosAutobloqueo)
        assertEquals(30, prefs.segundosPortapapeles)
        // Borrar la boveda tras N fallos viene apagado: es destructivo y sin
        // vuelta atras, asi que tiene que ser una decision explicita.
        assertEquals(0, prefs.borrarTrasFallos)
    }

    @Test
    fun `los ajustes se recuerdan`() {
        prefs.minutosAutobloqueo = 15
        prefs.segundosPortapapeles = 0
        prefs.borrarTrasFallos = 10

        val leidas = releidas()
        assertEquals(15, leidas.minutosAutobloqueo)
        assertEquals(0, leidas.segundosPortapapeles)
        assertEquals(10, leidas.borrarTrasFallos)
    }

    @Test
    fun `limpiar la espera borra los dos vencimientos`() {
        prefs.esperaMonotonicaHasta = 5_000L
        prefs.esperaParedHasta = 9_000L

        prefs.limpiaEspera()

        assertEquals(0L, prefs.esperaMonotonicaHasta)
        assertEquals(0L, prefs.esperaParedHasta)
    }

    @Test
    fun `borrar todo deja la boveda como recien instalada`() {
        prefs.guardaEnvoltura(ByteArray(16), 1_000, ByteArray(48))
        prefs.fallosSeguidos = 7
        prefs.minutosAutobloqueo = 15

        prefs.borraTodo()

        val leidas = releidas()
        assertFalse(leidas.estaConfigurada)
        assertEquals(0, leidas.fallosSeguidos)
        assertEquals(1, leidas.minutosAutobloqueo)
    }

    @Test
    fun `no quedan temporales a medio escribir`() {
        prefs.guardaEnvoltura(ByteArray(16), 1_000, ByteArray(48))
        prefs.minutosAutobloqueo = 5

        val archivos = Files.list(carpeta.root.toPath()).use { lista -> lista.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("boveda_config.properties"), archivos)
    }
}
