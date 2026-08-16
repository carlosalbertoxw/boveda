package com.carlosalbertoxw.boveda.datos

import com.carlosalbertoxw.boveda.seguridad.Kdf
import com.carlosalbertoxw.boveda.seguridad.Sobre
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64

/**
 * El formato del archivo de respaldo.
 *
 * Es un sobre JSON con la cabecera en claro y las credenciales cifradas dentro:
 *
 * ```json
 * {
 *   "formato": "boveda-respaldo",
 *   "version": 1,
 *   "creado": "2026-08-15T18:00:00Z",
 *   "entradas": 12,
 *   "kdf": { "algoritmo": "PBKDF2WithHmacSHA256", "iteraciones": 210000, "sal": "..." },
 *   "cifrado": "AES-256-GCM",
 *   "datos": "base64( iv || texto cifrado || etiqueta )"
 * }
 * ```
 *
 * La cabecera va legible a proposito: dentro de tres anos, abriendo el archivo
 * con un editor de texto, se sabe que es y con que se descifra. No revela nada
 * util —ni titulos, ni cuantas letras tiene ninguna contrasena— salvo cuantas
 * entradas hay, que es el precio de que el archivo se explique solo.
 *
 * La cabecera ademas se autentica (va como AAD): si alguien la edita para bajar
 * las iteraciones y abaratarse un ataque por diccionario, el descifrado falla.
 */
object Respaldo {

    const val FORMATO = "boveda-respaldo"
    const val VERSION = 1
    const val EXTENSION = "json"
    const val TIPO_MIME = "application/json"

    sealed interface Lectura {
        data class Ok(val credenciales: List<Credencial>, val creado: String) : Lectura
        data object ContrasenaIncorrecta : Lectura
        data object NoEsUnRespaldo : Lectura
        data class VersionNoSoportada(val version: Int) : Lectura
        data class Corrupto(val detalle: String) : Lectura
    }

    // --- Escritura --------------------------------------------------------

    fun escribe(credenciales: List<Credencial>, contrasena: CharArray): ByteArray {
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val salB64 = base64(sal)
        val cabecera = cabeceraAutenticada(Kdf.ITERACIONES, salB64)

        // El respaldo lleva su propia sal y su propia llave, sin relacion con la
        // de la boveda: el archivo va a acabar fuera del telefono y debe
        // sostenerse solo.
        val llave = Kdf.deriva(contrasena, sal, Kdf.ITERACIONES)
        val cifrado = try {
            Sobre.cierra(llave, cuerpo(credenciales).toByteArray(Charsets.UTF_8), cabecera)
        } finally {
            Sobre.borra(llave)
        }

        val sobre = JSONObject().apply {
            put("formato", FORMATO)
            put("version", VERSION)
            put("creado", Instant.now().toString())
            put("entradas", credenciales.size)
            put(
                "kdf",
                JSONObject().apply {
                    put("algoritmo", "PBKDF2WithHmacSHA256")
                    put("iteraciones", Kdf.ITERACIONES)
                    put("sal", salB64)
                }
            )
            put("cifrado", "AES-256-GCM")
            put("datos", base64(cifrado))
        }
        return sobre.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun cuerpo(credenciales: List<Credencial>): String {
        val entradas = JSONArray()
        credenciales.forEach { credencial ->
            entradas.put(
                JSONObject().apply {
                    // El id no se exporta: al importar se insertan como filas
                    // nuevas, y arrastrar ids ajenos solo causaria choques.
                    put("titulo", credencial.titulo)
                    put("usuario", credencial.usuario)
                    put("contrasena", credencial.contrasena)
                    put("sitio", credencial.sitio)
                    put("notas", credencial.notas)
                    put("creadoEn", credencial.creadoEn)
                    put("actualizadoEn", credencial.actualizadoEn)
                }
            )
        }
        return JSONObject().put("credenciales", entradas).toString()
    }

    // --- Lectura ----------------------------------------------------------

    fun lee(archivo: ByteArray, contrasena: CharArray): Lectura {
        val sobre = try {
            JSONObject(String(archivo, Charsets.UTF_8))
        } catch (_: JSONException) {
            return Lectura.NoEsUnRespaldo
        }

        if (sobre.optString("formato") != FORMATO) return Lectura.NoEsUnRespaldo

        val version = sobre.optInt("version", -1)
        if (version != VERSION) return Lectura.VersionNoSoportada(version)

        val kdf = sobre.optJSONObject("kdf") ?: return Lectura.Corrupto("falta el bloque kdf")
        val salB64 = kdf.optString("sal").ifEmpty { return Lectura.Corrupto("falta la sal") }
        val iteraciones = kdf.optInt("iteraciones", 0)
        if (iteraciones <= 0) return Lectura.Corrupto("iteraciones invalidas")

        val sal = try {
            Base64.getDecoder().decode(salB64)
        } catch (_: IllegalArgumentException) {
            return Lectura.Corrupto("la sal no es base64")
        }
        val datos = try {
            Base64.getDecoder().decode(sobre.optString("datos"))
        } catch (_: IllegalArgumentException) {
            return Lectura.Corrupto("el cuerpo no es base64")
        }
        if (datos.size <= Sobre.BYTES_IV) return Lectura.Corrupto("el cuerpo esta truncado")

        val llave = Kdf.deriva(contrasena, sal, iteraciones)
        val claro = try {
            Sobre.abre(llave, datos, cabeceraAutenticada(iteraciones, salB64))
        } catch (_: GeneralSecurityException) {
            // No se distingue "contrasena mala" de "archivo manipulado": en GCM
            // las dos cosas fallan igual, y esta bien que asi sea.
            return Lectura.ContrasenaIncorrecta
        } finally {
            Sobre.borra(llave)
        }

        return try {
            Lectura.Ok(descomponeCuerpo(claro), sobre.optString("creado"))
        } catch (e: JSONException) {
            Lectura.Corrupto(e.message ?: "el contenido no se entiende")
        } finally {
            Sobre.borra(claro)
        }
    }

    private fun descomponeCuerpo(claro: ByteArray): List<Credencial> {
        val entradas = JSONObject(String(claro, Charsets.UTF_8)).getJSONArray("credenciales")
        val ahora = System.currentTimeMillis()
        return (0 until entradas.length()).map { i ->
            val entrada = entradas.getJSONObject(i)
            Credencial(
                id = 0,
                titulo = entrada.optString("titulo"),
                usuario = entrada.optString("usuario"),
                contrasena = entrada.optString("contrasena"),
                sitio = entrada.optString("sitio"),
                notas = entrada.optString("notas"),
                creadoEn = entrada.optLong("creadoEn", ahora),
                actualizadoEn = entrada.optLong("actualizadoEn", ahora)
            )
        }
    }

    // --- Comun ------------------------------------------------------------

    /**
     * Los campos de la cabecera que no pueden cambiar sin invalidar el archivo.
     * Se serializan a mano y en orden fijo: no vale usar el JSON tal cual,
     * porque el orden de las claves no esta garantizado y el AAD tiene que ser
     * byte a byte el mismo al escribir y al leer.
     */
    private fun cabeceraAutenticada(iteraciones: Int, salB64: String): ByteArray =
        "$FORMATO|$VERSION|PBKDF2WithHmacSHA256|$iteraciones|$salB64|AES-256-GCM"
            .toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun nombreSugerido(): String {
        val dia = Instant.now().toString().substringBefore('T')
        return "boveda-respaldo-$dia.$EXTENSION"
    }
}
