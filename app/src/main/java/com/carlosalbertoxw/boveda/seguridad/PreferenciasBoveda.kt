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

    /**
     * Sal, iteraciones y sobre viajan siempre juntos: el sobre solo se abre con
     * la llave que sale de esa sal y esas iteraciones. Por eso se escriben en
     * una sola transaccion. Escribirlos por separado deja una ventana —corta,
     * pero real— en la que morir el proceso deja la boveda ilegible para
     * siempre, con la sal nueva y el sobre viejo.
     */
    fun guardaEnvoltura(sal: ByteArray, iteraciones: Int, sobre: ByteArray) {
        prefs.edit()
            .putString(SAL, aBase64(sal))
            .putInt(ITERACIONES, iteraciones)
            .putString(SOBRE_MAESTRA, aBase64(sobre))
            .commit()
    }

    val biometricoActivo: Boolean
        get() = sobreBiometrico != null && AlmacenBiometrico.existeLlave()

    fun apagaBiometrico() {
        AlmacenBiometrico.borraLlave()
        sobreBiometrico = null
    }

    // --- Ajustes de bloqueo ---------------------------------------------
    //
    // Estos tres se escriben con apply() y no con commit(): se cambian desde un
    // toque en Ajustes, y commit() bloquea el hilo principal hasta que el disco
    // responde. Perder el ultimo cambio si el proceso muere en ese instante no
    // rompe nada —el valor por omision sigue siendo seguro—, asi que no vale un
    // tiron en la interfaz. Lo de abajo, en cambio, sigue con commit().

    /** Minutos en segundo plano antes de cerrar la boveda. 0 = al instante. */
    var minutosAutobloqueo: Int
        get() = prefs.getInt(AUTOBLOQUEO, 1)
        set(valor) { prefs.edit().putInt(AUTOBLOQUEO, valor).apply() }

    /** Intentos fallidos tras los cuales se borra todo. 0 = nunca. */
    var borrarTrasFallos: Int
        get() = prefs.getInt(BORRAR_TRAS, 0)
        set(valor) { prefs.edit().putInt(BORRAR_TRAS, valor).apply() }

    var segundosPortapapeles: Int
        get() = prefs.getInt(PORTAPAPELES, 30)
        set(valor) { prefs.edit().putInt(PORTAPAPELES, valor).apply() }

    // --- Freno a la fuerza bruta ----------------------------------------

    var fallosSeguidos: Int
        get() = prefs.getInt(FALLOS, 0)
        set(valor) { prefs.edit().putInt(FALLOS, valor).commit() }

    /**
     * El castigo se guarda medido con los dos relojes del sistema, porque
     * ninguno de los dos aguanta solo (ver [Reloj]): el monotonico se reinicia
     * al apagar el telefono y el de pared lo mueve cambiar la fecha. Guardar
     * ambos permite cruzarlos al leerlos y detectar cual de los dos mintio.
     */
    var esperaMonotonicaHasta: Long
        get() = prefs.getLong(ESPERA_MONOTONICA, 0L)
        set(valor) { prefs.edit().putLong(ESPERA_MONOTONICA, valor).commit() }

    var esperaParedHasta: Long
        get() = prefs.getLong(ESPERA_PARED, 0L)
        set(valor) { prefs.edit().putLong(ESPERA_PARED, valor).commit() }

    fun limpiaEspera() {
        prefs.edit().putLong(ESPERA_MONOTONICA, 0L).putLong(ESPERA_PARED, 0L).commit()
    }

    fun borraTodo() {
        prefs.edit().clear().commit()
    }

    // --- Plomeria --------------------------------------------------------

    private fun leeBytes(clave: String): ByteArray? =
        prefs.getString(clave, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    private fun aBase64(valor: ByteArray): String = Base64.encodeToString(valor, Base64.NO_WRAP)

    /** Siempre commit y no apply: si el proceso muere a medias, la boveda queda ilegible. */
    private fun escribeBytes(clave: String, valor: ByteArray?) {
        val editor = prefs.edit()
        if (valor == null) editor.remove(clave)
        else editor.putString(clave, aBase64(valor))
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
        const val ESPERA_MONOTONICA = "espera_hasta"
        const val ESPERA_PARED = "espera_pared_hasta"
    }
}
