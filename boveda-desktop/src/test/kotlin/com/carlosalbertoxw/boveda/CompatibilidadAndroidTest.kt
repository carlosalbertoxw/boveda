package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Respaldo
import com.carlosalbertoxw.boveda.seguridad.Kdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que hace que un respaldo del telefono se abra en la computadora, y al reves.
 *
 * Las otras pruebas del respaldo escriben y leen con el mismo codigo, asi que
 * pasarian igual aunque el formato se hubiera desviado del de Android en las
 * dos direcciones a la vez. Estas no: comparan contra valores calculados fuera
 * de la JVM, con Node.js, a partir de la especificacion del formato
 * (`herramientas/respaldo-referencia.mjs`).
 */
class CompatibilidadAndroidTest {

    @Test
    fun `la derivacion coincide byte a byte con una implementacion independiente`() {
        // UTF-8 aplicado dos veces sobre "clave🔐ñ", PBKDF2-HMAC-SHA256, sal de
        // dieciseis unos, 1000 iteraciones. Calculado con crypto.pbkdf2Sync.
        // Con emoji y enie a proposito: con solo ASCII, cualquier codificacion
        // daria lo mismo y la prueba no probaria nada.
        val esperada = "3aa1cc4211b2592dbae18c917cf6f19dc244799da360e056c085087781bc5e3f"

        val llave = Kdf.deriva("clave🔐ñ".toCharArray(), ByteArray(16) { 1 }, 1_000)

        assertEquals(esperada, llave.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `un respaldo escrito fuera de esta app se lee completo`() {
        val archivo = javaClass.getResourceAsStream("/respaldo-referencia-v2.json")!!.use { it.readBytes() }

        val lectura = Respaldo.lee(archivo, "contraseña del respaldo 🔐".toCharArray())

        assertTrue("no se pudo leer el respaldo de referencia: $lectura", lectura is Respaldo.Lectura.Ok)
        val entradas = (lectura as Respaldo.Lectura.Ok).entradas.associateBy { it.credencial.titulo }
        assertEquals(setOf("Banco", "Correo"), entradas.keys)

        val banco = entradas.getValue("Banco")
        assertEquals("N0-la-adivinas #2026", banco.credencial.contrasena)
        assertEquals("Cuenta de nómina", banco.credencial.notas)
        assertNull(banco.credencial.eliminadoEn)
        assertEquals(listOf("N0-la-adivinas #2026", "la vieja del año pasado"), banco.historial.map { it.contrasena })
        assertEquals("N0-la-adivinas #2026", banco.historial.single { it.actual }.contrasena)

        assertEquals(1_700_090_000_000, entradas.getValue("Correo").credencial.eliminadoEn)
    }

    @Test
    fun `el respaldo de referencia rechaza la contrasena equivocada`() {
        val archivo = javaClass.getResourceAsStream("/respaldo-referencia-v2.json")!!.use { it.readBytes() }

        // Misma contrasena sin el emoji: si la derivacion ignorara los bytes
        // fuera de ASCII, esta abriria.
        val lectura = Respaldo.lee(archivo, "contraseña del respaldo ".toCharArray())

        assertTrue(lectura is Respaldo.Lectura.ContrasenaIncorrecta)
    }
}
