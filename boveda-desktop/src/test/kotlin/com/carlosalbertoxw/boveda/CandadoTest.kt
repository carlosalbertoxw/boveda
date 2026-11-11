package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.seguridad.Apertura
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import com.carlosalbertoxw.boveda.seguridad.Kdf
import com.carlosalbertoxw.boveda.seguridad.Reloj
import com.carlosalbertoxw.boveda.seguridad.Sobre
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El freno a la fuerza bruta y el cambio de maestra.
 *
 * Esta es la parte que no se ve: si se rompe, la app sigue pareciendo la misma
 * y sin embargo deja de proteger —o, peor, deja fuera al dueno—. El fallo que
 * motivo estas pruebas en Android era justo de los segundos: la espera se
 * guardaba con un reloj que se reinicia al apagar el equipo, asi que reiniciar
 * convertia un castigo de quince segundos en dias de bloqueo.
 */
class CandadoTest {

    /** Un reloj que se mueve cuando la prueba lo dice. */
    private class RelojFalso(
        var monotonico: Long = 1_000_000L,
        var pared: Long = 1_700_000_000_000L
    ) : Reloj {
        override fun monotonico(): Long = monotonico
        override fun pared(): Long = pared

        fun avanza(milisegundos: Long) {
            monotonico += milisegundos
            pared += milisegundos
        }

        /** Apagar y encender: el monotonico vuelve casi a cero, el de pared sigue. */
        fun reinicia(apagadoMilisegundos: Long = 60_000L) {
            monotonico = 3_000L
            pared += apagadoMilisegundos
        }
    }

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var reloj: RelojFalso
    private lateinit var gestor: GestorBoveda

    private val maestra = "correcto caballo bateria"

    // Pocas iteraciones: aqui se prueba la logica del candado, no el coste de
    // PBKDF2, y con 600 000 cada intento fallido tardaria lo suyo.
    private val iteraciones = 1_000

    @Before
    fun preparaBoveda() {
        reloj = RelojFalso()
        gestor = GestorBoveda(carpeta.root.toPath(), reloj)

        // Se monta la envoltura a mano en vez de llamar a crea(), que ademas
        // usaria las iteraciones de verdad. Es la misma estructura: una frase
        // aleatoria envuelta con la llave derivada de la maestra.
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val kek = Kdf.deriva(maestra.toCharArray(), sal, iteraciones)
        gestor.prefs.guardaEnvoltura(sal, iteraciones, Sobre.cierra(kek, Sobre.aleatorio(32)))
    }

    private suspend fun fallaUnaVez(): Apertura = gestor.abreConMaestra("me equivoco".toCharArray())

    // --- Verificar y cambiar la maestra ----------------------------------

    @Test
    fun `verificar acepta la maestra correcta y rechaza cualquier otra`() = runTest {
        assertTrue(gestor.verificaMaestra(maestra.toCharArray()))
        assertFalse(gestor.verificaMaestra("otra cosa".toCharArray()))
    }

    @Test
    fun `cambiar la maestra deja valida la nueva e invalida la vieja`() = runTest {
        assertTrue(gestor.cambiaMaestra(maestra.toCharArray(), "la nueva de verdad".toCharArray()))

        assertTrue(gestor.verificaMaestra("la nueva de verdad".toCharArray()))
        assertFalse(gestor.verificaMaestra(maestra.toCharArray()))
    }

    @Test
    fun `cambiar la maestra con la actual equivocada no toca nada`() = runTest {
        assertFalse(gestor.cambiaMaestra("no es esta".toCharArray(), "intento".toCharArray()))

        assertTrue(gestor.verificaMaestra(maestra.toCharArray()))
        assertFalse(gestor.verificaMaestra("intento".toCharArray()))
    }

    @Test
    fun `al cambiar la maestra suben las iteraciones a las de hoy`() = runTest {
        gestor.cambiaMaestra(maestra.toCharArray(), "la nueva de verdad".toCharArray())

        assertEquals(Kdf.ITERACIONES, gestor.prefs.iteraciones)
    }

    // --- Freno a la fuerza bruta -----------------------------------------

    @Test
    fun `los tres primeros intentos no cuestan espera`() = runTest {
        repeat(3) {
            val resultado = fallaUnaVez()
            assertTrue(resultado is Apertura.Incorrecta)
            assertEquals(0, (resultado as Apertura.Incorrecta).esperaSegundos)
        }
        assertEquals(0, gestor.segundosDeEspera())
    }

    @Test
    fun `el cuarto intento ya cuesta quince segundos y crece desde ahi`() = runTest {
        repeat(3) { fallaUnaVez() }

        assertEquals(15, (fallaUnaVez() as Apertura.Incorrecta).esperaSegundos)
        reloj.avanza(15_000)
        assertEquals(30, (fallaUnaVez() as Apertura.Incorrecta).esperaSegundos)
        reloj.avanza(30_000)
        assertEquals(60, (fallaUnaVez() as Apertura.Incorrecta).esperaSegundos)
    }

    @Test
    fun `la espera nunca pasa del castigo maximo`() = runTest {
        repeat(12) {
            fallaUnaVez()
            reloj.avanza(GestorBoveda.CASTIGO_MAXIMO * 1000L)
        }

        repeat(3) { fallaUnaVez() }
        assertTrue(gestor.segundosDeEspera() <= GestorBoveda.CASTIGO_MAXIMO)
    }

    @Test
    fun `mientras corre la espera no se acepta ningun intento`() = runTest {
        repeat(4) { fallaUnaVez() }

        // Ni siquiera con la contrasena buena: si no, bastaria con acertar para
        // saltarse el castigo, y probar contrasenas es justo lo que se frena.
        assertTrue(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.EnEspera)
    }

    @Test
    fun `pasada la espera se vuelve a aceptar`() = runTest {
        repeat(4) { fallaUnaVez() }
        assertTrue(gestor.segundosDeEspera() > 0)

        reloj.avanza(16_000)

        assertEquals(0, gestor.segundosDeEspera())
    }

    @Test
    fun `la espera sobrevive a cerrar y reabrir la app`() = runTest {
        repeat(4) { fallaUnaVez() }

        // Un proceso nuevo lee lo mismo del disco: cerrar la app para reintentar
        // no borra el castigo.
        val otroProceso = GestorBoveda(carpeta.root.toPath(), reloj)

        assertTrue(otroProceso.segundosDeEspera() > 0)
        assertTrue(otroProceso.abreConMaestra(maestra.toCharArray()) is Apertura.EnEspera)
    }

    // --- Los dos relojes ---------------------------------------------------

    @Test
    fun `reiniciar el equipo no convierte la espera en dias`() = runTest {
        reloj.monotonico = 3L * 24 * 60 * 60 * 1000
        repeat(4) { fallaUnaVez() }

        reloj.reinicia(apagadoMilisegundos = 60_000)

        assertTrue(
            "la espera tras reiniciar fue de ${gestor.segundosDeEspera()} s",
            gestor.segundosDeEspera() <= GestorBoveda.CASTIGO_MAXIMO
        )
    }

    @Test
    fun `reiniciar a mitad de la espera la vuelve a empezar`() = runTest {
        reloj.monotonico = 3L * 24 * 60 * 60 * 1000
        repeat(4) { fallaUnaVez() }

        // Tras un reinicio no se puede saber cuanto tiempo paso de verdad: el
        // reloj de pared se puede haber adelantado a mano. El castigo vuelve a
        // empezar entero, medido con el monotonico nuevo.
        reloj.reinicia(apagadoMilisegundos = 60_000)

        assertEquals(15, gestor.segundosDeEspera())
        assertTrue(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.EnEspera)

        reloj.avanza(15_000)
        assertEquals(0, gestor.segundosDeEspera())
    }

    @Test
    fun `reiniciar y adelantar la fecha del equipo no saltea la espera`() = runTest {
        reloj.monotonico = 3L * 24 * 60 * 60 * 1000
        repeat(4) { fallaUnaVez() }

        reloj.reinicia(apagadoMilisegundos = 60_000)
        reloj.pared += 24L * 60 * 60 * 1000

        assertTrue(gestor.segundosDeEspera() > 0)
        assertTrue(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.EnEspera)
    }

    @Test
    fun `reiniciar y atrasar la fecha del equipo no saltea la espera`() = runTest {
        reloj.monotonico = 3L * 24 * 60 * 60 * 1000
        repeat(4) { fallaUnaVez() }

        reloj.reinicia(apagadoMilisegundos = 60_000)
        reloj.pared -= 30L * 24 * 60 * 60 * 1000

        assertTrue(gestor.segundosDeEspera() > 0)
        assertTrue(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.EnEspera)
    }

    @Test
    fun `una espera ya cumplida no revive al reiniciar`() = runTest {
        reloj.monotonico = 3L * 24 * 60 * 60 * 1000
        repeat(4) { fallaUnaVez() }

        reloj.avanza(16_000)
        // La app la ve vencida, como cuando la pantalla de desbloqueo cuenta.
        assertEquals(0, gestor.segundosDeEspera())

        reloj.reinicia(apagadoMilisegundos = 60_000)
        assertEquals(0, gestor.segundosDeEspera())
    }

    @Test
    fun `adelantar la fecha del equipo no saltea la espera`() = runTest {
        repeat(4) { fallaUnaVez() }

        reloj.pared += 10L * 60 * 1000

        assertTrue(gestor.segundosDeEspera() > 0)
        assertTrue(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.EnEspera)
    }

    @Test
    fun `atrasar la fecha del equipo tampoco alarga la espera sin limite`() = runTest {
        repeat(4) { fallaUnaVez() }

        reloj.pared -= 30L * 24 * 60 * 60 * 1000

        assertTrue(gestor.segundosDeEspera() <= GestorBoveda.CASTIGO_MAXIMO)
    }

    // --- Borrado tras demasiados intentos --------------------------------

    @Test
    fun `sin limite configurado la boveda no se borra nunca`() = runTest {
        gestor.prefs.borrarTrasFallos = 0

        repeat(15) {
            fallaUnaVez()
            reloj.avanza(GestorBoveda.CASTIGO_MAXIMO * 1000L)
        }

        assertTrue(gestor.prefs.estaConfigurada)
    }

    @Test
    fun `al llegar al limite de intentos la boveda se borra`() = runTest {
        gestor.prefs.borrarTrasFallos = 5

        var ultimo: Apertura? = null
        repeat(5) {
            ultimo = fallaUnaVez()
            reloj.avanza(GestorBoveda.CASTIGO_MAXIMO * 1000L)
        }

        assertTrue(ultimo is Apertura.Borrada)
        assertFalse(gestor.prefs.estaConfigurada)
    }

    @Test
    fun `acertar pone el contador de fallos a cero`() = runTest {
        repeat(2) { fallaUnaVez() }
        assertEquals(2, gestor.prefs.fallosSeguidos)

        gestor.verificaMaestra(maestra.toCharArray())
        // verificaMaestra no toca el contador: comprueba, no abre.
        assertEquals(2, gestor.prefs.fallosSeguidos)

        repeat(2) { fallaUnaVez() }
        reloj.avanza(GestorBoveda.CASTIGO_MAXIMO * 1000L)
        assertEquals(Apertura.Ok, gestor.abreConMaestra(maestra.toCharArray()))

        assertEquals(0, gestor.prefs.fallosSeguidos)
        assertEquals(0, gestor.segundosDeEspera())
        gestor.bloquea()
    }

    @Test
    fun `abrir con iteraciones viejas las sube sin cambiar la maestra`() = runTest {
        reloj.avanza(1)
        assertEquals(Apertura.Ok, gestor.abreConMaestra(maestra.toCharArray()))
        gestor.bloquea()

        // La boveda se creo con 1000; al abrirla con la maestra se reenvolvio
        // con las de hoy, y la misma maestra sigue sirviendo.
        assertEquals(Kdf.ITERACIONES, gestor.prefs.iteraciones)
        assertTrue(gestor.verificaMaestra(maestra.toCharArray()))
    }
}
