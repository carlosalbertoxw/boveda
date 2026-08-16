package com.carlosalbertoxw.boveda

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import com.carlosalbertoxw.boveda.seguridad.Portapapeles
import com.carlosalbertoxw.boveda.ui.Avisos

/**
 * Aqui vive el estado de la boveda, no en una Activity.
 *
 * Es deliberado: si el candado dependiera de la Activity, girar el telefono la
 * recrearia y habria que decidir si se reabre sola —lo que anularia el bloqueo—
 * o se vuelve a pedir la contrasena en cada rotacion. Colgado del proceso, el
 * candado sobrevive a la rotacion y se cierra cuando el proceso pierde el foco.
 */
class AplicacionBoveda : Application() {

    lateinit var gestor: GestorBoveda
        private set

    /** Buzon unico de mensajes: lo llenan los modelos, lo vacia la barra de avisos. */
    val avisos = Avisos()

    /** Para tareas que deben sobrevivir a la pantalla, como borrar el portapapeles. */
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var salidaEn = 0L
    private var permisoSalidaHasta = 0L

    /**
     * Ultimo toque del usuario, para el cierre por inactividad con la app al
     * frente. Vive aqui y no en la Activity porque la Activity se recrea al
     * rotar el telefono o al cambiar el tema, y con el contador dentro cada
     * rotacion regalaba un ciclo entero de inactividad.
     */
    @Volatile
    var ultimoToque = SystemClock.elapsedRealtime()
        private set

    fun registraToque() {
        ultimoToque = SystemClock.elapsedRealtime()
    }

    /** Si toca cerrar por llevar demasiado tiempo sin que nadie toque nada. */
    fun caducoPorInactividad(): Boolean {
        if (salidaPermitida) {
            // El selector de archivos esta al frente: no hay toques que
            // registrar, y eso no es haber dejado la app sola.
            registraToque()
            return false
        }
        // Con "al instante" configurado, en primer plano se toma un minuto:
        // cerrar la boveda mientras la miras seria absurdo.
        val minutos = gestor.prefs.minutosAutobloqueo.coerceAtLeast(1)
        return SystemClock.elapsedRealtime() - ultimoToque >= minutos * 60_000L
    }

    /**
     * Abrir el selector de archivos del sistema manda la app a segundo plano, y
     * el cierre automatico la bloquearia a media exportacion. Esto concede una
     * excepcion corta y acotada en el tiempo: si el usuario abandona el selector
     * y no vuelve, el permiso caduca solo y el candado vuelve a la normalidad.
     */
    fun permiteSalidaBreve(segundos: Int = 180) {
        permisoSalidaHasta = SystemClock.elapsedRealtime() + segundos * 1000L
    }

    val salidaPermitida: Boolean
        get() = SystemClock.elapsedRealtime() < permisoSalidaHasta

    override fun onCreate() {
        super.onCreate()
        gestor = GestorBoveda(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStop(owner: LifecycleOwner) {
                // Irse a segundo plano con una contrasena en el portapapeles es
                // justo el momento en que otra app podria leerla.
                Portapapeles.cancelaPendiente()
                Portapapeles.limpiaTodo(this@AplicacionBoveda)

                if (salidaPermitida) {
                    // Salida esperada: el selector de archivos. No cuenta.
                    salidaEn = 0L
                    return
                }

                salidaEn = SystemClock.elapsedRealtime()
                if (gestor.prefs.minutosAutobloqueo == 0) gestor.bloquea()
            }

            override fun onStart(owner: LifecycleOwner) {
                permisoSalidaHasta = 0L
                if (salidaEn == 0L) return
                val limite = gestor.prefs.minutosAutobloqueo * 60_000L
                if (SystemClock.elapsedRealtime() - salidaEn >= limite) gestor.bloquea()
                salidaEn = 0L
            }
        })
    }

    companion object {
        fun de(contexto: Context): AplicacionBoveda =
            contexto.applicationContext as AplicacionBoveda
    }
}
