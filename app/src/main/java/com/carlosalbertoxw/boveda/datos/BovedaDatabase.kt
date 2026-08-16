package com.carlosalbertoxw.boveda.datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [Credencial::class], version = 1, exportSchema = true)
abstract class BovedaDatabase : RoomDatabase() {

    abstract fun credencialDao(): CredencialDao

    companion object {
        const val NOMBRE = "boveda.db"

        /**
         * Politica de migracion: **siempre explicita, nunca destructiva.**
         *
         * En una app normal, `fallbackToDestructiveMigration` es una salida
         * comoda: se pierde una cache y se vuelve a bajar. Aqui borraria la
         * unica copia que existe de las contrasenas del usuario, que no estan
         * en ningun servidor ni se pueden volver a pedir. Por eso no se usa, y
         * por eso no debe usarse nunca aunque una build falle al abrir.
         *
         * Al cambiar el esquema hay que subir la version en @Database, escribir
         * la Migration correspondiente y anadirla a esta lista. El esquema de
         * cada version se exporta a app/schemas (ver ksp room.schemaLocation) y
         * se versiona: es lo que permite escribir la migracion y probarla con
         * MigrationTestHelper. Si se sube la version sin migracion, Room lanza
         * IllegalStateException al abrir y el usuario ve "no se pudo abrir la
         * boveda" con sus datos intactos pero inalcanzables.
         */
        private val MIGRACIONES: Array<Migration> = emptyArray()

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
                .addMigrations(*MIGRACIONES)
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
