package com.carlosalbertoxw.boveda

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.boveda.datos.BovedaDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La migracion de la base, contra SQLCipher de verdad.
 *
 * Es la unica parte de esta app capaz de perder datos del usuario en silencio.
 * No hay copia en ningun servidor y la politica del proyecto prohibe la
 * migracion destructiva, asi que si una migracion sale mal el usuario se queda
 * con el archivo intacto pero inalcanzable, o —peor— con una base que abre
 * habiendo tirado algo por el camino. Por eso se prueba abriendo una base
 * escrita con el esquema viejo y comprobando fila por fila lo que quedo.
 *
 * Se ejecutan con:
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigracionTest {

    private companion object {
        const val BASE = "migracion-de-prueba.db"

        /**
         * La frase de una boveda de mentira. En la app real es aleatoria y sale
         * de la envoltura; aqui solo hace falta que sea la misma al crear la
         * base y al volver a abrirla.
         */
        val FRASE = "frase de prueba para la migracion".toByteArray()
    }

    /**
     * `clearPassphrase = false` es imprescindible: por omision SQLCipher llena
     * de ceros el arreglo en cuanto abre la base —que es justo lo que se quiere
     * en produccion—, y aqui la misma fabrica tiene que abrirla dos veces, una
     * para crearla en la version 1 y otra para migrarla.
     */
    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BovedaDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(FRASE, null, false)
    )

    init {
        System.loadLibrary("sqlcipher")
    }

    @Test
    fun de_la_version_1_a_la_2_no_se_pierde_ninguna_entrada() {
        ayudante.createDatabase(BASE, 1).use { vieja ->
            vieja.insert("credenciales", 0, fila("Banco", "carlos", "melon-42", 1_000L))
            vieja.insert("credenciales", 0, fila("Correo", "yo", "sandia-7", 2_000L))
        }

        // runMigrationsAndValidate compara el esquema resultante con el 2.json
        // exportado: si la migracion escrita a mano y la entidad no coinciden
        // —una columna de mas, un indice de menos—, falla aqui y no en el
        // telefono de alguien.
        val nueva = ayudante.runMigrationsAndValidate(BASE, 2, true)

        nueva.query("SELECT titulo, contrasena, eliminadoEn FROM credenciales ORDER BY titulo")
            .use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("Banco", cursor.getString(0))
                assertEquals("melon-42", cursor.getString(1))
                // Columna nueva y nula: nada de lo que ya existia esta borrado.
                assertTrue(cursor.isNull(2))
            }
    }

    @Test
    fun de_la_version_1_a_la_2_cada_entrada_estrena_historial() {
        ayudante.createDatabase(BASE, 1).use { vieja ->
            vieja.insert("credenciales", 0, fila("Banco", "carlos", "melon-42", 1_000L))
        }

        val nueva = ayudante.runMigrationsAndValidate(BASE, 2, true)

        // Sin esto, la contrasena que la entrada usa hoy —la que mas falta hace
        // tener anotada— seria la unica que nunca estuvo en el historial.
        nueva.query(
            "SELECT contrasena, creadoEn, actual FROM historial_contrasenas"
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("melon-42", cursor.getString(0))
            assertEquals(1_000L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
        }
    }

    private fun fila(titulo: String, usuario: String, contrasena: String, cuando: Long) =
        ContentValues().apply {
            put("titulo", titulo)
            put("usuario", usuario)
            put("contrasena", contrasena)
            put("sitio", "")
            put("notas", "")
            put("creadoEn", cuando)
            put("actualizadoEn", cuando)
        }
}
