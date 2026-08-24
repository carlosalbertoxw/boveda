package com.carlosalbertoxw.boveda.datos

import java.util.concurrent.TimeUnit

/**
 * Cuanto dura lo borrado antes de dejar de existir.
 *
 * Va aparte y sin tocar la base, igual que [GestorRespaldo.Fusion]: es la unica
 * regla de la papelera con criterio propio —cuando algo ya cumplio su plazo— y
 * asi se prueba sola, sin levantar SQLCipher ni esperar treinta dias.
 *
 * Los treinta dias son el equilibrio de siempre: suficientes para que alguien
 * note el borrado al volver de un viaje, y no tantos como para que la papelera
 * se convierta en un segundo listado donde las contrasenas viejas se acumulan
 * sin que nadie las mire.
 */
object Papelera {

    const val DIAS_RETENCION = 30

    private val MILIS_RETENCION = TimeUnit.DAYS.toMillis(DIAS_RETENCION.toLong())

    /**
     * La marca de tiempo por debajo de la cual todo lo borrado ya vencio.
     * Lo que se mando a la papelera antes de este instante ya no se recupera.
     */
    fun limite(ahora: Long): Long = ahora - MILIS_RETENCION

    /**
     * Dias que le quedan a una entrada de la papelera, redondeando hacia
     * arriba: mientras quede un solo minuto se muestra "1 dia", que es lo
     * honesto —queda menos de un dia, pero todavia se puede recuperar—. Cero
     * significa que ya vencio y desaparece en la proxima apertura.
     */
    fun diasRestantes(eliminadoEn: Long, ahora: Long): Int {
        val restante = eliminadoEn + MILIS_RETENCION - ahora
        if (restante <= 0) return 0
        val dia = TimeUnit.DAYS.toMillis(1)
        return ((restante + dia - 1) / dia).toInt()
    }
}
