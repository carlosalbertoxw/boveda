package com.carlosalbertoxw.boveda

import androidx.compose.ui.input.key.KeyEvent
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import com.carlosalbertoxw.boveda.seguridad.Portapapeles
import com.carlosalbertoxw.boveda.seguridad.Reloj
import com.carlosalbertoxw.boveda.ui.Avisos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path

/**
 * Aqui vive el estado de la boveda, no en la ventana.
 *
 * Es el `Application` de Android: el candado, el buzon de avisos y las reglas
 * de cuando cerrar. En escritorio "irse a segundo plano" significa que la
 * ventana pierde el foco —se cambio a otra aplicacion o se minimizo—, y eso es
 * lo que llega a [alPerderFoco] y [alRecuperarFoco].
 */
class AplicacionBoveda(
    directorio: Path,
    private val reloj: Reloj = Reloj.Sistema
) {

    val gestor = GestorBoveda(directorio, reloj)

    /** Buzon unico de mensajes: lo llenan los modelos, lo vacia la barra de avisos. */
    val avisos = Avisos()

    /** Para tareas que deben sobrevivir a la pantalla, como borrar el portapapeles. */
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Los atajos de teclado de la pantalla que este delante, si los tiene. */
    @Volatile
    var atajos: ((KeyEvent) -> Boolean)? = null

    private var salidaEn = 0L
    private var permisoSalidaHasta = 0L

    /** Ultimo gesto del usuario (tecla, clic o rueda) dentro de la app. */
    @Volatile
    var ultimoToque = reloj.monotonico()
        private set

    fun registraToque() {
        ultimoToque = reloj.monotonico()
    }

    /** Si toca cerrar por llevar demasiado tiempo sin que nadie toque nada. */
    fun caducoPorInactividad(): Boolean {
        if (salidaPermitida) {
            // El selector de archivos esta al frente: no hay gestos que
            // registrar, y eso no es haber dejado la app sola.
            registraToque()
            return false
        }
        // Con "al instante" configurado, con la ventana al frente se toma un
        // minuto: cerrar la boveda mientras la miras seria absurdo.
        val minutos = gestor.prefs.minutosAutobloqueo.coerceAtLeast(1)
        return reloj.monotonico() - ultimoToque >= minutos * 60_000L
    }

    /**
     * Abrir el selector de archivos del sistema le quita el foco a la ventana,
     * y el cierre automatico la bloquearia a media exportacion. Esto concede
     * una excepcion corta y acotada en el tiempo: si el usuario abandona el
     * selector y no vuelve, el permiso caduca solo.
     */
    fun permiteSalidaBreve(segundos: Int = 180) {
        permisoSalidaHasta = reloj.monotonico() + segundos * 1000L
    }

    val salidaPermitida: Boolean
        get() = reloj.monotonico() < permisoSalidaHasta

    fun alPerderFoco() {
        if (salidaPermitida) {
            // Salida esperada: el selector de archivos. No cuenta.
            salidaEn = 0L
            return
        }
        salidaEn = reloj.monotonico()
        if (gestor.prefs.minutosAutobloqueo == 0) gestor.bloquea()
    }

    fun alRecuperarFoco() {
        permisoSalidaHasta = 0L
        registraToque()
        if (salidaEn == 0L) return
        val limite = gestor.prefs.minutosAutobloqueo * 60_000L
        if (reloj.monotonico() - salidaEn >= limite) gestor.bloquea()
        salidaEn = 0L
    }

    /** Al cerrar la ventana: nada de lo copiado se queda atras, y la base se cierra. */
    fun alSalir() {
        Portapapeles.limpiaTodo()
        gestor.bloquea()
    }
}
