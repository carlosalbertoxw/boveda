package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.seguridad.Generador
import com.carlosalbertoxw.boveda.seguridad.Kdf
import com.carlosalbertoxw.boveda.seguridad.Sobre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * Estas pruebas no comprueban que el codigo "corra": comprueban las propiedades
 * de las que depende la seguridad de la boveda. Si alguna se rompe, la app
 * seguiria funcionando de cara al usuario y sin embargo ya no protegeria nada.
 */
class CriptografiaTest {

    // --- Sobre ------------------------------------------------------------

    @Test
    fun `lo cifrado se recupera igual`() {
        val llave = Sobre.aleatorio(32)
        val claro = "contrasena del banco".toByteArray()

        val recuperado = Sobre.abre(llave, Sobre.cierra(llave, claro))

        assertEquals("contrasena del banco", String(recuperado))
    }

    @Test(expected = AEADBadTagException::class)
    fun `con la llave equivocada no descifra, no devuelve basura`() {
        val sobre = Sobre.cierra(Sobre.aleatorio(32), "secreto".toByteArray())
        Sobre.abre(Sobre.aleatorio(32), sobre)
    }

    @Test(expected = AEADBadTagException::class)
    fun `alterar un solo byte del sobre lo invalida`() {
        val llave = Sobre.aleatorio(32)
        val sobre = Sobre.cierra(llave, "secreto".toByteArray())

        // Simula a alguien editando el archivo de preferencias a mano.
        sobre[sobre.size - 3] = (sobre[sobre.size - 3] + 1).toByte()

        Sobre.abre(llave, sobre)
    }

    @Test
    fun `dos cifrados del mismo texto no se parecen`() {
        val llave = Sobre.aleatorio(32)
        val uno = Sobre.cierra(llave, "igual".toByteArray())
        val otro = Sobre.cierra(llave, "igual".toByteArray())

        // Si el IV se repitiera, GCM filtraria informacion entre los dos.
        assertNotEquals(uno.toList(), otro.toList())
    }

    @Test
    fun `borrar deja el arreglo en ceros`() {
        val secreto = Sobre.aleatorio(32)
        Sobre.borra(secreto)
        assertTrue(secreto.all { it == 0.toByte() })
    }

    // --- Derivacion de llave ---------------------------------------------

    @Test
    fun `la misma contrasena y sal dan siempre la misma llave`() {
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val a = Kdf.deriva("Correcto caballo".toCharArray(), sal, 1_000)
        val b = Kdf.deriva("Correcto caballo".toCharArray(), sal, 1_000)

        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun `la misma contrasena con otra sal da otra llave`() {
        val a = Kdf.deriva("Correcto caballo".toCharArray(), Sobre.aleatorio(16), 1_000)
        val b = Kdf.deriva("Correcto caballo".toCharArray(), Sobre.aleatorio(16), 1_000)

        // Es lo que impide precalcular tablas que sirvan para varias boveda.
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `los acentos y emojis cuentan de verdad`() {
        val sal = Sobre.aleatorio(16)
        val conAcento = Kdf.deriva("cafétería".toCharArray(), sal, 1_000)
        val sinAcento = Kdf.deriva("cafeteria".toCharArray(), sal, 1_000)
        val conEmoji = Kdf.deriva("clave🔐".toCharArray(), sal, 1_000)
        val sinEmoji = Kdf.deriva("clave".toCharArray(), sal, 1_000)

        // Si PBEKeySpec se quedara con el byte bajo de cada char, estos pares
        // colisionarian y la contrasena seria mas debil de lo que aparenta.
        assertNotEquals(conAcento.toList(), sinAcento.toList())
        assertNotEquals(conEmoji.toList(), sinEmoji.toList())
    }

    @Test
    fun `la llave derivada mide 256 bits`() {
        assertEquals(32, Kdf.deriva("x".toCharArray(), Sobre.aleatorio(16), 1_000).size)
    }

    @Test
    fun `una contrasena larga vale mas que una corta con simbolos`() {
        assertTrue(Kdf.fuerza("correcto caballo bateria grapa") > Kdf.fuerza("P4s#w0!"))
    }

    // --- Ciclo completo de la boveda -------------------------------------

    @Test
    fun `solo la contrasena correcta desenvuelve la frase de la base`() {
        val frase = Sobre.aleatorio(32).joinToString("") { "%02x".format(it) }
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)

        val kek = Kdf.deriva("mi maestra".toCharArray(), sal, 1_000)
        val guardado = Sobre.cierra(kek, frase.toByteArray())

        // Camino bueno.
        val buena = Kdf.deriva("mi maestra".toCharArray(), sal, 1_000)
        assertEquals(frase, String(Sobre.abre(buena, guardado)))

        // Camino malo: una letra de diferencia.
        val mala = Kdf.deriva("mi maestrA".toCharArray(), sal, 1_000)
        val fallo = runCatching { Sobre.abre(mala, guardado) }
        assertTrue(fallo.isFailure)
    }

    // --- Generador --------------------------------------------------------

    @Test
    fun `respeta el largo pedido`() {
        assertEquals(32, Generador.genera(Generador.Ajustes(largo = 32)).length)
    }

    @Test
    fun `incluye al menos uno de cada grupo activado`() {
        repeat(50) {
            val clave = Generador.genera(Generador.Ajustes(largo = 12))
            assertTrue("sin minuscula: $clave", clave.any { it.isLowerCase() })
            assertTrue("sin mayuscula: $clave", clave.any { it.isUpperCase() })
            assertTrue("sin digito: $clave", clave.any { it.isDigit() })
            assertTrue("sin simbolo: $clave", clave.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `no repite lo generado`() {
        val ajustes = Generador.Ajustes(largo = 20)
        val generadas = (1..200).map { Generador.genera(ajustes) }.toSet()
        assertEquals(200, generadas.size)
    }

    @Test
    fun `sin simbolos no mete simbolos`() {
        repeat(50) {
            val clave = Generador.genera(Generador.Ajustes(largo = 16, simbolos = false))
            assertTrue("colo un simbolo: $clave", clave.all { it.isLetterOrDigit() })
        }
    }
}
