package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.Respaldo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El respaldo es el unico archivo que sale del telefono, asi que sus
 * propiedades se prueban una por una: que vuelva completo, que no se abra sin
 * la contrasena y que no se pueda debilitar editando la cabecera.
 *
 * Va con Robolectric porque el sobre se arma con org.json, que en un test de
 * JVM puro esta vacio de contenido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RespaldoTest {

    private val muestra = listOf(
        Credencial(
            id = 7,
            titulo = "Banco",
            usuario = "carlos@ejemplo.mx",
            contrasena = "N0-la-adivinas #2026",
            sitio = "banco.mx",
            notas = "Cuenta de nómina",
            creadoEn = 1_700_000_000_000,
            actualizadoEn = 1_700_000_500_000
        ),
        Credencial(
            id = 9,
            titulo = "Correo",
            usuario = "carlos",
            contrasena = "otra distinta",
            sitio = "",
            notas = "",
            creadoEn = 1_700_000_100_000,
            actualizadoEn = 1_700_000_100_000
        )
    )

    private fun clave() = "respaldo seguro 2026".toCharArray()

    @Test
    fun `lo exportado vuelve completo y sin perder acentos`() {
        val archivo = Respaldo.escribe(muestra, clave())
        val leido = Respaldo.lee(archivo, clave())

        assertTrue(leido is Respaldo.Lectura.Ok)
        val entradas = (leido as Respaldo.Lectura.Ok).credenciales
        assertEquals(2, entradas.size)
        assertEquals("Banco", entradas[0].titulo)
        assertEquals("N0-la-adivinas #2026", entradas[0].contrasena)
        assertEquals("Cuenta de nómina", entradas[0].notas)
        assertEquals(1_700_000_000_000, entradas[0].creadoEn)
    }

    @Test
    fun `los ids no viajan en el archivo`() {
        val leido = Respaldo.lee(Respaldo.escribe(muestra, clave()), clave())
        val entradas = (leido as Respaldo.Lectura.Ok).credenciales

        // Al importar se insertan como filas nuevas; arrastrar ids ajenos solo
        // provocaria choques con lo que ya hay en la boveda.
        assertTrue(entradas.all { it.id == 0L })
    }

    @Test
    fun `sin la contrasena correcta no se abre`() {
        val archivo = Respaldo.escribe(muestra, clave())
        val leido = Respaldo.lee(archivo, "otra cosa".toCharArray())

        assertTrue(leido is Respaldo.Lectura.ContrasenaIncorrecta)
    }

    @Test
    fun `ninguna contrasena aparece en claro dentro del archivo`() {
        val texto = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)

        assertFalse(texto.contains("N0-la-adivinas"))
        assertFalse(texto.contains("carlos@ejemplo.mx"))
        assertFalse(texto.contains("Banco"))
        // La cabecera si va legible, a proposito.
        assertTrue(texto.contains(Respaldo.FORMATO))
    }

    @Test
    fun `bajar las iteraciones de la cabecera invalida el archivo`() {
        val original = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)
        val manipulado = original.replace("\"iteraciones\": 210000", "\"iteraciones\": 1")

        // Comprueba que la sustitucion de verdad ocurrio, no que el test pase
        // por no haber cambiado nada.
        assertFalse(original == manipulado)

        val leido = Respaldo.lee(manipulado.toByteArray(Charsets.UTF_8), clave())

        // La cabecera va autenticada como AAD: sin esto, un atacante abarataria
        // su ataque por diccionario a coste cero.
        assertTrue(leido is Respaldo.Lectura.ContrasenaIncorrecta)
    }

    @Test
    fun `alterar el cuerpo cifrado invalida el archivo`() {
        val archivo = Respaldo.escribe(muestra, clave())
        // Se voltea un byte a media zona de datos.
        archivo[archivo.size / 2] = (archivo[archivo.size / 2].toInt() xor 0x01).toByte()

        val leido = Respaldo.lee(archivo, clave())
        assertTrue(leido !is Respaldo.Lectura.Ok)
    }

    @Test
    fun `un archivo ajeno se rechaza sin confundirlo con contrasena mala`() {
        val leido = Respaldo.lee("""{"hola":"mundo"}""".toByteArray(), clave())
        assertTrue(leido is Respaldo.Lectura.NoEsUnRespaldo)
    }

    @Test
    fun `algo que ni siquiera es json se rechaza`() {
        val leido = Respaldo.lee("no soy un archivo".toByteArray(), clave())
        assertTrue(leido is Respaldo.Lectura.NoEsUnRespaldo)
    }

    @Test
    fun `dos exportaciones iguales dan archivos distintos`() {
        val uno = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)
        val otro = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)

        // Sal e IV nuevos cada vez: dos respaldos del mismo contenido no deben
        // poder compararse para deducir que no cambio nada entre ellos.
        assertFalse(uno == otro)
    }

    @Test
    fun `una boveda vacia se exporta e importa sin romperse`() {
        val leido = Respaldo.lee(Respaldo.escribe(emptyList(), clave()), clave())
        assertEquals(0, (leido as Respaldo.Lectura.Ok).credenciales.size)
    }
}
