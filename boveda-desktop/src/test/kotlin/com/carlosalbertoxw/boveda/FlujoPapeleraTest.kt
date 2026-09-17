package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.BaseBoveda
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.Papelera
import com.carlosalbertoxw.boveda.seguridad.Sobre
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/**
 * La papelera y el historial, contra la base cifrada de verdad.
 *
 * Las dos cosas viven de que las credenciales y el historial se muevan juntos,
 * y eso solo se puede comprobar con transacciones que de verdad escriben.
 */
class FlujoPapeleraTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val frase = Sobre.aleatorio(32)
    private lateinit var base: BaseBoveda
    private val dia = TimeUnit.DAYS.toMillis(1)

    @Before
    fun arranca() {
        base = BaseBoveda.abre(carpeta.root.toPath(), frase.copyOf())
    }

    /**
     * Al terminar cada prueba se reabre la base desde el archivo y se exige que
     * diga exactamente lo mismo que habia en memoria: lo comprobado no vale si
     * no llego al disco.
     */
    @After
    fun termina() {
        val enMemoria = base.contenido.value
        base.cierra()
        val releida = BaseBoveda.abre(carpeta.root.toPath(), frase.copyOf())
        try {
            assertEquals(enMemoria, releida.contenido.value)
        } finally {
            releida.cierra()
        }
    }

    private fun dao() = base.credencialDao()

    private suspend fun nueva(titulo: String = "Banco", contrasena: String = "melon-42") =
        base.credencialDao().inserta(Credencial(titulo = titulo, usuario = "carlos", contrasena = contrasena))

    // --- Papelera ---------------------------------------------------------

    @Test
    fun borrar_saca_del_listado_pero_no_de_la_boveda() = runBlocking {
        val id = nueva()

        dao().mandaAPapelera(id, System.currentTimeMillis())

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

        assertEquals(listOf("Reciente"), dao().listaConPapelera().map { it.titulo })
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
        assertEquals("Banco de siempre", dao().listaCompleta().single().titulo)
    }

    @Test
    fun volver_a_una_contrasena_anterior_mueve_la_marca_sin_duplicar() = runBlocking {
        val id = nueva()
        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))
        val anterior = dao().historialDe(id).first().single { it.contrasena == "melon-42" }

        dao().usaDelHistorial(anterior, System.currentTimeMillis())

        assertEquals("melon-42", dao().listaCompleta().single().contrasena)
        val historial = dao().historialDe(id).first()
        assertEquals(2, historial.size)
        assertEquals(1, historial.count { it.actual })
        assertEquals("melon-42", historial.first { it.actual }.contrasena)
    }

    @Test
    fun la_contrasena_actual_no_se_puede_borrar_del_historial() = runBlocking {
        val id = nueva()
        val actual = dao().historialDe(id).first().single()

        val borradas = dao().borraDelHistorial(actual.id)

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
        assertEquals("sandia-7", dao().listaCompleta().single().contrasena)
        assertTrue(historial.single().actual)
    }

    @Test
    fun lo_borrado_conserva_su_historial_mientras_dura_el_plazo() = runBlocking {
        val id = nueva()
        dao().actualiza(dao().listaCompleta().single().copy(contrasena = "sandia-7"))

        dao().mandaAPapelera(id, System.currentTimeMillis())

        assertEquals(2, dao().historialDe(id).first().size)
    }

    @Test
    fun los_ids_no_se_reusan_tras_borrar() = runBlocking {
        val primera = nueva(titulo = "Primera")
        dao().mandaAPapelera(primera, System.currentTimeMillis())
        dao().vaciaPapelera()

        val segunda = nueva(titulo = "Segunda")

        // Una pantalla que se quedo con el id viejo no puede acabar mostrando
        // otra entrada distinta.
        assertTrue(segunda > primera)
    }
}
