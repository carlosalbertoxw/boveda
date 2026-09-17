package com.carlosalbertoxw.boveda

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.boveda.datos.BovedaDatabase
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exportar y volver a importar, con la base de verdad.
 *
 * El respaldo es la unica salida que tienen los datos, y la unica forma de
 * recuperarlos si el telefono se pierde. Si esta ida y vuelta se rompe, no se
 * nota hasta el dia en que hace falta, que es el peor dia para enterarse.
 */
@RunWith(AndroidJUnit4::class)
class FlujoRespaldoTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private lateinit var gestor: GestorBoveda
    private lateinit var respaldo: GestorRespaldo
    private lateinit var archivo: File

    private val maestra = "correcto caballo bateria grapa"
    private val claveArchivo = "otra distinta para el archivo"

    private val muestra = listOf(
        Credencial(titulo = "Banco", usuario = "carlos", contrasena = "melon-42"),
        Credencial(titulo = "Correo", usuario = "yo@ejemplo.mx", contrasena = "sandia-7")
    )

    @Before
    fun arranca() = runBlocking {
        limpia()
        gestor = GestorBoveda(contexto)
        respaldo = GestorRespaldo(contexto, gestor)
        archivo = File(contexto.cacheDir, "respaldo-de-prueba.json")
        archivo.delete()

        gestor.crea(maestra.toCharArray())
        muestra.forEach { dao().inserta(it) }
    }

    @After
    fun termina() {
        runCatching { gestor.bloquea() }
        archivo.delete()
        limpia()
    }

    private fun limpia() {
        contexto.getSharedPreferences("boveda_config", Context.MODE_PRIVATE)
            .edit().clear().commit()
        BovedaDatabase.borraArchivos(contexto)
    }

    private fun dao() = (gestor.estado.value as EstadoBoveda.Abierta).db.credencialDao()

    private fun destino(): Uri = Uri.fromFile(archivo)

    private suspend fun exporta() =
        respaldo.exporta(destino(), maestra.toCharArray(), claveArchivo.toCharArray())

    @Test
    fun lo_exportado_vuelve_completo_tras_vaciar_la_boveda() = runBlocking {
        assertEquals(GestorRespaldo.Exportacion.Ok(2), exporta())
        assertTrue(archivo.length() > 0)

        // Se simula el telefono perdido: la boveda queda vacia y solo hay el
        // archivo.
        dao().vacia()
        assertEquals(0, dao().listaCompleta().size)

        val resultado = respaldo.importa(destino(), claveArchivo.toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(2, 0), resultado)
        val recuperadas = dao().listaCompleta().associateBy { it.titulo }
        assertEquals("melon-42", recuperadas.getValue("Banco").contrasena)
        assertEquals("yo@ejemplo.mx", recuperadas.getValue("Correo").usuario)
    }

    @Test
    fun importar_dos_veces_no_duplica_la_boveda() = runBlocking {
        exporta()

        respaldo.importa(destino(), claveArchivo.toCharArray(), reemplazar = false)
        val segunda = respaldo.importa(destino(), claveArchivo.toCharArray(), reemplazar = false)

        // Importar de nuevo por si acaso es lo que hace cualquiera que no esta
        // seguro de si entro la primera vez. No debe costarle una boveda doble.
        assertEquals(GestorRespaldo.Importacion.Ok(0, 2), segunda)
        assertEquals(2, dao().listaCompleta().size)
    }

    @Test
    fun exportar_con_la_maestra_equivocada_no_escribe_nada() = runBlocking {
        val resultado = respaldo.exporta(
            destino(),
            "esta no es la maestra".toCharArray(),
            claveArchivo.toCharArray()
        )

        // Sacar todas las contrasenas de golpe es la operacion mas peligrosa de
        // la app: no basta con que la boveda este abierta.
        assertEquals(GestorRespaldo.Exportacion.MaestraIncorrecta, resultado)
        assertFalse(archivo.exists() && archivo.length() > 0)
    }

    @Test
    fun con_la_clave_del_archivo_equivocada_no_se_importa() = runBlocking {
        exporta()
        dao().vacia()

        val resultado = respaldo.importa(
            destino(),
            "clave que no es".toCharArray(),
            reemplazar = false
        )

        assertEquals(GestorRespaldo.Importacion.ContrasenaIncorrecta, resultado)
        assertEquals(0, dao().listaCompleta().size)
    }

    @Test
    fun el_respaldo_devuelve_la_papelera_y_el_historial() = runBlocking {
        // Una entrada que cambio de contrasena y otra que esta en la papelera:
        // las dos cosas que la version 2 del formato existe para no perder.
        val banco = dao().listaCompleta().first { it.titulo == "Banco" }
        dao().actualiza(banco.copy(contrasena = "melon-43"))
        val correo = dao().listaCompleta().first { it.titulo == "Correo" }
        dao().mandaAPapelera(correo.id, 1_700_000_000_000)

        assertEquals(GestorRespaldo.Exportacion.Ok(2), exporta())
        dao().vacia()

        val resultado = respaldo.importa(destino(), claveArchivo.toCharArray(), reemplazar = false)
        assertEquals(GestorRespaldo.Importacion.Ok(2, 0), resultado)

        val vueltas = dao().listaConPapelera().associateBy { it.titulo }
        // Lo que estaba borrado vuelve borrado, con su fecha: restaurar un
        // respaldo no puede deshacer un borrado que el usuario si quiso.
        assertEquals(1_700_000_000_000, vueltas.getValue("Correo").eliminadoEn)
        assertNull(vueltas.getValue("Banco").eliminadoEn)

        val historial = dao().historialDe(vueltas.getValue("Banco").id).first()
        assertEquals(2, historial.size)
        assertEquals("melon-43", historial.first { it.actual }.contrasena)
        assertTrue(historial.any { it.contrasena == "melon-42" && !it.actual })
    }

    @Test
    fun importar_un_respaldo_no_revive_lo_que_esta_en_la_papelera() = runBlocking {
        exporta()
        val banco = dao().listaCompleta().first { it.titulo == "Banco" }
        dao().mandaAPapelera(banco.id, System.currentTimeMillis())

        val resultado = respaldo.importa(destino(), claveArchivo.toCharArray(), reemplazar = false)

        // El archivo trae "Banco" activo, pero aqui ya se borro despues de
        // hacerlo. Se omite: si entrara, quedarian dos —una activa y otra en la
        // papelera— y el borrado se habria deshecho sin decir nada.
        assertEquals(GestorRespaldo.Importacion.Ok(0, 2), resultado)
        assertEquals(2, dao().listaConPapelera().size)
        assertEquals(listOf("Correo"), dao().listaCompleta().map { it.titulo })
    }

    @Test
    fun un_cambio_de_contrasena_de_otro_equipo_actualiza_la_cuenta_sin_duplicarla() = runBlocking {
        // En el telefono solo cabe una boveda, asi que el "otro equipo" se
        // simula con dos respaldos: uno de antes del cambio y otro de despues.
        val antes = File(contexto.cacheDir, "respaldo-antes.json").apply { delete() }
        respaldo.exporta(Uri.fromFile(antes), maestra.toCharArray(), claveArchivo.toCharArray())

        val banco = dao().listaCompleta().first { it.titulo == "Banco" }
        dao().actualiza(banco.copy(contrasena = "melon-2027", actualizadoEn = banco.actualizadoEn + 60_000))
        assertEquals(GestorRespaldo.Exportacion.Ok(2), exporta())

        // Este equipo vuelve a como estaba antes del cambio...
        respaldo.importa(Uri.fromFile(antes), claveArchivo.toCharArray(), reemplazar = true)
        antes.delete()

        // ...y recibe el respaldo del otro, que trae la contrasena nueva.
        val resultado = respaldo.importa(destino(), claveArchivo.toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(agregadas = 0, omitidas = 1, actualizadas = 1), resultado)
        val cuentas = dao().listaCompleta().filter { it.titulo == "Banco" }
        assertEquals(1, cuentas.size)
        assertEquals("melon-2027", cuentas.single().contrasena)
        val historial = dao().historialDe(cuentas.single().id).first()
        assertEquals("melon-2027", historial.single { it.actual }.contrasena)
        assertTrue(historial.any { it.contrasena == "melon-42" })
    }

    @Test
    fun el_archivo_exportado_no_lleva_nada_en_claro() = runBlocking {
        exporta()

        val texto = archivo.readText()

        assertFalse(texto.contains("melon-42"))
        assertFalse(texto.contains("yo@ejemplo.mx"))
        assertFalse(texto.contains("Banco"))
        // La cabecera si va legible, a proposito: dentro de tres anos hay que
        // poder abrir el archivo y saber que es.
        assertTrue(texto.contains("boveda-respaldo"))
    }
}
