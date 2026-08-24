package com.carlosalbertoxw.boveda.datos

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una entrada de la boveda.
 *
 * Los campos van en claro dentro de la tabla porque la tabla entera vive en un
 * archivo cifrado con SQLCipher (AES-256): sin la frase, el .db es ruido. Cifrar
 * ademas campo por campo no agregaria nada util aqui y si romperia la busqueda.
 */
@Entity(tableName = "credenciales")
data class Credencial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
     * Es un borrado logico: la fila se queda donde estaba y solo deja de salir
     * en el listado. Se eligio asi y no una tabla aparte porque una contrasena
     * borrada por error sigue siendo una contrasena —merece el mismo cifrado,
     * el mismo respaldo y el mismo historial que las demas—, y moverla de tabla
     * obligaria a duplicar todo eso para el unico caso en que mas duele
     * equivocarse.
     *
     * La fecha no es decorativa: es la que cuenta los treinta dias que faltan
     * para el borrado definitivo. Ver [Papelera].
     */
    val eliminadoEn: Long? = null
) {
    val enPapelera: Boolean get() = eliminadoEn != null
}
