package com.carlosalbertoxw.boveda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.boveda.seguridad.Kdf
import com.carlosalbertoxw.boveda.seguridad.PreferenciasBoveda
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lo unico que la app escribe fuera de la base cifrada.
 *
 * No hay secretos aqui, pero si las tres piezas sin las cuales la boveda no se
 * puede abrir nunca mas. Lo que se comprueba es sobre todo que esas tres viajen
 * siempre juntas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferenciasBovedaTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: PreferenciasBoveda

    @Before
    fun limpia() {
        contexto.getSharedPreferences("boveda_config", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = PreferenciasBoveda(contexto)
    }

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

        assertTrue(prefs.estaConfigurada)
        assertArrayEquals(sal, prefs.sal)
        assertArrayEquals(sobre, prefs.sobreMaestra)
        assertEquals(12_345, prefs.iteraciones)
    }

    @Test
    fun `guardar la envoltura otra vez reemplaza las tres, no mezcla`() {
        prefs.guardaEnvoltura(ByteArray(16) { 1 }, 1_000, ByteArray(48) { 1 })
        prefs.guardaEnvoltura(ByteArray(16) { 2 }, 2_000, ByteArray(48) { 2 })

        // Si alguna quedara de la tanda anterior, la llave derivada ya no
        // abriria el sobre y la boveda seria irrecuperable.
        assertArrayEquals(ByteArray(16) { 2 }, prefs.sal)
        assertArrayEquals(ByteArray(48) { 2 }, prefs.sobreMaestra)
        assertEquals(2_000, prefs.iteraciones)
    }

    @Test
    fun `sin nada guardado las iteraciones son las de hoy`() {
        // Importa para las boveda viejas: si el valor faltara y se asumiera un
        // numero menor, la llave saldria distinta y no abriria.
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

        val releidas = PreferenciasBoveda(contexto)
        assertEquals(15, releidas.minutosAutobloqueo)
        assertEquals(0, releidas.segundosPortapapeles)
        assertEquals(10, releidas.borrarTrasFallos)
    }

    @Test
    fun `limpiar la espera borra los dos vencimientos`() {
        prefs.esperaMonotonicaHasta = 5_000L
        prefs.esperaParedHasta = 9_000L

        prefs.limpiaEspera()

        // Los dos, no uno: dejar uno puesto reviviria la espera al leerlos
        // cruzados.
        assertEquals(0L, prefs.esperaMonotonicaHasta)
        assertEquals(0L, prefs.esperaParedHasta)
    }

    @Test
    fun `borrar todo deja la boveda como recien instalada`() {
        prefs.guardaEnvoltura(ByteArray(16), 1_000, ByteArray(48))
        prefs.fallosSeguidos = 7
        prefs.minutosAutobloqueo = 15

        prefs.borraTodo()

        assertFalse(prefs.estaConfigurada)
        assertEquals(0, prefs.fallosSeguidos)
        assertEquals(1, prefs.minutosAutobloqueo)
    }
}
