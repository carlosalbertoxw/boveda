package com.carlosalbertoxw.boveda.datos

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * La tabla `credenciales` tal y como la ve Room.
 *
 * El resto de la app trabaja con [Credencial], el modelo del nucleo compartido
 * con escritorio, que no sabe nada de Room. Esta clase solo existe para que
 * Room sepa como es la tabla: las consultas devuelven [Credencial] directamente
 * (Room la construye con las columnas del mismo nombre) y solo insertar y
 * actualizar pasan por aqui.
 *
 * Los campos tienen que ser exactamente los de [Credencial], con los mismos
 * nombres y tipos: de ellos sale el esquema que se exporta a app/schemas y que
 * valida Room al abrir. Cambiar uno es cambiar la base, y eso pide subir la
 * version y escribir la migracion (ver [BovedaDatabase]).
 *
 * Los campos van en claro dentro de la tabla porque la tabla entera vive en un
 * archivo cifrado con SQLCipher (AES-256): sin la frase, el .db es ruido.
 */
@Entity(tableName = "credenciales")
data class CredencialFila(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val titulo: String,
    val usuario: String,
    val contrasena: String,
    val sitio: String,
    val notas: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminadoEn: Long?
)

fun Credencial.aFila() = CredencialFila(
    id = id,
    titulo = titulo,
    usuario = usuario,
    contrasena = contrasena,
    sitio = sitio,
    notas = notas,
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
    eliminadoEn = eliminadoEn
)
