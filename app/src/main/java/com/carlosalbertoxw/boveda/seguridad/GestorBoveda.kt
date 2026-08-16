package com.carlosalbertoxw.boveda.seguridad

import android.content.Context
import com.carlosalbertoxw.boveda.R
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
class GestorBoveda(
    private val app: Context,
    private val reloj: Reloj = Reloj.Sistema
) {

    val prefs = PreferenciasBoveda(app)

    private val _estado = MutableStateFlow<EstadoBoveda>(
        if (prefs.estaConfigurada) EstadoBoveda.Cerrada else EstadoBoveda.SinConfigurar
    )
    val estado: StateFlow<EstadoBoveda> = _estado.asStateFlow()

    /**
     * La frase viva mientras la boveda esta abierta. Hace falta guardarla para
     * poder activar la huella o cambiar la maestra sin volver a pedir nada. Se
     * sobreescribe con ceros al bloquear.
     *
     * Se toca desde tres sitios con hilos distintos —Dispatchers.Default al
     * abrir con maestra, IO al abrir con huella y el hilo principal al activar
     * la huella o bloquear—, asi que las lecturas van con @Volatile y todo
     * cambio pasa por [candado]. Es un cerrojo normal y no un Mutex de
     * corrutinas porque [bloquea] y [restablece] no son suspend y las secciones
     * duran lo que tarda asignar un puntero y llenar 32 bytes de ceros.
     */
    @Volatile
    private var fraseViva: ByteArray? = null

    private val candado = Any()

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
            prefs.guardaEnvoltura(sal, Kdf.ITERACIONES, Sobre.cierra(kek, frase))
            prefs.sobreBiometrico = null
            prefs.fallosSeguidos = 0
            prefs.limpiaEspera()
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

        val sal = prefs.sal
            ?: return@withContext Apertura.Falla(texto(R.string.error_no_configurada))
        val sobre = prefs.sobreMaestra
            ?: return@withContext Apertura.Falla(texto(R.string.error_no_configurada))

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
        prefs.limpiaEspera()
        // La maestra correcta esta en memoria justo ahora y en ningun otro
        // momento: es la unica ocasion de encarecer la derivacion sin pedirle
        // nada al usuario. Sin esto, una boveda creada hoy seguiria con las
        // iteraciones de hoy dentro de diez anos.
        if (prefs.iteraciones < Kdf.ITERACIONES) reenvuelve(maestra, frase)
        abreBase(frase)
    }

    /**
     * Vuelve a envolver la misma frase con sal nueva y las iteraciones
     * actuales. No toca la base: igual que [cambiaMaestra], lo que cambia es
     * la envoltura, no lo envuelto.
     */
    private fun reenvuelve(maestra: CharArray, frase: ByteArray) {
        val salNueva = Sobre.aleatorio(Kdf.BYTES_SAL)
        val kek = Kdf.deriva(maestra, salNueva, Kdf.ITERACIONES)
        try {
            prefs.guardaEnvoltura(salNueva, Kdf.ITERACIONES, Sobre.cierra(kek, frase))
        } finally {
            Sobre.borra(kek)
        }
    }

    /**
     * Segundo camino a la misma frase. Recibe el Cipher que BiometricPrompt ya
     * autentico: si no hubo huella valida, el hardware habria hecho fallar el
     * doFinal, asi que llegar aqui con el descifrado hecho ya es la prueba.
     */
    suspend fun abreConBiometrico(cifradorAutenticado: Cipher): Apertura =
        withContext(Dispatchers.IO) {
            val sobre = prefs.sobreBiometrico
                ?: return@withContext Apertura.Falla(texto(R.string.error_atajo_inactivo))
            val frase = try {
                cifradorAutenticado.doFinal(sobre, Sobre.BYTES_IV, sobre.size - Sobre.BYTES_IV)
            } catch (_: GeneralSecurityException) {
                prefs.apagaBiometrico()
                return@withContext Apertura.Falla(
                    texto(R.string.error_atajo_invalido)
                )
            }
            prefs.fallosSeguidos = 0
            prefs.limpiaEspera()
            abreBase(frase)
        }

    private fun abreBase(frase: ByteArray): Apertura = try {
        // Copia defensiva: SupportOpenHelperFactory pone en ceros el arreglo que
        // recibe en cuanto abre la base, y aqui hace falta conservar la frase
        // para poder activar la huella o cambiar la maestra despues.
        val db = BovedaDatabase.abre(app, frase.copyOf())
        // Room abre en diferido, en la primera consulta. Se fuerza la apertura
        // aqui, que ya vamos fuera del hilo principal, para que un archivo
        // corrupto falle ahora y no a media pantalla de listado.
        db.openHelper.writableDatabase
        synchronized(candado) {
            fraseViva?.let { Sobre.borra(it) }
            fraseViva = frase
        }
        _estado.value = EstadoBoveda.Abierta(db)
        Apertura.Ok
    } catch (e: Exception) {
        Sobre.borra(frase)
        Apertura.Falla(e.message ?: texto(R.string.aviso_no_se_pudo_abrir))
    } catch (e: LinkageError) {
        // System.loadLibrary("sqlcipher") no lanza una Exception sino un Error,
        // que el catch de arriba no recoge. Si la libreria nativa no carga
        // —instalacion a medias, ABI que no toca— sin esto la app se cae en vez
        // de decir que no pudo abrir la boveda. Y caerse aqui es peor de lo
        // normal: pasa en la pantalla de desbloqueo, sin ninguna explicacion.
        Sobre.borra(frase)
        Apertura.Falla(e.message ?: texto(R.string.aviso_no_se_pudo_abrir))
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
        synchronized(candado) {
            fraseViva?.let { Sobre.borra(it) }
            fraseViva = null
        }
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
                prefs.guardaEnvoltura(salNueva, Kdf.ITERACIONES, Sobre.cierra(kekNueva, frase))
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
        val frase = synchronized(candado) { fraseViva } ?: return false
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
        synchronized(candado) {
            fraseViva?.let { Sobre.borra(it) }
            fraseViva = null
        }
        BovedaDatabase.borraArchivos(app)
        AlmacenBiometrico.borraLlave()
        prefs.borraTodo()
        _estado.value = EstadoBoveda.SinConfigurar
    }

    // --- Freno a la fuerza bruta -----------------------------------------

    /**
     * Cuanto falta para volver a aceptar intentos, cruzando los dos relojes.
     *
     * El castigo se guarda medido con ambos y aqui se toma **el mayor de los
     * dos restantes**: para volver a intentar hay que haber esperado tanto en
     * el reloj monotonico como en el de pared. Adelantar la fecha del telefono
     * no sirve, porque el monotonico sigue corriendo; y el monotonico por si
     * solo tampoco basta, porque se reinicia al apagar el equipo.
     *
     * La clave esta en [restante]: ningun vencimiento puede pedir mas de lo que
     * dura el castigo mas largo, porque nunca se anota uno mayor. Si al leerlo
     * pide mas, es que ese reloj se movio por debajo —el telefono se reinicio,
     * o alguien cambio la fecha— y su cuenta ya no significa nada, asi que se
     * descarta y manda el otro, que si es coherente.
     *
     * De ahi salen las tres propiedades que se buscan, y que estan probadas en
     * CandadoTest: reiniciar el telefono no alarga la espera (manda el reloj de
     * pared, que sobrevive al apagon), adelantar la fecha no la saltea (manda
     * el monotonico, que no se deja mover), y en ningun caso se puede pedir mas
     * de [CASTIGO_MAXIMO] segundos.
     */
    fun segundosDeEspera(): Int = maxOf(
        restante(prefs.esperaMonotonicaHasta - reloj.monotonico()),
        restante(prefs.esperaParedHasta - reloj.pared())
    )

    private fun restante(milisegundos: Long): Int = when {
        milisegundos <= 0L -> 0
        // Mas que el castigo mas largo posible: este reloj miente, no cuenta.
        milisegundos > CASTIGO_MAXIMO * 1000L -> 0
        else -> ((milisegundos + 999) / 1000).toInt()
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
            val milisegundos = castigo * 1000L
            prefs.esperaMonotonicaHasta = reloj.monotonico() + milisegundos
            prefs.esperaParedHasta = reloj.pared() + milisegundos
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
        fallos >= 9 -> CASTIGO_MAXIMO
        else -> 15 shl (fallos - 4)
    }

    private fun texto(id: Int): String = app.getString(id)

    private fun aHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    companion object {
        /** Techo del castigo, y por tanto techo de cualquier espera posible. */
        const val CASTIGO_MAXIMO = 300
    }
}
