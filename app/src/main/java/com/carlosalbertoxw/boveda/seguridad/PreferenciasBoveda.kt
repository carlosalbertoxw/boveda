package com.carlosalbertoxw.boveda.seguridad

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * Lo unico que la app escribe fuera de la base cifrada.
 *
 * Aqui no hay ningun secreto en claro: solo la sal, el numero de iteraciones y
 * las dos envolturas de la frase de la base, cada una inutil sin su llave. Aun
 * asi el archivo vive en el almacenamiento privado de la app y esta excluido de
 * todo respaldo.
 */
class PreferenciasBoveda(contexto: Context) {

    private val prefs: SharedPreferences =
        contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    // --- Configuracion de la boveda -------------------------------------

    val estaConfigurada: Boolean
        get() = prefs.contains(SAL) && prefs.contains(SOBRE_MAESTRA)

    var sal: ByteArray?
        get() = leeBytes(SAL)
        set(valor) = escribeBytes(SAL, valor)

    var iteraciones: Int
        get() = prefs.getInt(ITERACIONES, Kdf.ITERACIONES)
        set(valor) { prefs.edit().putInt(ITERACIONES, valor).commit() }

    /** Frase de la base envuelta con la llave derivada de la contrasena maestra. */
    var sobreMaestra: ByteArray?
        get() = leeBytes(SOBRE_MAESTRA)
        set(valor) = escribeBytes(SOBRE_MAESTRA, valor)

    /** Frase de la base envuelta con la llave biometrica del Keystore. */
    var sobreBiometrico: ByteArray?
        get() = leeBytes(SOBRE_BIOMETRICO)
        set(valor) = escribeBytes(SOBRE_BIOMETRICO, valor)

    val biometricoActivo: Boolean
        get() = sobreBiometrico != null && AlmacenBiometrico.existeLlave()

    fun apagaBiometrico() {
        AlmacenBiometrico.borraLlave()
        sobreBiometrico = null
    }

    // --- Ajustes de bloqueo ---------------------------------------------

    /** Minutos en segundo plano antes de cerrar la boveda. 0 = al instante. */
    var minutosAutobloqueo: Int
        get() = prefs.getInt(AUTOBLOQUEO, 1)
        set(valor) { prefs.edit().putInt(AUTOBLOQUEO, valor).commit() }

    /** Intentos fallidos tras los cuales se borra todo. 0 = nunca. */
    var borrarTrasFallos: Int
        get() = prefs.getInt(BORRAR_TRAS, 0)
        set(valor) { prefs.edit().putInt(BORRAR_TRAS, valor).commit() }

    var segundosPortapapeles: Int
        get() = prefs.getInt(PORTAPAPELES, 30)
        set(valor) { prefs.edit().putInt(PORTAPAPELES, valor).commit() }

    // --- Freno a la fuerza bruta ----------------------------------------

    var fallosSeguidos: Int
        get() = prefs.getInt(FALLOS, 0)
        set(valor) { prefs.edit().putInt(FALLOS, valor).commit() }

    /**
     * Instante (reloj monotonico del sistema) hasta el que no se aceptan
     * intentos. Se usa elapsedRealtime y no currentTimeMillis a proposito: con
     * la hora del reloj, atrasar la fecha del telefono saltaria el castigo.
     */
    var esperaHasta: Long
        get() = prefs.getLong(ESPERA, 0L)
        set(valor) { prefs.edit().putLong(ESPERA, valor).commit() }

    fun borraTodo() {
        prefs.edit().clear().commit()
    }

    // --- Plomeria --------------------------------------------------------

    private fun leeBytes(clave: String): ByteArray? =
        prefs.getString(clave, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    /** Siempre commit y no apply: si el proceso muere a medias, la boveda queda ilegible. */
    private fun escribeBytes(clave: String, valor: ByteArray?) {
        val editor = prefs.edit()
        if (valor == null) editor.remove(clave)
        else editor.putString(clave, Base64.encodeToString(valor, Base64.NO_WRAP))
        editor.commit()
    }

    private companion object {
        const val ARCHIVO = "boveda_config"
        const val SAL = "sal"
        const val ITERACIONES = "iteraciones"
        const val SOBRE_MAESTRA = "sobre_maestra"
        const val SOBRE_BIOMETRICO = "sobre_biometrico"
        const val AUTOBLOQUEO = "minutos_autobloqueo"
        const val BORRAR_TRAS = "borrar_tras_fallos"
        const val PORTAPAPELES = "segundos_portapapeles"
        const val FALLOS = "fallos_seguidos"
        const val ESPERA = "espera_hasta"
    }
}
