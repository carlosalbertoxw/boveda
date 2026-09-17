package com.carlosalbertoxw.boveda.seguridad

/**
 * Los dos relojes del sistema, detras de una interfaz.
 *
 * Ninguno de los dos sirve solo para medir un castigo que debe sobrevivir a
 * que la app se cierre:
 *
 *  - El monotonico (`System.nanoTime`) no se mueve aunque el usuario cambie la
 *    fecha del equipo, pero **se reinicia al apagarlo**. En Windows, macOS y
 *    Linux la JVM lo toma del contador de arranque del sistema; la
 *    especificacion ni siquiera promete eso, solo que sea monotonico dentro de
 *    un mismo proceso, y el cruce de relojes lo absorbe igual.
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

    /** Milisegundos de un reloj que no retrocede, sin relacion con la fecha. */
    fun monotonico(): Long

    /** Milisegundos desde la epoca, segun la fecha del equipo. */
    fun pared(): Long

    object Sistema : Reloj {
        override fun monotonico(): Long = System.nanoTime() / 1_000_000L
        override fun pared(): Long = System.currentTimeMillis()
    }
}
