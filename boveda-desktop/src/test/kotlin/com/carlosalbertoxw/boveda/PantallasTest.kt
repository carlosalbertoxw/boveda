package com.carlosalbertoxw.boveda

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaAjustes
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaAlta
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaDetalle
import com.carlosalbertoxw.boveda.ui.pantallas.SEGUNDOS_VISIBLE
import com.carlosalbertoxw.boveda.ui.tema.TemaBoveda
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Las protecciones que viven en la interfaz y no en las capas de abajo.
 *
 * Que la contrasena salga tapada y se vuelva a tapar sola, o que no se pueda
 * crear la boveda con una maestra que no coincide, son decisiones de la
 * pantalla: ninguna prueba del gestor o del DAO las veria romperse. Corren en
 * la JVM, sin ventana visible, contra una boveda real en una carpeta temporal.
 */
@OptIn(ExperimentalTestApi::class)
class PantallasTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var aplicacion: AplicacionBoveda
    private lateinit var modelo: ModeloBoveda

    private val maestra = "correcto caballo bateria grapa"
    private val secreto = "N0-la-adivinas #2026"

    @Before
    fun arranca() {
        aplicacion = AplicacionBoveda(carpeta.root.toPath())
        modelo = ModeloBoveda(aplicacion)
    }

    @After
    fun termina() {
        runCatching { modelo.libera() }
        runCatching { aplicacion.gestor.bloquea() }
    }

    /** Crea la boveda sin pasar por la interfaz y le mete una entrada. */
    private fun bovedaConUnaEntrada(): Long = runBlocking {
        aplicacion.gestor.crea(maestra.toCharArray())
        val db = (aplicacion.gestor.estado.value as EstadoBoveda.Abierta).db
        db.credencialDao().inserta(Credencial(titulo = "Banco", usuario = "carlos", contrasena = secreto))
    }

    @Test
    fun `la boveda no se crea hasta que las dos maestras coinciden`() = runComposeUiTest {
        setContent { TemaBoveda { PantallaAlta(modelo) } }

        onNodeWithText(Textos.CONTRASENA_MAESTRA).performTextInput(maestra)
        onNodeWithText(Textos.ALTA_REPITE).performTextInput("$maestra y otra cosa")
        onNodeWithText(Textos.ALTA_NO_COINCIDEN).assertExists()
        onNodeWithText(Textos.ALTA_CREAR).assertIsNotEnabled()
    }

    @Test
    fun `crear la boveda desde la pantalla de alta la deja abierta`() = runComposeUiTest {
        setContent { TemaBoveda { PantallaAlta(modelo) } }

        onNodeWithText(Textos.CONTRASENA_MAESTRA).performTextInput(maestra)
        onNodeWithText(Textos.ALTA_REPITE).performTextInput(maestra)
        onNodeWithText(Textos.ALTA_CREAR).assertIsEnabled().performClick()

        // PBKDF2 con las iteraciones reales: tarda lo suyo.
        waitUntil(timeoutMillis = 30_000) { aplicacion.gestor.abierta }
        assertTrue(aplicacion.gestor.estado.value is EstadoBoveda.Abierta)
    }

    @Test
    fun `la contrasena sale tapada, se descubre y se vuelve a tapar sola`() = runComposeUiTest {
        val id = bovedaConUnaEntrada()
        setContent { TemaBoveda { PantallaDetalle(modelo, id, {}, {}, {}) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("carlos")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText(secreto).assertDoesNotExist()

        onNodeWithContentDescription(Textos.MOSTRAR).performClick()
        onNodeWithText(secreto).assertExists()

        mainClock.advanceTimeBy(SEGUNDOS_VISIBLE * 1000L + 1_000)
        onNodeWithText(secreto).assertDoesNotExist()
        onNodeWithContentDescription(Textos.MOSTRAR).assertExists()
    }

    @Test
    fun `las licencias de terceros se pueden leer desde ajustes`() = runComposeUiTest {
        bovedaConUnaEntrada()
        setContent { TemaBoveda { PantallaAjustes(aplicacion, modelo) {} } }

        onNodeWithText(Textos.AJUSTES_LICENCIAS_VER).performScrollTo().performClick()

        onNodeWithText("ZETETIC LLC", substring = true).assertExists()
    }
}
