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
 *   "version": 2,
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
 * El AAD incluye la version, asi que tampoco se puede hacer pasar un archivo
 * por uno de otra version para que se lea con reglas distintas.
 *
 * ## Version 2
 *
 * La 2 agrega lo que la boveda aprendio a guardar despues: la fecha de borrado
 * de lo que esta en la papelera y el historial de contrasenas de cada entrada.
 * Un respaldo tiene que poder devolver la boveda tal y como estaba, y "estaba"
 * incluye lo que se borro anteayer y todavia se puede recuperar.
 *
 * Los archivos de la version 1 se siguen leyendo: son los que ya existen en los
 * telefonos y computadoras de la gente, y un respaldo que deja de abrirse por una actualizacion
 * de la app es exactamente el fallo que un respaldo no puede tener. Al leerlos,
 * cada entrada estrena historial con la unica contrasena que trae el archivo.
 */
object Respaldo {

    const val FORMATO = "boveda-respaldo"
    const val VERSION = 2
    const val EXTENSION = "json"
    const val TIPO_MIME = "application/json"

    /** Las versiones que esta app sabe leer, no solo la que escribe. */
    private val VERSIONES_LEIBLES = setOf(1, 2)

    /**
     * Una credencial con su historial detras, que es la unidad que viaja en el
     * archivo. Van juntos y no en dos listas paralelas porque el historial no
     * significa nada sin su credencial, y separarlos obligaria a inventar en el
     * archivo unos ids que precisamente no se exportan.
     */
    data class Entrada(
        val credencial: Credencial,
        val historial: List<HistorialContrasena> = emptyList()
    )

    sealed interface Lectura {
        data class Ok(val entradas: List<Entrada>, val creado: String) : Lectura
        data object ContrasenaIncorrecta : Lectura
        data object NoEsUnRespaldo : Lectura
        data class VersionNoSoportada(val version: Int) : Lectura
        data class Corrupto(val detalle: String) : Lectura
    }

    // --- Escritura --------------------------------------------------------

    fun escribe(entradas: List<Entrada>, contrasena: CharArray): ByteArray {
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val salB64 = base64(sal)
        val cabecera = cabeceraAutenticada(VERSION, Kdf.ITERACIONES, salB64)

        // El respaldo lleva su propia sal y su propia llave, sin relacion con la
        // de la boveda: el archivo va a acabar fuera del equipo y debe
        // sostenerse solo.
        val llave = Kdf.deriva(contrasena, sal, Kdf.ITERACIONES)
        val cifrado = try {
            Sobre.cierra(llave, cuerpo(entradas).toByteArray(Charsets.UTF_8), cabecera)
        } finally {
            Sobre.borra(llave)
        }

        val sobre = JSONObject().apply {
            put("formato", FORMATO)
            put("version", VERSION)
            put("creado", Instant.now().toString())
            put("entradas", entradas.size)
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

    private fun cuerpo(entradas: List<Entrada>): String {
        val lista = JSONArray()
        entradas.forEach { lista.put(entradaJson(it)) }
        return JSONObject().put("credenciales", lista).toString()
    }

    private fun entradaJson(entrada: Entrada): JSONObject {
        val credencial = entrada.credencial
        return JSONObject().apply {
            // El id no se exporta: al importar se insertan como filas nuevas, y
            // arrastrar ids ajenos solo causaria choques.
            put("titulo", credencial.titulo)
            put("usuario", credencial.usuario)
            put("contrasena", credencial.contrasena)
            put("sitio", credencial.sitio)
            put("notas", credencial.notas)
            put("creadoEn", credencial.creadoEn)
            put("actualizadoEn", credencial.actualizadoEn)
            // Solo va si esta en la papelera. Una clave ausente se lee igual que
            // null y ahorra repetirla en todas las entradas activas, que son la
            // inmensa mayoria.
            credencial.eliminadoEn?.let { put("eliminadoEn", it) }
            put("historial", historialJson(entrada.historial))
        }
    }

    private fun historialJson(historial: List<HistorialContrasena>): JSONArray {
        val lista = JSONArray()
        historial.forEach { anotacion ->
            lista.put(
                JSONObject().apply {
                    put("contrasena", anotacion.contrasena)
                    put("creadoEn", anotacion.creadoEn)
                    put("actual", anotacion.actual)
                }
            )
        }
        return lista
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
        if (version !in VERSIONES_LEIBLES) return Lectura.VersionNoSoportada(version)

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
            Sobre.abre(llave, datos, cabeceraAutenticada(version, iteraciones, salB64))
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

    private fun descomponeCuerpo(claro: ByteArray): List<Entrada> {
        val lista = JSONObject(String(claro, Charsets.UTF_8)).getJSONArray("credenciales")
        val ahora = System.currentTimeMillis()
        return (0 until lista.length()).map { i -> entradaDe(lista.getJSONObject(i), ahora) }
    }

    private fun entradaDe(json: JSONObject, ahora: Long): Entrada {
        val credencial = Credencial(
            id = 0,
            titulo = json.optString("titulo"),
            usuario = json.optString("usuario"),
            contrasena = json.optString("contrasena"),
            sitio = json.optString("sitio"),
            notas = json.optString("notas"),
            creadoEn = json.optLong("creadoEn", ahora),
            actualizadoEn = json.optLong("actualizadoEn", ahora),
            // Ausente en los archivos de la version 1, y ausente tambien en la 2
            // para todo lo que no esta en la papelera.
            eliminadoEn = if (json.has("eliminadoEn")) json.optLong("eliminadoEn") else null
        )
        return normaliza(credencial, historialDe(json.optJSONArray("historial")))
    }

    private fun historialDe(lista: JSONArray?): List<HistorialContrasena> {
        if (lista == null) return emptyList()
        return (0 until lista.length()).map { i ->
            val anotacion = lista.getJSONObject(i)
            HistorialContrasena(
                credencialId = 0,
                contrasena = anotacion.optString("contrasena"),
                creadoEn = anotacion.optLong("creadoEn"),
                actual = anotacion.optBoolean("actual", false)
            )
        }
    }

    /**
     * Deja el historial de una entrada en el unico estado que la app considera
     * valido: exactamente una fila marcada como actual, y esa fila con la misma
     * contrasena que la credencial.
     *
     * Hace falta porque el archivo viene de fuera. Puede ser de la version 1 y
     * no traer historial; puede haberse escrito a mano con dos marcas, con
     * ninguna, o con la marca puesta en una contrasena que la credencial ya no
     * usa. Ninguno de esos casos debe entrar en la base: el resto de la app da
     * por hecho que la marca y la credencial dicen lo mismo.
     *
     * Cuando no hay ninguna fila que sirva de actual se agrega una con la
     * contrasena de la credencial. Nunca se descarta lo que el archivo traiga:
     * las demas contrasenas se conservan tal cual, solo pierden la marca.
     */
    fun normaliza(credencial: Credencial, historial: List<HistorialContrasena>): Entrada {
        val marcada = historial.firstOrNull { it.actual && it.contrasena == credencial.contrasena }
            ?: historial.firstOrNull { it.contrasena == credencial.contrasena }

        if (marcada == null) {
            val nueva = HistorialContrasena(
                credencialId = 0,
                contrasena = credencial.contrasena,
                creadoEn = credencial.actualizadoEn,
                actual = true
            )
            return Entrada(credencial, listOf(nueva) + historial.map { it.copy(actual = false) })
        }

        var puesta = false
        return Entrada(
            credencial,
            historial.map { anotacion ->
                if (!puesta && anotacion === marcada) {
                    puesta = true
                    anotacion.copy(actual = true)
                } else {
                    anotacion.copy(actual = false)
                }
            }
        )
    }

    // --- Comun ------------------------------------------------------------

    /**
     * Los campos de la cabecera que no pueden cambiar sin invalidar el archivo.
     * Se serializan a mano y en orden fijo: no vale usar el JSON tal cual,
     * porque el orden de las claves no esta garantizado y el AAD tiene que ser
     * byte a byte el mismo al escribir y al leer.
     */
    private fun cabeceraAutenticada(version: Int, iteraciones: Int, salB64: String): ByteArray =
        "$FORMATO|$version|PBKDF2WithHmacSHA256|$iteraciones|$salB64|AES-256-GCM"
            .toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun nombreSugerido(): String {
        val dia = Instant.now().toString().substringBefore('T')
        return "boveda-respaldo-$dia.$EXTENSION"
    }
}
