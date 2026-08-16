package com.carlosalbertoxw.boveda.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [Credencial::class], version = 1, exportSchema = true)
abstract class BovedaDatabase : RoomDatabase() {

    abstract fun credencialDao(): CredencialDao

    companion object {
        const val NOMBRE = "boveda.db"

        /**
         * A diferencia de una app normal, esta base NO se abre al arrancar: se
         * construye en el momento del desbloqueo, con la frase que acaba de
         * salir de la envoltura, y se cierra al bloquear. Mientras la boveda
         * este cerrada no existe ninguna conexion abierta, asi que no hay un
         * handle vivo del que sacar datos.
         *
         * No hay camino sin cifrar ni fallback: si SQLCipher no carga, la app no
         * abre. Es preferible a guardar contrasenas en claro sin avisar.
         *
         * [frase] se recibe como ByteArray y no como String a proposito: un
         * String es inmutable y no se puede poner en ceros, asi que el secreto
         * mas importante de la app se quedaria en el heap hasta que pasara el
         * recolector. SupportOpenHelperFactory llena de ceros el arreglo que
         * recibe en cuanto abre la base, asi que este es el ultimo sitio donde
         * la frase existe, y dura lo que tarda la apertura. Quien llama debe
         * pasar una copia suya, no el original que quiera conservar.
         */
        fun abre(contexto: Context, frase: ByteArray): BovedaDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(
                contexto.applicationContext,
                BovedaDatabase::class.java,
                NOMBRE
            )
                .openHelperFactory(SupportOpenHelperFactory(frase))
                .build()
        }

        /** Borra el archivo y sus diarios. Se usa al restablecer la boveda. */
        fun borraArchivos(contexto: Context) {
            val app = contexto.applicationContext
            listOf(NOMBRE, "$NOMBRE-wal", "$NOMBRE-shm", "$NOMBRE-journal").forEach {
                app.getDatabasePath(it).delete()
            }
        }
    }
}
