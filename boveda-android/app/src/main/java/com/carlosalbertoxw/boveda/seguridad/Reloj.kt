package com.carlosalbertoxw.boveda.seguridad

import android.os.SystemClock

/**
 * Los dos relojes del sistema, detras de una interfaz.
 *
 * Ninguno de los dos sirve solo para medir un castigo que debe sobrevivir a
 * que la app se cierre:
 *
 *  - El monotonico (`elapsedRealtime`) no se mueve aunque el usuario cambie la
 *    fecha del telefono, pero **se reinicia a cero al apagar el equipo**. Un
 *    vencimiento guardado con el telefono encendido tres dias se convierte,
 *    tras reiniciar, en tres dias de espera.
 *  - El de pared (`currentTimeMillis`) sobrevive al reinicio, pero atrasar la
 *    fecha a mano lo mueve.
 *
 * Por eso el castigo se guarda medido con los dos y se cruzan al leerlos: ver
 * [com.carlosalbertoxw.boveda.seguridad.GestorBoveda.segundosDeEspera].
 *
 * Que sea una interfaz y no una llamada directa es lo que permite probar el
 * caso del reinicio sin reiniciar nada.
 */
interface Reloj {

    /** Milisegundos desde el ultimo arranque del sistema. */
    fun monotonico(): Long

    /** Milisegundos desde la epoca, segun la fecha del telefono. */
    fun pared(): Long

    object Sistema : Reloj {
        override fun monotonico(): Long = SystemClock.elapsedRealtime()
        override fun pared(): Long = System.currentTimeMillis()
    }
}
