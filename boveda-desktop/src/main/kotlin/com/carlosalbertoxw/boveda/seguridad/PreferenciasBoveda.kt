package com.carlosalbertoxw.boveda.seguridad

import com.carlosalbertoxw.boveda.datos.ArchivoSeguro
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Properties

/**
 * Lo unico que la app escribe fuera de la base cifrada.
 *
 * Aqui no hay ningun secreto en claro: solo la sal, el numero de iteraciones y
 * la envoltura de la frase de la base, inutil sin la llave que sale de la
 * maestra. Aun asi el archivo vive en la carpeta privada de la app.
 *
 * Es el equivalente de las SharedPreferences de Android, con las mismas claves.
 * Cada cambio reescribe el archivo entero de forma atomica ([ArchivoSeguro]),
 * que es lo que en Android daba `commit()`: si el proceso muere a mitad, queda
 * la version anterior completa y no una mezcla.
 */
class PreferenciasBoveda(directorio: Path) {

    private val archivo: Path = directorio.resolve(ARCHIVO)
    private val valores = Properties()
    private val cerrojo = Any()

    init {
        if (Files.exists(archivo)) {
            Files.newInputStream(archivo).use { valores.load(it) }
        }
    }

    // --- Configuracion de la boveda -------------------------------------

    val estaConfigurada: Boolean
        get() = synchronized(cerrojo) { valores.containsKey(SAL) && valores.containsKey(SOBRE_MAESTRA) }

    var sal: ByteArray?
        get() = leeBytes(SAL)
        set(valor) = escribeBytes(SAL, valor)

    var iteraciones: Int
        get() = leeInt(ITERACIONES, Kdf.ITERACIONES)
        set(valor) = edita { it[ITERACIONES] = valor.toString() }

    /** Frase de la base envuelta con la llave derivada de la contrasena maestra. */
    var sobreMaestra: ByteArray?
        get() = leeBytes(SOBRE_MAESTRA)
        set(valor) = escribeBytes(SOBRE_MAESTRA, valor)

    /**
     * Sal, iteraciones y sobre viajan siempre juntos: el sobre solo se abre con
     * la llave que sale de esa sal y esas iteraciones. Por eso se escriben en
     * una sola pasada. Escribirlos por separado deja una ventana —corta, pero
     * real— en la que morir el proceso deja la boveda ilegible para siempre,
     * con la sal nueva y el sobre viejo.
     */
    fun guardaEnvoltura(sal: ByteArray, iteraciones: Int, sobre: ByteArray) = edita {
        it[SAL] = aBase64(sal)
        it[ITERACIONES] = iteraciones.toString()
        it[SOBRE_MAESTRA] = aBase64(sobre)
    }

    // --- Ajustes de bloqueo ---------------------------------------------

    /** Minutos sin usar la app (o fuera de ella) antes de cerrar la boveda. 0 = al instante. */
    var minutosAutobloqueo: Int
        get() = leeInt(AUTOBLOQUEO, 1)
        set(valor) = edita { it[AUTOBLOQUEO] = valor.toString() }

    /** Intentos fallidos tras los cuales se borra todo. 0 = nunca. */
    var borrarTrasFallos: Int
        get() = leeInt(BORRAR_TRAS, 0)
        set(valor) = edita { it[BORRAR_TRAS] = valor.toString() }

    var segundosPortapapeles: Int
        get() = leeInt(PORTAPAPELES, 30)
        set(valor) = edita { it[PORTAPAPELES] = valor.toString() }

    // --- Freno a la fuerza bruta ----------------------------------------

    var fallosSeguidos: Int
        get() = leeInt(FALLOS, 0)
        set(valor) = edita { it[FALLOS] = valor.toString() }

    /**
     * El castigo se guarda medido con los dos relojes del sistema, porque
     * ninguno de los dos aguanta solo (ver [Reloj]): el monotonico se reinicia
     * al apagar el equipo y el de pared lo mueve cambiar la fecha. Guardar
     * ambos permite cruzarlos al leerlos y detectar cual de los dos mintio.
     */
    var esperaMonotonicaHasta: Long
        get() = leeLong(ESPERA_MONOTONICA)
        set(valor) = edita { it[ESPERA_MONOTONICA] = valor.toString() }

    var esperaParedHasta: Long
        get() = leeLong(ESPERA_PARED)
        set(valor) = edita { it[ESPERA_PARED] = valor.toString() }

    fun limpiaEspera() = edita {
        it[ESPERA_MONOTONICA] = "0"
        it[ESPERA_PARED] = "0"
    }

    fun borraTodo() = edita { it.clear() }

    // --- Plomeria --------------------------------------------------------

    /**
     * Aplica el cambio sobre una copia y solo la da por buena si llego al
     * disco. Si escribir falla, la memoria sigue diciendo lo mismo que el
     * archivo, que es lo unico que importa al volver a arrancar.
     */
    private fun edita(cambio: (Properties) -> Unit) {
        synchronized(cerrojo) {
            val copia = Properties().apply { putAll(valores) }
            cambio(copia)
            val salida = ByteArrayOutputStream()
            copia.store(salida, "Boveda: sin secretos en claro. No editar a mano.")
            ArchivoSeguro.escribe(archivo, salida.toByteArray())
            valores.clear()
            valores.putAll(copia)
        }
    }

    private fun leeTexto(clave: String): String? =
        synchronized(cerrojo) { valores.getProperty(clave) }

    private fun leeInt(clave: String, omision: Int): Int =
        leeTexto(clave)?.toIntOrNull() ?: omision

    private fun leeLong(clave: String): Long = leeTexto(clave)?.toLongOrNull() ?: 0L

    private fun leeBytes(clave: String): ByteArray? =
        leeTexto(clave)?.let { Base64.getDecoder().decode(it) }

    private fun aBase64(valor: ByteArray): String = Base64.getEncoder().encodeToString(valor)

    private fun escribeBytes(clave: String, valor: ByteArray?) = edita {
        if (valor == null) it.remove(clave) else it[clave] = aBase64(valor)
    }

    private companion object {
        const val ARCHIVO = "boveda_config.properties"
        const val SAL = "sal"
        const val ITERACIONES = "iteraciones"
        const val SOBRE_MAESTRA = "sobre_maestra"
        const val AUTOBLOQUEO = "minutos_autobloqueo"
        const val BORRAR_TRAS = "borrar_tras_fallos"
        const val PORTAPAPELES = "segundos_portapapeles"
        const val FALLOS = "fallos_seguidos"
        const val ESPERA_MONOTONICA = "espera_hasta"
        const val ESPERA_PARED = "espera_pared_hasta"
    }
}
