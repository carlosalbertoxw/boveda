package com.carlosalbertoxw.boveda.seguridad

import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.BaseBoveda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.security.GeneralSecurityException

sealed interface EstadoBoveda {
    /** Primer arranque: todavia no hay contrasena maestra. */
    data object SinConfigurar : EstadoBoveda
    data object Cerrada : EstadoBoveda
    data class Abierta(val db: BaseBoveda) : EstadoBoveda
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
 * El esquema es de dos niveles, igual que en Android:
 *
 *   contrasena maestra --PBKDF2--> llave de envoltura --AES-GCM--> frase de la base
 *
 * La base se cifra con una frase larga y aleatoria, no con tu contrasena. Por
 * eso cambiar la contrasena maestra cuesta milisegundos: solo se vuelve a
 * envolver la misma frase, no se recifra la base.
 *
 * Lo que no existe en escritorio es el segundo camino, la huella. En Android la
 * llave del atajo vive en el chip seguro y el hardware se niega a usarla sin
 * una huella valida. Desde la JVM no hay una API equivalente comun a Windows
 * Hello, Touch ID y Linux, y guardar la frase en el llavero del sistema sin esa
 * atadura al hardware seria un atajo mas debil que la maestra que pretende
 * sustituir. Se prefirio no ofrecerlo.
 */
class GestorBoveda(
    private val directorio: Path,
    private val reloj: Reloj = Reloj.Sistema
) {

    val prefs = PreferenciasBoveda(directorio)

    private val _estado = MutableStateFlow<EstadoBoveda>(
        if (prefs.estaConfigurada) EstadoBoveda.Cerrada else EstadoBoveda.SinConfigurar
    )
    val estado: StateFlow<EstadoBoveda> = _estado.asStateFlow()

    val abierta: Boolean get() = _estado.value is EstadoBoveda.Abierta

    /** Donde viven los archivos cifrados, para contarselo al usuario en Ajustes. */
    val carpeta: Path get() = directorio

    // --- Alta ------------------------------------------------------------

    suspend fun crea(maestra: CharArray): Apertura = withContext(Dispatchers.Default) {
        // Si quedaron restos de una boveda anterior, el archivo viejo no abriria
        // con la frase nueva. Se limpia para arrancar en limpio.
        BaseBoveda.borraArchivos(directorio)

        val frase = Sobre.aleatorio(BYTES_FRASE)
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val kek = Kdf.deriva(maestra, sal, Kdf.ITERACIONES)
        try {
            prefs.guardaEnvoltura(sal, Kdf.ITERACIONES, Sobre.cierra(kek, frase))
            prefs.fallosSeguidos = 0
            prefs.limpiaEspera()
        } catch (e: Exception) {
            Sobre.borra(frase)
            throw e
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
            ?: return@withContext Apertura.Falla(Textos.ERROR_NO_CONFIGURADA)
        val sobre = prefs.sobreMaestra
            ?: return@withContext Apertura.Falla(Textos.ERROR_NO_CONFIGURADA)

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
        // nada al usuario.
        if (prefs.iteraciones < Kdf.ITERACIONES) reenvuelve(maestra, frase)
        abreBase(frase)
    }

    /**
     * Vuelve a envolver la misma frase con sal nueva y las iteraciones
     * actuales. No toca la base: lo que cambia es la envoltura, no lo envuelto.
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
     * La frase solo vive dentro de [BaseBoveda] mientras la boveda esta abierta
     * (le hace falta para escribir cada cambio) y se pone en ceros al cerrar.
     * A diferencia de Android, aqui no se guarda otra copia: sin huella que
     * activar, nadie mas la necesita.
     */
    private fun abreBase(frase: ByteArray): Apertura = try {
        val db = BaseBoveda.abre(directorio, frase.copyOf())
        _estado.value = EstadoBoveda.Abierta(db)
        Apertura.Ok
    } catch (_: GeneralSecurityException) {
        Apertura.Falla(Textos.ERROR_BASE_ILEGIBLE)
    } catch (e: Exception) {
        Apertura.Falla(e.message ?: Textos.AVISO_NO_SE_PUDO_ABRIR)
    } finally {
        Sobre.borra(frase)
    }

    // --- Cierre ----------------------------------------------------------

    fun bloquea() {
        val abierta = _estado.value as? EstadoBoveda.Abierta ?: return
        // Primero se anuncia el cierre y solo despues se cierra la base: asi los
        // flujos de la interfaz dejan de escucharla antes de que desaparezca.
        _estado.value =
            if (prefs.estaConfigurada) EstadoBoveda.Cerrada else EstadoBoveda.SinConfigurar
        abierta.db.cierra()
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
     * donde el atacante posible es alguien que se sento en el equipo desbloqueado.
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

    /** Borrado total e irreversible: base y envoltura. */
    fun restablece() {
        (_estado.value as? EstadoBoveda.Abierta)?.db?.cierra()
        BaseBoveda.borraArchivos(directorio)
        prefs.borraTodo()
        _estado.value = EstadoBoveda.SinConfigurar
    }

    // --- Freno a la fuerza bruta -----------------------------------------

    /**
     * Cuanto falta para volver a aceptar intentos, cruzando los dos relojes.
     *
     * El castigo se guarda medido con ambos y aqui se toma **el mayor de los
     * dos restantes**: para volver a intentar hay que haber esperado tanto en
     * el reloj monotonico como en el de pared. Adelantar la fecha del equipo
     * no sirve, porque el monotonico sigue corriendo; y atrasarla tampoco
     * alarga nada, porque ningun vencimiento puede pedir mas que el castigo mas
     * largo ([CASTIGO_MAXIMO]): si el de pared pide mas, ese reloj se movio por
     * debajo y no cuenta.
     *
     * **Reiniciar vuelve a empezar el castigo.** El monotonico se reinicia al
     * apagar el equipo, y se nota porque de pronto pide mas de lo posible.
     * Tras un reinicio solo queda el reloj de pared, y ese se puede adelantar a
     * mano: no hay forma de distinguir el tiempo que de verdad paso de un
     * cambio de fecha. Por eso el castigo se vuelve a armar completo desde ese
     * momento, medido con el monotonico nuevo. Lo paga quien reinicia a mitad
     * de una espera —como mucho [CASTIGO_MAXIMO] segundos— y a cambio reiniciar
     * y tocar la fecha deja de ser una forma de saltarse el freno.
     *
     * Una espera ya cumplida se borra en cuanto se ve vencida, para que un
     * reinicio posterior no la confunda con una pendiente.
     *
     * Las propiedades estan probadas en CandadoTest.
     */
    fun segundosDeEspera(): Int {
        val hastaMonotonico = prefs.esperaMonotonicaHasta
        val hastaPared = prefs.esperaParedHasta
        if (hastaMonotonico == 0L && hastaPared == 0L) return 0

        val faltaMonotonico = hastaMonotonico - reloj.monotonico()
        if (faltaMonotonico > CASTIGO_MAXIMO * 1000L) {
            val castigo = castigo(prefs.fallosSeguidos)
            if (castigo > 0) armaEspera(castigo) else prefs.limpiaEspera()
            return castigo
        }

        val espera = maxOf(restante(faltaMonotonico), restante(hastaPared - reloj.pared()))
        if (espera == 0) prefs.limpiaEspera()
        return espera
    }

    private fun restante(milisegundos: Long): Int = when {
        milisegundos <= 0L -> 0
        // Mas que el castigo mas largo posible: este reloj miente, no cuenta.
        milisegundos > CASTIGO_MAXIMO * 1000L -> 0
        else -> ((milisegundos + 999) / 1000).toInt()
    }

    private fun armaEspera(segundos: Int) {
        val milisegundos = segundos * 1000L
        prefs.esperaMonotonicaHasta = reloj.monotonico() + milisegundos
        prefs.esperaParedHasta = reloj.pared() + milisegundos
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
        if (castigo > 0) armaEspera(castigo)
        return Apertura.Incorrecta(fallos, castigo)
    }

    /**
     * Los primeros tres intentos salen gratis —uno se equivoca al teclear— y a
     * partir de ahi la espera se duplica hasta cinco minutos.
     */
    private fun castigo(fallos: Int): Int = when {
        fallos <= 3 -> 0
        fallos >= 9 -> CASTIGO_MAXIMO
        else -> 15 shl (fallos - 4)
    }

    companion object {
        /** Techo del castigo, y por tanto techo de cualquier espera posible. */
        const val CASTIGO_MAXIMO = 300

        /** 256 bits: la llave AES de la base sale directamente de aqui. */
        private const val BYTES_FRASE = 32
    }
}
