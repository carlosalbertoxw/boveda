package com.carlosalbertoxw.boveda

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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exportar y volver a importar, con la base de verdad.
 *
 * El respaldo es la unica salida que tienen los datos, y la unica forma de
 * recuperarlos si el equipo se pierde. Si esta ida y vuelta se rompe, no se
 * nota hasta el dia en que hace falta, que es el peor dia para enterarse.
 */
class FlujoRespaldoTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var gestor: GestorBoveda
    private lateinit var respaldo: GestorRespaldo
    private lateinit var archivo: Path

    private val maestra = "correcto caballo bateria grapa"
    private val claveArchivo = "otra distinta para el archivo"

    private val muestra = listOf(
        Credencial(titulo = "Banco", usuario = "carlos", contrasena = "melon-42"),
        Credencial(titulo = "Correo", usuario = "yo@ejemplo.mx", contrasena = "sandia-7")
    )

    @Before
    fun arranca() = runBlocking {
        gestor = GestorBoveda(carpeta.newFolder("boveda").toPath())
        respaldo = GestorRespaldo(gestor)
        archivo = carpeta.root.toPath().resolve("respaldo-de-prueba.json")

        gestor.crea(maestra.toCharArray())
        muestra.forEach { dao().inserta(it) }
    }

    @After
    fun termina() {
        runCatching { gestor.bloquea() }
    }

    private fun dao() = (gestor.estado.value as EstadoBoveda.Abierta).db.credencialDao()

    private suspend fun exporta() =
        respaldo.exporta(archivo, maestra.toCharArray(), claveArchivo.toCharArray())

    @Test
    fun lo_exportado_vuelve_completo_tras_vaciar_la_boveda() = runBlocking {
        assertEquals(GestorRespaldo.Exportacion.Ok(2), exporta())
        assertTrue(Files.size(archivo) > 0)

        dao().vacia()
        assertEquals(0, dao().listaCompleta().size)

        val resultado = respaldo.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(2, 0), resultado)
        val recuperadas = dao().listaCompleta().associateBy { it.titulo }
        assertEquals("melon-42", recuperadas.getValue("Banco").contrasena)
        assertEquals("yo@ejemplo.mx", recuperadas.getValue("Correo").usuario)
    }

    @Test
    fun importar_dos_veces_no_duplica_la_boveda() = runBlocking {
        exporta()

        respaldo.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)
        val segunda = respaldo.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(0, 2), segunda)
        assertEquals(2, dao().listaCompleta().size)
    }

    @Test
    fun reemplazar_deja_solo_lo_del_archivo() = runBlocking {
        exporta()
        dao().inserta(Credencial(titulo = "Solo aqui", usuario = "x", contrasena = "y"))

        val resultado = respaldo.importa(archivo, claveArchivo.toCharArray(), reemplazar = true)

        assertEquals(GestorRespaldo.Importacion.Ok(2, 0), resultado)
        assertEquals(listOf("Banco", "Correo"), dao().listaCompleta().map { it.titulo })
    }

    @Test
    fun exportar_con_la_maestra_equivocada_no_escribe_nada() = runBlocking {
        val resultado = respaldo.exporta(
            archivo,
            "esta no es la maestra".toCharArray(),
            claveArchivo.toCharArray()
        )

        assertEquals(GestorRespaldo.Exportacion.MaestraIncorrecta, resultado)
        assertFalse(Files.exists(archivo))
    }

    @Test
    fun con_la_clave_del_archivo_equivocada_no_se_importa() = runBlocking {
        exporta()
        dao().vacia()

        val resultado = respaldo.importa(archivo, "clave que no es".toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.ContrasenaIncorrecta, resultado)
        assertEquals(0, dao().listaCompleta().size)
    }

    @Test
    fun el_respaldo_devuelve_la_papelera_y_el_historial() = runBlocking {
        val banco = dao().listaCompleta().first { it.titulo == "Banco" }
        dao().actualiza(banco.copy(contrasena = "melon-43"))
        val correo = dao().listaCompleta().first { it.titulo == "Correo" }
        dao().mandaAPapelera(correo.id, 1_700_000_000_000)

        assertEquals(GestorRespaldo.Exportacion.Ok(2), exporta())
        dao().vacia()

        val resultado = respaldo.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)
        assertEquals(GestorRespaldo.Importacion.Ok(2, 0), resultado)

        val vueltas = dao().listaConPapelera().associateBy { it.titulo }
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

        val resultado = respaldo.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(0, 2), resultado)
        assertEquals(2, dao().listaConPapelera().size)
        assertEquals(listOf("Correo"), dao().listaCompleta().map { it.titulo })
    }

    @Test
    fun pasar_un_cambio_de_contrasena_entre_dos_equipos_no_duplica_la_cuenta() = runBlocking {
        // El "otro equipo": una boveda aparte que empieza con lo mismo.
        val otro = GestorBoveda(carpeta.newFolder("otro-equipo").toPath())
        val respaldoOtro = GestorRespaldo(otro)
        otro.crea("la maestra del otro equipo".toCharArray())
        exporta()
        respaldoOtro.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)
        val daoOtro = (otro.estado.value as EstadoBoveda.Abierta).db.credencialDao()

        // En este equipo se cambia la contrasena del banco, y se vuelve a exportar.
        val banco = dao().listaCompleta().first { it.titulo == "Banco" }
        dao().actualiza(banco.copy(contrasena = "melon-2027", actualizadoEn = banco.actualizadoEn + 60_000))
        Files.delete(archivo)
        exporta()

        val resultado = respaldoOtro.importa(archivo, claveArchivo.toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(agregadas = 0, omitidas = 1, actualizadas = 1), resultado)
        val cuentas = daoOtro.listaCompleta().filter { it.titulo == "Banco" }
        assertEquals(1, cuentas.size)
        assertEquals("melon-2027", cuentas.single().contrasena)
        val historial = daoOtro.historialDe(cuentas.single().id).first()
        assertEquals("melon-2027", historial.single { it.actual }.contrasena)
        assertTrue(historial.any { it.contrasena == "melon-42" })
        otro.bloquea()
    }

    @Test
    fun el_archivo_exportado_no_lleva_nada_en_claro() = runBlocking {
        exporta()

        val texto = Files.readString(archivo)

        assertFalse(texto.contains("melon-42"))
        assertFalse(texto.contains("yo@ejemplo.mx"))
        assertFalse(texto.contains("Banco"))
        assertTrue(texto.contains("boveda-respaldo"))
    }

    @Test
    fun el_respaldo_de_referencia_se_importa_en_una_boveda_real() = runBlocking {
        val referencia = carpeta.root.toPath().resolve("referencia.json")
        javaClass.getResourceAsStream("/respaldo-referencia-v2.json")!!.use { Files.copy(it, referencia) }
        dao().vacia()

        val resultado = respaldo.importa(referencia, "contraseña del respaldo 🔐".toCharArray(), reemplazar = false)

        assertEquals(GestorRespaldo.Importacion.Ok(2, 0), resultado)
        assertEquals(listOf("Banco"), dao().listaCompleta().map { it.titulo })
        assertEquals(listOf("Correo"), dao().papelera().first().map { it.titulo })
    }

    @Test
    fun un_archivo_que_no_es_respaldo_se_rechaza_sin_tocar_la_boveda() = runBlocking {
        val ajeno = carpeta.root.toPath().resolve("foto.jpg")
        Files.write(ajeno, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))

        val resultado = respaldo.importa(ajeno, claveArchivo.toCharArray(), reemplazar = true)

        assertEquals(GestorRespaldo.Importacion.NoEsUnRespaldo, resultado)
        // Aunque se pidio reemplazar: nada se borra antes de saber que el archivo sirve.
        assertEquals(2, dao().listaCompleta().size)
    }
}
