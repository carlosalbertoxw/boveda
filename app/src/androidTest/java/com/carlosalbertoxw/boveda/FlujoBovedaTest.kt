package com.carlosalbertoxw.boveda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.boveda.datos.BovedaDatabase
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.seguridad.Apertura
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El ciclo completo, en un telefono de verdad.
 *
 * Las pruebas de la JVM no pueden abrir la base: SQLCipher es una libreria
 * nativa. Aqui si, y eso es justo lo que hace falta comprobar de vez en cuando,
 * porque toda la seguridad de la app descansa en que ese archivo salga cifrado
 * y solo vuelva a abrirse con la contrasena correcta.
 *
 * Se ejecutan con:
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FlujoBovedaTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private lateinit var gestor: GestorBoveda

    private val maestra = "correcto caballo bateria grapa"

    @Before
    fun arranca() {
        limpia()
        gestor = GestorBoveda(contexto)
    }

    @After
    fun termina() {
        runCatching { gestor.bloquea() }
        limpia()
    }

    private fun limpia() {
        contexto.getSharedPreferences("boveda_config", Context.MODE_PRIVATE)
            .edit().clear().commit()
        BovedaDatabase.borraArchivos(contexto)
    }

    private fun dao() = (gestor.estado.value as EstadoBoveda.Abierta).db.credencialDao()

    @Test
    fun crear_guardar_bloquear_y_volver_a_abrir() = runBlocking {
        assertTrue(gestor.estado.value is EstadoBoveda.SinConfigurar)

        assertEquals(Apertura.Ok, gestor.crea(maestra.toCharArray()))
        assertTrue(gestor.estado.value is EstadoBoveda.Abierta)

        dao().inserta(
            Credencial(titulo = "Banco", usuario = "carlos", contrasena = "no la adivinas")
        )

        gestor.bloquea()
        assertTrue(gestor.estado.value is EstadoBoveda.Cerrada)

        assertEquals(Apertura.Ok, gestor.abreConMaestra(maestra.toCharArray()))

        // Lo guardado antes de cerrar sigue ahi despues de volver a abrir: es
        // lo unico que el usuario le pide a esta app.
        val guardadas = dao().listaCompleta()
        assertEquals(1, guardadas.size)
        assertEquals("no la adivinas", guardadas.first().contrasena)
    }

    @Test
    fun con_la_contrasena_equivocada_no_abre() = runBlocking {
        gestor.crea(maestra.toCharArray())
        gestor.bloquea()

        val resultado = gestor.abreConMaestra("esta no es".toCharArray())

        assertTrue(resultado is Apertura.Incorrecta)
        assertTrue(gestor.estado.value is EstadoBoveda.Cerrada)
    }

    @Test
    fun cambiar_la_maestra_no_deja_atras_lo_guardado() = runBlocking {
        gestor.crea(maestra.toCharArray())
        dao().inserta(Credencial(titulo = "Correo", usuario = "yo", contrasena = "secreta"))

        assertTrue(gestor.cambiaMaestra(maestra.toCharArray(), "la nueva de verdad".toCharArray()))
        gestor.bloquea()

        // La base no se recifra al cambiar la maestra —solo la envoltura—, asi
        // que esto comprueba que la frase que sale de la envoltura nueva sigue
        // siendo la que abre el mismo archivo.
        assertFalse(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.Ok)
        assertEquals(Apertura.Ok, gestor.abreConMaestra("la nueva de verdad".toCharArray()))
        assertEquals("secreta", dao().listaCompleta().first().contrasena)
    }

    @Test
    fun el_archivo_de_la_base_no_contiene_nada_legible() = runBlocking {
        gestor.crea(maestra.toCharArray())
        dao().inserta(
            Credencial(titulo = "Banco", usuario = "carlos@ejemplo.mx", contrasena = "melon-42")
        )
        // Cerrar vacia el diario a la base antes de mirarla.
        gestor.bloquea()

        val crudo = contexto.getDatabasePath(BovedaDatabase.NOMBRE).readBytes()
            .toString(Charsets.ISO_8859_1)

        assertFalse("la contrasena aparece en claro", crudo.contains("melon-42"))
        assertFalse("el usuario aparece en claro", crudo.contains("carlos@ejemplo.mx"))
        assertFalse("el titulo aparece en claro", crudo.contains("Banco"))
        // Y ni siquiera se ve que sea una base de datos: SQLCipher tambien cifra
        // la cabecera que SQLite deja en claro.
        assertFalse("la cabecera de SQLite esta en claro", crudo.startsWith("SQLite format 3"))
    }
}
