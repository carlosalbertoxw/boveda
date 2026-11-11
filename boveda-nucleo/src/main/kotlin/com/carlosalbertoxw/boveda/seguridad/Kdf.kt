package com.carlosalbertoxw.boveda.seguridad

import java.nio.CharBuffer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derivacion de llave a partir de la contrasena maestra.
 *
 * La contrasena maestra NO se guarda en ningun lado, ni siquiera como hash.
 * De ella se deriva una llave de 256 bits que solo sirve para desenvolver la
 * frase real de la base. Si el atacante se lleva el archivo de preferencias, lo
 * unico que puede hacer es adivinar contrasenas, y cada intento le cuesta una
 * pasada completa de PBKDF2.
 *
 * PBKDF2-HMAC-SHA256 es lo que hay disponible de fabrica desde API 26, y
 * [ITERACIONES] es la cifra que pide OWASP para esa variante (las 210 000 que
 * se usaban antes son las de PBKDF2-HMAC-SHA512, que cuesta mas por pasada).
 * Argon2id resistiria mejor a un atacante con GPU, pero exige meter una
 * libreria nativa de terceros; se dejo fuera para no ampliar la superficie de
 * confianza.
 *
 * El numero de iteraciones se guarda junto a la sal precisamente para poder
 * subirlo sin dejar inservibles las bovedas ya creadas: se ponen al dia solas
 * al desbloquear con la maestra. Los respaldos llevan el suyo en la cabecera,
 * asi que los archivos viejos se siguen abriendo.
 */
object Kdf {

    const val ITERACIONES = 600_000

    /**
     * Tope para lo que se acepta al leer un archivo que viene de fuera. La
     * derivacion corre antes de poder comprobar nada (el AAD solo se verifica
     * con la llave ya derivada), asi que un respaldo hecho a proposito con dos
     * mil millones de iteraciones dejaria la app calculando durante horas.
     * Diez millones es mas de diez veces la cifra de hoy: margen de sobra para
     * subirla en el futuro sin tocar este numero.
     */
    const val MAXIMO_ITERACIONES = 10_000_000

    const val BYTES_SAL = 16
    private const val BITS_LLAVE = 256
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"

    fun deriva(maestra: CharArray, sal: ByteArray, iteraciones: Int): ByteArray {
        // Algunas implementaciones de PBEKeySpec en Android se quedan solo con
        // el byte bajo de cada char, lo que aplastaria acentos y emojis y haria
        // que dos contrasenas distintas derivaran la misma llave. Se convierte a
        // UTF-8 a mano y se reexpande a chars de 8 bits: asi la derivacion
        // depende siempre de los bytes reales, sea cual sea el proveedor.
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(maestra))
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val expandida = CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() }

        val spec = PBEKeySpec(expandida, sal, iteraciones, BITS_LLAVE)
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            Sobre.borra(bytes)
            Sobre.borra(expandida)
        }
    }

    /**
     * Fuerza aproximada, para el medidor de la pantalla de alta. Es una
     * heuristica de entropia, no un veredicto: cuenta variedad de alfabetos y
     * longitud, que es lo que de verdad encarece un ataque por diccionario.
     */
    fun fuerza(maestra: String): Int {
        if (maestra.isEmpty()) return 0
        var alfabeto = 0
        if (maestra.any { it.isLowerCase() }) alfabeto += 26
        if (maestra.any { it.isUpperCase() }) alfabeto += 26
        if (maestra.any { it.isDigit() }) alfabeto += 10
        if (maestra.any { !it.isLetterOrDigit() }) alfabeto += 33
        val bits = maestra.length * (Math.log(alfabeto.toDouble()) / Math.log(2.0))
        return bits.toInt()
    }
}
