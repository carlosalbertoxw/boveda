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
    val actualizadoEn: Long = System.currentTimeMillis()
)
