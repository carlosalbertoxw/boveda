package com.carlosalbertoxw.boveda.datos

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * La tabla `historial_contrasenas` tal y como la ve Room.
 *
 * Igual que [CredencialFila]: el resto de la app usa [HistorialContrasena], el
 * modelo del nucleo, y esta clase solo describe la tabla y sirve para insertar.
 * Sus campos tienen que coincidir uno a uno con los de [HistorialContrasena].
 */
@Entity(
    tableName = "historial_contrasenas",
    foreignKeys = [
        ForeignKey(
            entity = CredencialFila::class,
            parentColumns = ["id"],
            childColumns = ["credencialId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("credencialId")]
)
data class HistorialFila(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val credencialId: Long,
    val contrasena: String,
    val creadoEn: Long,
    val actual: Boolean
)

fun HistorialContrasena.aFila() = HistorialFila(
    id = id,
    credencialId = credencialId,
    contrasena = contrasena,
    creadoEn = creadoEn,
    actual = actual
)
