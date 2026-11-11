package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Respaldo
import com.carlosalbertoxw.boveda.datos.enPapelera
import com.carlosalbertoxw.boveda.seguridad.Kdf
import com.carlosalbertoxw.boveda.seguridad.Sobre
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * El respaldo es el unico archivo que sale del equipo, asi que sus
 * propiedades se prueban una por una: que vuelva completo —con la papelera y el
 * historial dentro—, que no se abra sin la contrasena, que no se pueda
 * debilitar editando la cabecera y que los archivos viejos se sigan leyendo.
 *
 * Aqui org.json es la libreria real. La app de Android repite la ida y vuelta
 * con Robolectric, contra el org.json que trae el sistema.
 */
class RespaldoTest {

    private val banco = Credencial(
        id = 7,
        titulo = "Banco",
        usuario = "carlos@ejemplo.mx",
        contrasena = "N0-la-adivinas #2026",
        sitio = "banco.mx",
        notas = "Cuenta de nómina",
        creadoEn = 1_700_000_000_000,
        actualizadoEn = 1_700_000_500_000
    )

    private val correo = Credencial(
        id = 9,
        titulo = "Correo",
        usuario = "carlos",
        contrasena = "otra distinta",
        sitio = "",
        notas = "",
        creadoEn = 1_700_000_100_000,
        actualizadoEn = 1_700_000_100_000,
        // Borrada anteayer: sigue en la papelera esperando su plazo.
        eliminadoEn = 1_700_090_000_000
    )

    private val muestra = listOf(
        Respaldo.Entrada(
            banco,
            listOf(
                HistorialContrasena(
                    id = 3,
                    credencialId = 7,
                    contrasena = "N0-la-adivinas #2026",
                    creadoEn = 1_700_000_500_000,
                    actual = true
                ),
                HistorialContrasena(
                    id = 1,
                    credencialId = 7,
                    contrasena = "la vieja del año pasado",
                    creadoEn = 1_600_000_000_000,
                    actual = false
                )
            )
        ),
        Respaldo.Entrada(
            correo,
            listOf(
                HistorialContrasena(
                    id = 8,
                    credencialId = 9,
                    contrasena = "otra distinta",
                    creadoEn = 1_700_000_100_000,
                    actual = true
                )
            )
        )
    )

    private fun clave() = "respaldo seguro 2026".toCharArray()

    private fun ida_y_vuelta(entradas: List<Respaldo.Entrada> = muestra): List<Respaldo.Entrada> {
        val leido = Respaldo.lee(Respaldo.escribe(entradas, clave()), clave())
        assertTrue("no se pudo releer el respaldo: $leido", leido is Respaldo.Lectura.Ok)
        return (leido as Respaldo.Lectura.Ok).entradas
    }

    @Test
    fun `lo exportado vuelve completo y sin perder acentos`() {
        val entradas = ida_y_vuelta()

        assertEquals(2, entradas.size)
        val primera = entradas[0].credencial
        assertEquals("Banco", primera.titulo)
        assertEquals("N0-la-adivinas #2026", primera.contrasena)
        assertEquals("Cuenta de nómina", primera.notas)
        assertEquals(1_700_000_000_000, primera.creadoEn)
    }

    @Test
    fun `lo que estaba en la papelera sigue en la papelera al volver`() {
        val entradas = ida_y_vuelta().associateBy { it.credencial.titulo }

        // Si el respaldo no llevara la fecha de borrado, restaurar convertiria
        // la papelera en entradas activas y el borrado se desharia solo.
        assertNull(entradas.getValue("Banco").credencial.eliminadoEn)
        assertEquals(1_700_090_000_000, entradas.getValue("Correo").credencial.eliminadoEn)
        assertTrue(entradas.getValue("Correo").credencial.enPapelera)
    }

    @Test
    fun `el historial viaja entero y con una sola marca`() {
        val historial = ida_y_vuelta().first { it.credencial.titulo == "Banco" }.historial

        assertEquals(2, historial.size)
        assertEquals(
            listOf("N0-la-adivinas #2026", "la vieja del año pasado"),
            historial.map { it.contrasena }
        )
        assertEquals(1, historial.count { it.actual })
        assertEquals("N0-la-adivinas #2026", historial.first { it.actual }.contrasena)
        assertEquals(1_600_000_000_000, historial[1].creadoEn)
    }

    @Test
    fun `los ids no viajan en el archivo`() {
        val entradas = ida_y_vuelta()

        // Al importar se insertan como filas nuevas; arrastrar ids ajenos solo
        // provocaria choques con lo que ya hay en la boveda. Vale igual para el
        // historial: su credencialId se asigna al insertar.
        assertTrue(entradas.all { it.credencial.id == 0L })
        assertTrue(entradas.flatMap { it.historial }.all { it.id == 0L && it.credencialId == 0L })
    }

    @Test
    fun `sin la contrasena correcta no se abre`() {
        val archivo = Respaldo.escribe(muestra, clave())
        val leido = Respaldo.lee(archivo, "otra cosa".toCharArray())

        assertTrue(leido is Respaldo.Lectura.ContrasenaIncorrecta)
    }

    @Test
    fun `ninguna contrasena aparece en claro dentro del archivo`() {
        val texto = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)

        assertFalse(texto.contains("N0-la-adivinas"))
        assertFalse(texto.contains("carlos@ejemplo.mx"))
        assertFalse(texto.contains("Banco"))
        // Las viejas del historial son contrasenas como las demas y se cifran
        // igual: son las que mas facil seria olvidarse de proteger.
        assertFalse(texto.contains("la vieja del año pasado"))
        // La cabecera si va legible, a proposito.
        assertTrue(texto.contains(Respaldo.FORMATO))
    }

    @Test
    fun `bajar las iteraciones de la cabecera invalida el archivo`() {
        val original = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)
        val manipulado = original.replace("\"iteraciones\": 210000", "\"iteraciones\": 1")

        // Comprueba que la sustitucion de verdad ocurrio, no que el test pase
        // por no haber cambiado nada.
        assertFalse(original == manipulado)

        val leido = Respaldo.lee(manipulado.toByteArray(Charsets.UTF_8), clave())

        // La cabecera va autenticada como AAD: sin esto, un atacante abarataria
        // su ataque por diccionario a coste cero.
        assertTrue(leido is Respaldo.Lectura.ContrasenaIncorrecta)
    }

    @Test
    fun `bajar la version de la cabecera invalida el archivo`() {
        val original = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)
        val manipulado = original.replace("\"version\": 2", "\"version\": 1")
        assertFalse(original == manipulado)

        // La version tambien va en el AAD: no se puede hacer pasar un archivo
        // por uno de otra version para que se lea con reglas distintas.
        val leido = Respaldo.lee(manipulado.toByteArray(Charsets.UTF_8), clave())
        assertTrue(leido is Respaldo.Lectura.ContrasenaIncorrecta)
    }

    @Test
    fun `alterar el cuerpo cifrado invalida el archivo`() {
        val archivo = Respaldo.escribe(muestra, clave())
        // Se voltea un byte a media zona de datos.
        archivo[archivo.size / 2] = (archivo[archivo.size / 2].toInt() xor 0x01).toByte()

        val leido = Respaldo.lee(archivo, clave())
        assertTrue(leido !is Respaldo.Lectura.Ok)
    }

    @Test
    fun `un archivo ajeno se rechaza sin confundirlo con contrasena mala`() {
        val leido = Respaldo.lee("""{"hola":"mundo"}""".toByteArray(), clave())
        assertTrue(leido is Respaldo.Lectura.NoEsUnRespaldo)
    }

    @Test
    fun `algo que ni siquiera es json se rechaza`() {
        val leido = Respaldo.lee("no soy un archivo".toByteArray(), clave())
        assertTrue(leido is Respaldo.Lectura.NoEsUnRespaldo)
    }

    @Test
    fun `un respaldo de una version futura se rechaza diciendolo`() {
        val original = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)
        val delFuturo = original.replace("\"version\": 2", "\"version\": 99")

        val leido = Respaldo.lee(delFuturo.toByteArray(Charsets.UTF_8), clave())

        // Importa que no se confunda con "contrasena incorrecta": el usuario
        // tiene que saber que el archivo es bueno y la app es la vieja.
        assertEquals(Respaldo.Lectura.VersionNoSoportada(99), leido)
    }

    @Test
    fun `dos exportaciones iguales dan archivos distintos`() {
        val uno = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)
        val otro = String(Respaldo.escribe(muestra, clave()), Charsets.UTF_8)

        // Sal e IV nuevos cada vez: dos respaldos del mismo contenido no deben
        // poder compararse para deducir que no cambio nada entre ellos.
        assertFalse(uno == otro)
    }

    @Test
    fun `una boveda vacia se exporta e importa sin romperse`() {
        val leido = Respaldo.lee(Respaldo.escribe(emptyList(), clave()), clave())
        assertEquals(0, (leido as Respaldo.Lectura.Ok).entradas.size)
    }

    // --- Compatibilidad con la version 1 ---------------------------------

    @Test
    fun `un respaldo de la version 1 se sigue leyendo`() {
        val leido = Respaldo.lee(respaldoVersion1(), clave())

        // Los archivos de la version 1 estan en los telefonos de la gente. Un
        // respaldo que deja de abrirse por actualizar la app es exactamente el
        // fallo que un respaldo no puede tener.
        assertTrue("no se pudo leer el archivo v1: $leido", leido is Respaldo.Lectura.Ok)
        val entradas = (leido as Respaldo.Lectura.Ok).entradas
        assertEquals(1, entradas.size)
        assertEquals("Banco", entradas[0].credencial.titulo)
        assertEquals("melon-42", entradas[0].credencial.contrasena)
    }

    @Test
    fun `un respaldo de la version 1 entra con la papelera vacia y su historial abierto`() {
        val entrada = (Respaldo.lee(respaldoVersion1(), clave()) as Respaldo.Lectura.Ok)
            .entradas
            .single()

        // La version 1 no sabia de papeleras: nada de lo que trae esta borrado.
        assertNull(entrada.credencial.eliminadoEn)
        // Y aunque no traiga historial, la entrada no puede quedarse sin el: su
        // contrasena de hoy es justo la que hace falta tener anotada.
        assertEquals(1, entrada.historial.size)
        assertEquals("melon-42", entrada.historial.single().contrasena)
        assertTrue(entrada.historial.single().actual)
    }

    /**
     * Un archivo tal y como lo escribia la version 1: sin `eliminadoEn`, sin
     * `historial` y con la version 1 en la cabecera autenticada. Se arma a mano
     * porque el escritor de la version 1 ya no existe.
     */
    private fun respaldoVersion1(): ByteArray {
        val sal = Sobre.aleatorio(Kdf.BYTES_SAL)
        val salB64 = Base64.getEncoder().encodeToString(sal)
        val aad = "boveda-respaldo|1|PBKDF2WithHmacSHA256|${Kdf.ITERACIONES}|$salB64|AES-256-GCM"
        val cuerpo = JSONObject().put(
            "credenciales",
            JSONArray().put(
                JSONObject().apply {
                    put("titulo", "Banco")
                    put("usuario", "carlos")
                    put("contrasena", "melon-42")
                    put("sitio", "banco.mx")
                    put("notas", "")
                    put("creadoEn", 1_600_000_000_000)
                    put("actualizadoEn", 1_600_000_000_000)
                }
            )
        ).toString()

        val llave = Kdf.deriva(clave(), sal, Kdf.ITERACIONES)
        val cifrado = try {
            Sobre.cierra(llave, cuerpo.toByteArray(Charsets.UTF_8), aad.toByteArray(Charsets.UTF_8))
        } finally {
            Sobre.borra(llave)
        }

        return JSONObject().apply {
            put("formato", "boveda-respaldo")
            put("version", 1)
            put("creado", "2026-01-01T00:00:00Z")
            put("entradas", 1)
            put(
                "kdf",
                JSONObject().apply {
                    put("algoritmo", "PBKDF2WithHmacSHA256")
                    put("iteraciones", Kdf.ITERACIONES)
                    put("sal", salB64)
                }
            )
            put("cifrado", "AES-256-GCM")
            put("datos", Base64.getEncoder().encodeToString(cifrado))
        }.toString(2).toByteArray(Charsets.UTF_8)
    }
}
