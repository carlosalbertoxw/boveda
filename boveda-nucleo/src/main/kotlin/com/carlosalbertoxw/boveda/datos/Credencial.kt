package com.carlosalbertoxw.boveda.datos

/**
 * Una entrada de la boveda.
 *
 * Es el mismo modelo en las dos apps, sin nada de la forma en que cada una lo
 * guarda: en Android lo lee y escribe Room sobre SQLCipher, en escritorio va
 * dentro de un archivo AES-256-GCM. Los campos van en claro dentro de ese
 * almacen porque el almacen entero esta cifrado: sin la frase es ruido, y
 * cifrar ademas campo por campo no agregaria nada util y si romperia la
 * busqueda.
 */
data class Credencial(
    val id: Long = 0,
    val titulo: String,
    val usuario: String,
    val contrasena: String,
    val sitio: String = "",
    val notas: String = "",
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis(),
    /**
     * Cuando se mando a la papelera, o null si esta activa.
     *
     * Es un borrado logico: la entrada se queda donde estaba y solo deja de
     * salir en el listado. Se eligio asi y no un almacen aparte porque una
     * contrasena borrada por error sigue siendo una contrasena —merece el
     * mismo cifrado, el mismo respaldo y el mismo historial que las demas—, y
     * moverla obligaria a duplicar todo eso para el unico caso en que mas
     * duele equivocarse.
     *
     * La fecha no es decorativa: es la que cuenta los treinta dias que faltan
     * para el borrado definitivo. Ver [Papelera].
     */
    val eliminadoEn: Long? = null
)

/**
 * Fuera de la clase a proposito: Room construye [Credencial] directamente desde
 * las columnas de la tabla, y una propiedad sin columna detras dentro de la
 * clase le haria buscar una que no existe.
 */
val Credencial.enPapelera: Boolean get() = eliminadoEn != null
