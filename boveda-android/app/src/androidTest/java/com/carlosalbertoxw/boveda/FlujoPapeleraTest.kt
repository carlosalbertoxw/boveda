package com.carlosalbertoxw.boveda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.boveda.datos.BovedaDatabase
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.Papelera
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * La papelera y el historial, contra la base de verdad.
 *
 * Las dos cosas viven de que dos tablas se muevan juntas, y eso solo se puede
 * comprobar donde hay transacciones de verdad: en la JVM no hay SQLCipher, y
 * sin base no hay nada que quede a medias.
 *
 * Se ejecutan con:
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FlujoPapeleraTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private lateinit var gestor: GestorBoveda

    private val maestra = "correcto caballo bateria grapa"
    private val dia = TimeUnit.DAYS.toMillis(1)

    @Before
    fun arranca() = runBlocking {
        limpia()
        gestor = GestorBoveda(contexto)
        gestor.crea(maestra.toCharArray())
        Unit
    }

    @After
    fun termina() {
        runCatching { gestor.bloquea() }
        limpia()
    }

    private fun limpia() {
        contexto.getSharedPreferences("boveda_config", Context.MODE_PRIVATE)
            .edit().clear().commit()
        BovedaDatabase.borraArchivos(contexto)
    }

    private fun dao() = (gestor.estado.value as EstadoBoveda.Abierta).db.credencialDao()

    private suspend fun nueva(titulo: String = "Banco", contrasena: String = "melon-42") =
        dao().inserta(Credencial(titulo = titulo, usuario = "carlos", contrasena = contrasena))

    // --- Papelera ---------------------------------------------------------

    @Test
    fun borrar_saca_del_listado_pero_no_de_la_boveda() = runBlocking {
        val id = nueva()

        dao().mandaAPapelera(id, System.currentTimeMillis())

        // Fuera del listado y dentro de la papelera: lo importante es que la
        // fila siga existiendo, porque es lo que permite recuperarla.
        assertEquals(0, dao().listaCompleta().size)
        assertEquals(1, dao().papelera().first().size)
        assertEquals(1, dao().listaConPapelera().size)
        assertNotNull(dao().listaConPapelera().single().eliminadoEn)
    }

    @Test
    fun restaurar_devuelve_la_entrada_intacta() = runBlocking {
        val id = nueva()
        dao().mandaAPapelera(id, System.currentTimeMillis())

        dao().restaura(id)

        val vuelta = dao().listaCompleta().single()
        assertEquals("melon-42", vuelta.contrasena)
        assertNull(vuelta.eliminadoEn)
        assertTrue(dao().papelera().first().isEmpty())
        // Y su historial vuelve con ella: nunca dejo de estar ahi.
        assertEquals(1, dao().historialDe(id).first().size)
    }

    @Test
    fun borrar_de_verdad_se_lleva_tambien_el_historial() = runBlocking {
        val id = nueva()
        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))
        assertEquals(2, dao().historialDe(id).first().size)
        dao().mandaAPapelera(id, System.currentTimeMillis())

        dao().borraDeVerdad(id)

        assertEquals(0, dao().listaConPapelera().size)
        // Dejar contrasenas huerfanas en la base seria justo lo contrario de lo
        // que promete el boton de "borrar para siempre".
        assertTrue(dao().historialCompleto().isEmpty())
    }

    @Test
    fun la_purga_respeta_el_plazo() = runBlocking {
        val ahora = System.currentTimeMillis()
        val vieja = nueva(titulo = "Vieja")
        val reciente = nueva(titulo = "Reciente")
        dao().mandaAPapelera(vieja, ahora - 31 * dia)
        dao().mandaAPapelera(reciente, ahora - 2 * dia)

        dao().purga(Papelera.limite(ahora))

        val quedan = dao().listaConPapelera()
        assertEquals(listOf("Reciente"), quedan.map { it.titulo })
        // Y el historial de la vencida se fue con ella.
        assertEquals(1, dao().historialCompleto().size)
    }

    @Test
    fun vaciar_la_papelera_no_toca_lo_activo() = runBlocking {
        val activa = nueva(titulo = "Activa")
        val borrada = nueva(titulo = "Borrada")
        dao().mandaAPapelera(borrada, System.currentTimeMillis())

        dao().vaciaPapelera()

        assertEquals(listOf("Activa"), dao().listaCompleta().map { it.titulo })
        assertEquals(1, dao().historialDe(activa).first().size)
    }

    // --- Historial --------------------------------------------------------

    @Test
    fun una_entrada_nueva_ya_trae_historial() = runBlocking {
        val id = nueva()

        val historial = dao().historialDe(id).first()

        // Si el historial empezara al primer cambio, la contrasena original
        // seria la unica que nunca estaria anotada.
        assertEquals(1, historial.size)
        assertEquals("melon-42", historial.single().contrasena)
        assertTrue(historial.single().actual)
    }

    @Test
    fun cambiar_la_contrasena_anota_la_nueva_y_mueve_la_marca() = runBlocking {
        val id = nueva()

        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))

        val historial = dao().historialDe(id).first()
        assertEquals(2, historial.size)
        assertEquals(1, historial.count { it.actual })
        assertEquals("sandia-7", historial.first { it.actual }.contrasena)
        assertTrue(historial.any { it.contrasena == "melon-42" && !it.actual })
    }

    @Test
    fun editar_el_titulo_no_ensucia_el_historial() = runBlocking {
        val id = nueva()

        dao().actualiza(dao().listaCompleta().single().copy(titulo = "Banco de siempre"))

        assertEquals(1, dao().historialDe(id).first().size)
    }

    @Test
    fun volver_a_una_contrasena_anterior_mueve_la_marca_sin_duplicar() = runBlocking {
        val id = nueva()
        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))
        val anterior = dao().historialDe(id).first().single { it.contrasena == "melon-42" }

        dao().usaDelHistorial(anterior, System.currentTimeMillis())

        // La entrada vuelve a la de antes...
        assertEquals("melon-42", dao().listaCompleta().single().contrasena)
        val historial = dao().historialDe(id).first()
        // ...la marca se mueve con ella, y no aparece una tercera fila.
        assertEquals(2, historial.size)
        assertEquals(1, historial.count { it.actual })
        assertEquals("melon-42", historial.first { it.actual }.contrasena)
    }

    @Test
    fun la_contrasena_actual_no_se_puede_borrar_del_historial() = runBlocking {
        val id = nueva()
        val actual = dao().historialDe(id).first().single()

        val borradas = dao().borraDelHistorial(actual.id)

        // Dejaria a la entrada sin la fila que le corresponde, asi que el SQL
        // no la toca y el modelo lo cuenta por el numero de filas que cayeron.
        assertEquals(0, borradas)
        assertEquals(1, dao().historialDe(id).first().size)
    }

    @Test
    fun una_contrasena_vieja_si_se_borra_del_historial() = runBlocking {
        val id = nueva()
        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))
        val vieja = dao().historialDe(id).first().single { it.contrasena == "melon-42" }

        assertEquals(1, dao().borraDelHistorial(vieja.id))

        val historial = dao().historialDe(id).first()
        assertEquals(1, historial.size)
        assertFalse(historial.any { it.contrasena == "melon-42" })
        // La entrada sigue con la suya y con su marca intacta.
        assertEquals("sandia-7", dao().listaCompleta().single().contrasena)
        assertTrue(historial.single().actual)
    }

    @Test
    fun lo_borrado_conserva_su_historial_mientras_dura_el_plazo() = runBlocking {
        val id = nueva()
        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))

        dao().mandaAPapelera(id, System.currentTimeMillis())

        // Recuperar una entrada sin sus contrasenas viejas seria recuperarla a
        // medias, y lo viejo es justo lo que se busca al deshacer un borrado.
        assertEquals(2, dao().historialDe(id).first().size)
    }
}
