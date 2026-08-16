package com.carlosalbertoxw.boveda.seguridad

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.carlosalbertoxw.boveda.datos.BovedaDatabase
import java.security.GeneralSecurityException
import javax.crypto.Cipher

sealed interface EstadoBoveda {
    /** Primer arranque: todavia no hay contrasena maestra. */
    data object SinConfigurar : EstadoBoveda
    data object Cerrada : EstadoBoveda
    data class Abierta(val db: BovedaDatabase) : EstadoBoveda
}

sealed interface Apertura {
    data object Ok : Apertura
    /** Contrasena incorrecta. [esperaSegundos] > 0 cuando ademas entro el castigo. */
    data class Incorrecta(val fallos: Int, val esperaSegundos: Int) : Apertura
    data class EnEspera(val segundos: Int) : Apertura
    /** Se alcanzo el limite de intentos y la boveda se borro. */
    data object Borrada : Apertura
    data class Falla(val mensaje: String) : Apertura
}

/**
 * El guardian de la boveda: todo lo que abre, cierra o rehace la caja pasa por aqui.
 *
 * El esquema es de dos niveles, que es como lo hacen los gestores serios:
 *
 *   contrasena maestra --PBKDF2--> llave de envoltura --AES-GCM--> frase de la base
 *   huella --Keystore/TEE--------> llave de envoltura --AES-GCM--> frase de la base
 *
 * La base se cifra con una frase larga y aleatoria, no con tu contrasena. Eso
 * tiene dos consecuencias practicas: cambiar la contrasena maestra cuesta
 * milisegundos (solo se vuelve a envolver la misma frase, no se recifra la base),
 * y la huella y la contrasena son dos caminos independientes a la misma frase,
 * asi que perder uno no te deja fuera.
 */
class GestorBoveda(private val app: Context) {

    val prefs = PreferenciasBoveda(app)

    private val _estado = MutableStateFlow<EstadoBoveda>(
        if (prefs.estaConfigurada) EstadoBoveda.Cerrada else EstadoBoveda.SinConfigurar
    )
    val estado: StateFlow<EstadoBoveda> = _estado.asStateFlow()

    /**
     * La frase viva mientras la boveda esta abierta. Hace falta guardarla para
     * poder activar la huella o cambiar la maestra sin volver a pedir nada. Se
     * sobreescribe con ceros al bloquear.
     */
    private var fraseViva: ByteArray? = null

    val abierta: Boolean get() = _estado.value is EstadoBoveda.Abierta

    // --- Alta ------------------------------------------------------------

    suspend fun crea(maestra: CharArray): Apertura = withContext(Dispatchers.Default) {
        // Si quedaron restos de una boveda anterior, el archivo viejo no abriria
        // con la frase nueva. Se limpia para arrancar en limpio.
        BovedaDatabase.borraArchivos(app)

        val frase = aHex(Sobre.aleatorio(32)).toByteArray(Charsets.UTF_8)
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val kek = Kdf.deriva(maestra, sal, Kdf.ITERACIONES)
        try {
            prefs.sal = sal
            prefs.iteraciones = Kdf.ITERACIONES
            prefs.sobreMaestra = Sobre.cierra(kek, frase)
            prefs.sobreBiometrico = null
            prefs.fallosSeguidos = 0
            prefs.esperaHasta = 0L
            AlmacenBiometrico.borraLlave()
        } finally {
            Sobre.borra(kek)
        }
        abreBase(frase)
    }

    // --- Desbloqueo ------------------------------------------------------

    suspend fun abreConMaestra(maestra: CharArray): Apertura = withContext(Dispatchers.Default) {
        val espera = segundosDeEspera()
        if (espera > 0) return@withContext Apertura.EnEspera(espera)

        val sal = prefs.sal ?: return@withContext Apertura.Falla("La boveda no esta configurada.")
        val sobre = prefs.sobreMaestra
            ?: return@withContext Apertura.Falla("La boveda no esta configurada.")

        val kek = Kdf.deriva(maestra, sal, prefs.iteraciones)
        val frase = try {
            Sobre.abre(kek, sobre)
        } catch (_: GeneralSecurityException) {
            // La etiqueta GCM no cuadro: la contrasena no es la correcta.
            null
        } finally {
            Sobre.borra(kek)
        }

        if (frase == null) return@withContext registraFallo()

        prefs.fallosSeguidos = 0
        prefs.esperaHasta = 0L
        abreBase(frase)
    }

    /**
     * Segundo camino a la misma frase. Recibe el Cipher que BiometricPrompt ya
     * autentico: si no hubo huella valida, el hardware habria hecho fallar el
     * doFinal, asi que llegar aqui con el descifrado hecho ya es la prueba.
     */
    suspend fun abreConBiometrico(cifradorAutenticado: Cipher): Apertura =
        withContext(Dispatchers.IO) {
            val sobre = prefs.sobreBiometrico
                ?: return@withContext Apertura.Falla("El atajo no esta activo.")
            val frase = try {
                cifradorAutenticado.doFinal(sobre, Sobre.BYTES_IV, sobre.size - Sobre.BYTES_IV)
            } catch (_: GeneralSecurityException) {
                prefs.apagaBiometrico()
                return@withContext Apertura.Falla(
                    "El atajo dejo de ser valido. Usa tu contrasena maestra."
                )
            }
            prefs.fallosSeguidos = 0
            prefs.esperaHasta = 0L
            abreBase(frase)
        }

    private fun abreBase(frase: ByteArray): Apertura = try {
        // Copia defensiva: SupportOpenHelperFactory pone en ceros el arreglo que
        // recibe en cuanto abre la base, y aqui hace falta conservar la frase
        // para poder activar la huella o cambiar la maestra despues.
        val db = BovedaDatabase.abre(app, String(frase.copyOf(), Charsets.UTF_8))
        // Room abre en diferido, en la primera consulta. Se fuerza la apertura
        // aqui, que ya vamos fuera del hilo principal, para que un archivo
        // corrupto falle ahora y no a media pantalla de listado.
        db.openHelper.writableDatabase
        fraseViva?.let { Sobre.borra(it) }
        fraseViva = frase
        _estado.value = EstadoBoveda.Abierta(db)
        Apertura.Ok
    } catch (e: Exception) {
        Sobre.borra(frase)
        Apertura.Falla(e.message ?: "No se pudo abrir la boveda.")
    }

    // --- Cierre ----------------------------------------------------------

    fun bloquea() {
        val abierta = _estado.value as? EstadoBoveda.Abierta ?: return
        // Primero se anuncia el cierre y solo despues se cierra la base: asi los
        // flujos de la interfaz dejan de escucharla antes de que desaparezca.
        // La carrera que queda la absorbe el catch del modelo de vista.
        _estado.value =
            if (prefs.estaConfigurada) EstadoBoveda.Cerrada else EstadoBoveda.SinConfigurar
        runCatching { abierta.db.close() }
        fraseViva?.let { Sobre.borra(it) }
        fraseViva = null
    }

    // --- Mantenimiento ---------------------------------------------------

    /**
     * Cambiar la maestra no toca la base: se vuelve a envolver la misma frase
     * con una sal y una llave nuevas. Instantaneo, y sin ventana en la que los
     * datos queden a medio recifrar.
     */
    suspend fun cambiaMaestra(actual: CharArray, nueva: CharArray): Boolean =
        withContext(Dispatchers.Default) {
            val sal = prefs.sal ?: return@withContext false
            val sobre = prefs.sobreMaestra ?: return@withContext false

            val kekVieja = Kdf.deriva(actual, sal, prefs.iteraciones)
            val frase = try {
                Sobre.abre(kekVieja, sobre)
            } catch (_: GeneralSecurityException) {
                null
            } finally {
                Sobre.borra(kekVieja)
            } ?: return@withContext false

            val salNueva = Sobre.aleatorio(Kdf.BYTES_SAL)
            val kekNueva = Kdf.deriva(nueva, salNueva, Kdf.ITERACIONES)
            try {
                prefs.sal = salNueva
                prefs.iteraciones = Kdf.ITERACIONES
                prefs.sobreMaestra = Sobre.cierra(kekNueva, frase)
            } finally {
                Sobre.borra(kekNueva, frase)
            }
            true
        }

    /**
     * Comprueba la maestra sin abrir ni cerrar nada. Se usa para autorizar
     * acciones graves con la boveda ya abierta —exportar todo, por ejemplo—,
     * donde el atacante posible es alguien que agarro el telefono desbloqueado.
     */
    suspend fun verificaMaestra(maestra: CharArray): Boolean = withContext(Dispatchers.Default) {
        val sal = prefs.sal ?: return@withContext false
        val sobre = prefs.sobreMaestra ?: return@withContext false
        val kek = Kdf.deriva(maestra, sal, prefs.iteraciones)
        try {
            Sobre.abre(kek, sobre).also { Sobre.borra(it) }
            true
        } catch (_: GeneralSecurityException) {
            false
        } finally {
            Sobre.borra(kek)
        }
    }

    /** Envuelve la frase viva con la llave del Keystore ya autenticada. */
    fun activaBiometrico(cifradorAutenticado: Cipher): Boolean {
        val frase = fraseViva ?: return false
        return runCatching {
            prefs.sobreBiometrico = cifradorAutenticado.iv + cifradorAutenticado.doFinal(frase)
            true
        }.getOrElse {
            prefs.apagaBiometrico()
            false
        }
    }

    /** Borrado total e irreversible: base, envolturas y llave del Keystore. */
    fun restablece() {
        (_estado.value as? EstadoBoveda.Abierta)?.db?.let { runCatching { it.close() } }
        fraseViva?.let { Sobre.borra(it) }
        fraseViva = null
        BovedaDatabase.borraArchivos(app)
        AlmacenBiometrico.borraLlave()
        prefs.borraTodo()
        _estado.value = EstadoBoveda.SinConfigurar
    }

    // --- Freno a la fuerza bruta -----------------------------------------

    fun segundosDeEspera(): Int {
        val restante = prefs.esperaHasta - SystemClock.elapsedRealtime()
        return if (restante > 0) ((restante + 999) / 1000).toInt() else 0
    }

    private fun registraFallo(): Apertura {
        val fallos = prefs.fallosSeguidos + 1
        prefs.fallosSeguidos = fallos

        val limite = prefs.borrarTrasFallos
        if (limite > 0 && fallos >= limite) {
            restablece()
            return Apertura.Borrada
        }

        val castigo = castigo(fallos)
        if (castigo > 0) {
            prefs.esperaHasta = SystemClock.elapsedRealtime() + castigo * 1000L
        }
        return Apertura.Incorrecta(fallos, castigo)
    }

    /**
     * Los primeros tres intentos salen gratis —uno se equivoca al teclear— y a
     * partir de ahi la espera se duplica hasta cinco minutos. Con 210 000
     * iteraciones de PBKDF2 por intento, probar un diccionario a mano deja de
     * ser viable mucho antes de eso.
     */
    private fun castigo(fallos: Int): Int = when {
        fallos <= 3 -> 0
        fallos >= 9 -> 300
        else -> 15 shl (fallos - 4)
    }

    private fun aHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
