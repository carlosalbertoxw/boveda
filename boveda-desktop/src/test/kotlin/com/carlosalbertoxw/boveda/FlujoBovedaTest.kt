package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.BaseBoveda
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * El ciclo completo de la boveda contra el archivo cifrado de verdad.
 *
 * En Android esta suite era instrumentada porque SQLCipher es codigo nativo.
 * Aqui la base es JVM pura, asi que el ciclo entero corre con las pruebas
 * normales y con las 600 000 iteraciones reales.
 */
class FlujoBovedaTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var gestor: GestorBoveda
    private val maestra = "correcto caballo bateria grapa"

    @Before
    fun arranca() {
        gestor = GestorBoveda(carpeta.root.toPath())
    }

    @After
    fun termina() {
        runCatching { gestor.bloquea() }
    }

    private fun dao() = (gestor.estado.value as EstadoBoveda.Abierta).db.credencialDao()

    @Test
    fun crear_guardar_bloquear_y_volver_a_abrir() = runBlocking {
        assertTrue(gestor.estado.value is EstadoBoveda.SinConfigurar)

        assertEquals(Apertura.Ok, gestor.crea(maestra.toCharArray()))
        assertTrue(gestor.estado.value is EstadoBoveda.Abierta)

        dao().inserta(Credencial(titulo = "Banco", usuario = "carlos", contrasena = "no la adivinas"))

        gestor.bloquea()
        assertTrue(gestor.estado.value is EstadoBoveda.Cerrada)

        // Un proceso nuevo, como al volver a abrir la app al dia siguiente.
        gestor = GestorBoveda(carpeta.root.toPath())
        assertTrue(gestor.estado.value is EstadoBoveda.Cerrada)
        assertEquals(Apertura.Ok, gestor.abreConMaestra(maestra.toCharArray()))

        val guardadas = dao().listaCompleta()
        assertEquals(1, guardadas.size)
        assertEquals("no la adivinas", guardadas.first().contrasena)
    }

    @Test
    fun con_la_contrasena_equivocada_no_abre() = runBlocking {
        gestor.crea(maestra.toCharArray())
        gestor.bloquea()

        val resultado = gestor.abreConMaestra("otra cosa".toCharArray())

        assertTrue(resultado is Apertura.Incorrecta)
        assertTrue(gestor.estado.value is EstadoBoveda.Cerrada)
    }

    @Test
    fun cambiar_la_maestra_no_deja_atras_lo_guardado() = runBlocking {
        gestor.crea(maestra.toCharArray())
        dao().inserta(Credencial(titulo = "Correo", usuario = "yo", contrasena = "secreta"))

        assertTrue(gestor.cambiaMaestra(maestra.toCharArray(), "la nueva de verdad".toCharArray()))
        gestor.bloquea()

        assertFalse(gestor.abreConMaestra(maestra.toCharArray()) is Apertura.Ok)
        assertEquals(Apertura.Ok, gestor.abreConMaestra("la nueva de verdad".toCharArray()))
        assertEquals("secreta", dao().listaCompleta().first().contrasena)
    }

    @Test
    fun el_archivo_de_la_base_no_contiene_nada_legible() = runBlocking {
        gestor.crea(maestra.toCharArray())
        dao().inserta(
            Credencial(titulo = "Banco", usuario = "carlos@ejemplo.mx", contrasena = "melon-42", notas = "nómina")
        )
        gestor.bloquea()

        val carpetaRaiz = carpeta.root.toPath()
        val crudo = String(Files.readAllBytes(carpetaRaiz.resolve(BaseBoveda.NOMBRE)), Charsets.ISO_8859_1)
        val config = Files.readString(carpetaRaiz.resolve("boveda_config.properties"))

        assertFalse("la contrasena aparece en claro", crudo.contains("melon-42"))
        assertFalse("el usuario aparece en claro", crudo.contains("carlos@ejemplo.mx"))
        assertFalse("el titulo aparece en claro", crudo.contains("Banco"))
        assertFalse("la estructura aparece en claro", crudo.contains("credenciales"))
        // Y fuera de la base tampoco: ni la maestra ni nada de lo guardado.
        assertFalse(config.contains(maestra))
        assertFalse(config.contains("melon-42"))
    }

    @Test
    fun alterar_un_byte_de_la_base_impide_abrirla_sin_perder_nada() = runBlocking {
        gestor.crea(maestra.toCharArray())
        dao().inserta(Credencial(titulo = "Banco", usuario = "carlos", contrasena = "melon-42"))
        gestor.bloquea()

        val archivo = carpeta.root.toPath().resolve(BaseBoveda.NOMBRE)
        val original = Files.readAllBytes(archivo)
        val alterado = original.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        Files.write(archivo, alterado)

        // No abre con datos a medias ni "arregla" nada borrando: dice que no puede.
        val resultado = gestor.abreConMaestra(maestra.toCharArray())
        assertTrue(resultado is Apertura.Falla)
        assertTrue(gestor.estado.value is EstadoBoveda.Cerrada)
        // El archivo sigue ahi tal cual, por si se puede recuperar de otra forma.
        assertTrue(alterado.contentEquals(Files.readAllBytes(archivo)))
    }

    @Test
    fun restablecer_borra_la_base_y_la_configuracion() = runBlocking {
        gestor.crea(maestra.toCharArray())
        dao().inserta(Credencial(titulo = "Banco", usuario = "carlos", contrasena = "melon-42"))

        gestor.restablece()

        assertTrue(gestor.estado.value is EstadoBoveda.SinConfigurar)
        assertFalse(Files.exists(carpeta.root.toPath().resolve(BaseBoveda.NOMBRE)))
        assertFalse(GestorBoveda(carpeta.root.toPath()).prefs.estaConfigurada)
    }

    @Test
    fun con_la_boveda_cerrada_la_base_no_responde() = runBlocking {
        gestor.crea(maestra.toCharArray())
        val base = (gestor.estado.value as EstadoBoveda.Abierta).db
        gestor.bloquea()

        // Nadie que se haya quedado con la referencia puede seguir leyendo.
        assertFalse(base.abierta)
        assertTrue(runCatching { base.credencialDao().listaCompleta() }.isFailure)
        assertTrue(base.contenido.value.credenciales.isEmpty())
    }
}
