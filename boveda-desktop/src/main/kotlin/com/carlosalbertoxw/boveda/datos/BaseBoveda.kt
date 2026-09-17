package com.carlosalbertoxw.boveda.datos

import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.seguridad.Sobre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lo que hay dentro de la boveda en un instante dado. Inmutable: cada
 * transaccion produce uno nuevo, y solo se publica si llego entero al disco.
 *
 * Los contadores de id no retroceden nunca, igual que AUTOINCREMENT en SQLite:
 * un id borrado no se reusa, asi que una pantalla que se quedo con un id viejo
 * no puede acabar mostrando otra entrada.
 */
data class Contenido(
    val credenciales: List<Credencial>,
    val historial: List<HistorialContrasena>,
    val siguienteIdCredencial: Long,
    val siguienteIdHistorial: Long
) {
    companion object {
        val VACIO = Contenido(emptyList(), emptyList(), 1, 1)
    }
}

/**
 * La base de la boveda: el equivalente de escritorio de SQLCipher + Room.
 *
 * En Android la base es un SQLite cifrado entero con SQLCipher. En escritorio no
 * hay un SQLCipher para la JVM que funcione igual en Windows, macOS y Linux sin
 * compilar codigo nativo, y meter uno a medias ampliaria la superficie de
 * confianza justo en la pieza mas delicada. Una boveda personal pesa kilobytes,
 * asi que aqui el archivo entero es un solo sobre AES-256-GCM:
 *
 * ```
 * "BVDB" || IV (12) || cifrado( JSON con credenciales e historial ) || etiqueta (16)
 * ```
 *
 * Lo que se conserva de SQLCipher es lo que importa:
 *
 *  - El archivo es ruido sin la frase, y la etiqueta GCM detecta cualquier
 *    byte alterado (SQLCipher lo hace con un HMAC por pagina).
 *  - La frase no es la maestra: es aleatoria y la maestra solo la desenvuelve
 *    ([com.carlosalbertoxw.boveda.seguridad.GestorBoveda]).
 *  - Las transacciones son atomicas: se escribe un temporal completo y se
 *    renombra encima ([ArchivoSeguro]). Una credencial y su historial se
 *    mueven juntos o no se mueven, igual que con `@Transaction` en Room.
 *  - **Nunca se destruye lo guardado para poder abrir.** Si el esquema del
 *    archivo es mas nuevo que el que entiende esta version, no se abre y se
 *    dice por que, con los datos intactos.
 *
 * Mientras la boveda esta cerrada no existe ningun objeto de estos: se construye
 * al desbloquear y se descarta al bloquear, con la frase puesta en ceros.
 */
class BaseBoveda private constructor(
    private val archivo: Path,
    private var frase: ByteArray?,
    inicial: Contenido
) {

    private val cerrojo = Any()

    private val _contenido = MutableStateFlow(inicial)

    /** Lo que se observa desde la interfaz. Al cerrar pasa a vacio. */
    val contenido: StateFlow<Contenido> = _contenido.asStateFlow()

    private val dao = CredencialDao(this)

    fun credencialDao(): CredencialDao = dao

    val abierta: Boolean get() = synchronized(cerrojo) { frase != null }

    /**
     * Lectura de una sola vez. Falla si la base ya se cerro, igual que Room
     * contra una base cerrada: devolver una lista vacia haria creer a quien
     * exporta que la boveda estaba vacia.
     */
    internal fun lee(): Contenido = synchronized(cerrojo) {
        checkNotNull(frase) { Textos.ERROR_BASE_CERRADA }
        _contenido.value
    }

    /**
     * Aplica un cambio de forma atomica. El bloque recibe el contenido actual y
     * devuelve el nuevo junto con lo que la operacion quiera contestar. Si
     * devuelve el mismo objeto no se escribe nada.
     *
     * El cerrojo es normal y no un Mutex de corrutinas porque dentro no hay
     * ninguna suspension: calcular el cambio, cifrar y escribir unos kilobytes.
     * Asi [cierra] puede esperar a que termine la escritura en curso sin ser
     * suspend, y la frase no se pone en ceros a media escritura.
     */
    internal suspend fun <T> transaccion(cambio: (Contenido) -> Pair<Contenido, T>): T =
        withContext(Dispatchers.IO) {
            synchronized(cerrojo) {
                val llave = checkNotNull(frase) { Textos.ERROR_BASE_CERRADA }
                val actual = _contenido.value
                val (nuevo, respuesta) = cambio(actual)
                if (nuevo !== actual) {
                    escribe(archivo, llave, nuevo)
                    _contenido.value = nuevo
                }
                respuesta
            }
        }

    fun cierra() {
        synchronized(cerrojo) {
            frase?.let { Sobre.borra(it) }
            frase = null
            _contenido.value = Contenido.VACIO
        }
    }

    companion object {
        const val NOMBRE = "boveda.dat"

        /**
         * Version de la estructura de dentro. La 2 es la misma que la base 2 de
         * Android: credenciales con `eliminadoEn` y la tabla de historial.
         *
         * Politica de migracion: **siempre explicita, nunca destructiva.** Al
         * cambiar la estructura hay que subir este numero y escribir en
         * [migra] el paso desde la anterior. Un archivo de un esquema mas nuevo
         * no se abre ni se toca.
         */
        const val ESQUEMA = 2

        private val MAGIA = "BVDB".toByteArray(Charsets.US_ASCII)

        /** La cabecera va autenticada: nadie puede hacer pasar el archivo por otro formato. */
        private val AAD = "boveda-base|1|AES-256-GCM".toByteArray(Charsets.UTF_8)

        /**
         * Abre (o crea, si todavia no existe) la base con [frase]. La base se
         * queda con el arreglo y lo pone en ceros al cerrar, asi que quien
         * llama debe pasar una copia suya, igual que con SupportOpenHelperFactory.
         *
         * Lanza [java.security.GeneralSecurityException] si la frase no abre el
         * archivo o si este fue alterado.
         */
        fun abre(directorio: Path, frase: ByteArray): BaseBoveda {
            val archivo = directorio.resolve(NOMBRE)
            try {
                val contenido = if (Files.exists(archivo)) {
                    leeArchivo(archivo, frase)
                } else {
                    Contenido.VACIO.also { escribe(archivo, frase, it) }
                }
                return BaseBoveda(archivo, frase, contenido)
            } catch (e: Throwable) {
                Sobre.borra(frase)
                throw e
            }
        }

        /** Borra el archivo y su temporal. Se usa al crear y al restablecer la boveda. */
        fun borraArchivos(directorio: Path) {
            listOf(NOMBRE, "$NOMBRE.tmp").forEach { Files.deleteIfExists(directorio.resolve(it)) }
        }

        // --- Disco ------------------------------------------------------

        private fun escribe(archivo: Path, frase: ByteArray, contenido: Contenido) {
            val claro = aJson(contenido).toString().toByteArray(Charsets.UTF_8)
            try {
                ArchivoSeguro.escribe(archivo, MAGIA + Sobre.cierra(frase, claro, AAD))
            } finally {
                Sobre.borra(claro)
            }
        }

        private fun leeArchivo(archivo: Path, frase: ByteArray): Contenido {
            val bytes = Files.readAllBytes(archivo)
            require(bytes.size > MAGIA.size + Sobre.BYTES_IV &&
                bytes.copyOfRange(0, MAGIA.size).contentEquals(MAGIA)) { Textos.ERROR_BASE_ILEGIBLE }
            val claro = Sobre.abre(frase, bytes.copyOfRange(MAGIA.size, bytes.size), AAD)
            try {
                return migra(JSONObject(String(claro, Charsets.UTF_8)))
            } finally {
                Sobre.borra(claro)
            }
        }

        /**
         * Lleva el JSON de cualquier esquema conocido al actual. Hoy solo existe
         * el 2; cuando haya un 3, aqui va el paso `2 -> 3`, sin descartar nada.
         */
        private fun migra(json: JSONObject): Contenido {
            val esquema = json.optInt("esquema", -1)
            check(esquema in 1..ESQUEMA) { Textos.errorEsquemaFuturo(esquema, ESQUEMA) }
            return deJson(json)
        }

        private fun aJson(contenido: Contenido): JSONObject = JSONObject().apply {
            put("esquema", ESQUEMA)
            put("siguienteIdCredencial", contenido.siguienteIdCredencial)
            put("siguienteIdHistorial", contenido.siguienteIdHistorial)
            put(
                "credenciales",
                JSONArray().apply {
                    contenido.credenciales.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("id", c.id)
                                put("titulo", c.titulo)
                                put("usuario", c.usuario)
                                put("contrasena", c.contrasena)
                                put("sitio", c.sitio)
                                put("notas", c.notas)
                                put("creadoEn", c.creadoEn)
                                put("actualizadoEn", c.actualizadoEn)
                                c.eliminadoEn?.let { put("eliminadoEn", it) }
                            }
                        )
                    }
                }
            )
            put(
                "historial",
                JSONArray().apply {
                    contenido.historial.forEach { h ->
                        put(
                            JSONObject().apply {
                                put("id", h.id)
                                put("credencialId", h.credencialId)
                                put("contrasena", h.contrasena)
                                put("creadoEn", h.creadoEn)
                                put("actual", h.actual)
                            }
                        )
                    }
                }
            )
        }

        private fun deJson(json: JSONObject): Contenido {
            val credenciales = json.getJSONArray("credenciales").let { lista ->
                (0 until lista.length()).map { i ->
                    val c = lista.getJSONObject(i)
                    Credencial(
                        id = c.getLong("id"),
                        titulo = c.getString("titulo"),
                        usuario = c.getString("usuario"),
                        contrasena = c.getString("contrasena"),
                        sitio = c.optString("sitio"),
                        notas = c.optString("notas"),
                        creadoEn = c.getLong("creadoEn"),
                        actualizadoEn = c.getLong("actualizadoEn"),
                        eliminadoEn = if (c.has("eliminadoEn")) c.getLong("eliminadoEn") else null
                    )
                }
            }
            val historial = json.getJSONArray("historial").let { lista ->
                (0 until lista.length()).map { i ->
                    val h = lista.getJSONObject(i)
                    HistorialContrasena(
                        id = h.getLong("id"),
                        credencialId = h.getLong("credencialId"),
                        contrasena = h.getString("contrasena"),
                        creadoEn = h.getLong("creadoEn"),
                        actual = h.getBoolean("actual")
                    )
                }
            }
            // Los contadores se protegen contra un archivo incoherente: nunca
            // por debajo del id mas alto que ya existe.
            val maxCredencial = credenciales.maxOfOrNull { it.id } ?: 0
            val maxHistorial = historial.maxOfOrNull { it.id } ?: 0
            return Contenido(
                credenciales = credenciales,
                historial = historial,
                siguienteIdCredencial = maxOf(json.optLong("siguienteIdCredencial", 1), maxCredencial + 1),
                siguienteIdHistorial = maxOf(json.optLong("siguienteIdHistorial", 1), maxHistorial + 1)
            )
        }
    }
}
