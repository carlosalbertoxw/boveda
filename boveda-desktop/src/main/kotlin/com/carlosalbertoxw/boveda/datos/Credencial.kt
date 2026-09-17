package com.carlosalbertoxw.boveda.datos

/**
 * Una entrada de la boveda.
 *
 * Los campos van en claro dentro de la estructura porque la estructura entera
 * vive en un archivo cifrado con AES-256-GCM ([BaseBoveda]): sin la frase, el
 * archivo es ruido. Cifrar ademas campo por campo no agregaria nada util aqui y
 * si romperia la busqueda.
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
     * salir en el listado. Una contrasena borrada por error sigue siendo una
     * contrasena —merece el mismo cifrado, el mismo respaldo y el mismo
     * historial que las demas—.
     *
     * La fecha no es decorativa: es la que cuenta los treinta dias que faltan
     * para el borrado definitivo. Ver [Papelera].
     */
    val eliminadoEn: Long? = null
) {
    val enPapelera: Boolean get() = eliminadoEn != null
}
