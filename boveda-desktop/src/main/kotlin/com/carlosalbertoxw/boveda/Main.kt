package com.carlosalbertoxw.boveda

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.carlosalbertoxw.boveda.datos.ArchivoSeguro
import com.carlosalbertoxw.boveda.escritorio.InstanciaUnica
import com.carlosalbertoxw.boveda.escritorio.ProteccionPantalla
import com.carlosalbertoxw.boveda.escritorio.Rutas
import com.carlosalbertoxw.boveda.ui.LocalVentana
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import com.carlosalbertoxw.boveda.ui.RaizBoveda
import com.carlosalbertoxw.boveda.ui.tema.IconoBoveda
import com.carlosalbertoxw.boveda.ui.tema.TemaBoveda
import kotlinx.coroutines.delay
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private const val ANCHO_PREFERIDO = 520
private const val ALTO_PREFERIDO = 760
private const val ANCHO_MINIMO = 400
private const val ALTO_MINIMO = 480

/** Aire alrededor de la ventana para que el borde y la sombra no toquen la barra de tareas. */
private const val MARGEN = 40

fun main() {
    val directorio = Rutas.directorioDatos()
    try {
        ArchivoSeguro.creaDirectorioPrivado(directorio)
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(
            null,
            Textos.errorCarpetaDatos(directorio.toString(), e.message.orEmpty()),
            Textos.NOMBRE_APP,
            JOptionPane.ERROR_MESSAGE
        )
        exitProcess(1)
    }

    val instancia = InstanciaUnica.adquiere(directorio)
    if (instancia == null) {
        JOptionPane.showMessageDialog(null, Textos.YA_ABIERTA, Textos.NOMBRE_APP, JOptionPane.WARNING_MESSAGE)
        exitProcess(0)
    }

    val aplicacion = AplicacionBoveda(directorio)

    // Cualquier tecla, clic o giro de rueda en cualquier ventana de la app
    // cuenta como uso: es el onUserInteraction de Android. El movimiento del
    // raton no cuenta, igual que alli no contaba tener el dedo cerca.
    Toolkit.getDefaultToolkit().addAWTEventListener(
        AWTEventListener { aplicacion.registraToque() },
        AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK
    )

    // El espacio util de la pantalla, sin la barra de tareas ni el dock, ya en
    // unidades logicas (con la escala de Windows aplicada). La ventana nunca
    // pide mas que eso: en una pantalla de 1366x768 una altura fija de 820
    // dejaba la barra de titulo y los botones de abajo fuera de la vista.
    val util = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val ancho = minOf(ANCHO_PREFERIDO, util.width - MARGEN)
    val alto = minOf(ALTO_PREFERIDO, util.height - MARGEN)

    application {
        val estadoVentana = rememberWindowState(
            width = ancho.dp,
            height = alto.dp,
            position = WindowPosition(Alignment.Center)
        )
        val modelo = remember { ModeloBoveda(aplicacion) }

        Window(
            onCloseRequest = {
                aplicacion.alSalir()
                instancia.suelta()
                exitApplication()
            },
            state = estadoVentana,
            title = Textos.NOMBRE_APP,
            icon = rememberVectorPainter(IconoBoveda),
            onPreviewKeyEvent = { evento -> aplicacion.atajos?.invoke(evento) ?: false }
        ) {
            DisposableEffect(window) {
                window.minimumSize = Dimension(minOf(ANCHO_MINIMO, ancho), minOf(ALTO_MINIMO, alto))

                // Perder el foco es el "irse a segundo plano" del escritorio:
                // cambiar a otra aplicacion o minimizar la ventana.
                val foco = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent) = aplicacion.alRecuperarFoco()
                    override fun windowLostFocus(e: WindowEvent) = aplicacion.alPerderFoco()
                }
                // La proteccion contra capturas necesita la ventana nativa ya
                // creada, que no existe hasta que se muestra por primera vez.
                val apertura = object : WindowAdapter() {
                    override fun windowOpened(e: WindowEvent) {
                        ProteccionPantalla.aplica(window)
                    }
                }
                window.addWindowFocusListener(foco)
                window.addWindowListener(apertura)
                if (window.isShowing) ProteccionPantalla.aplica(window)

                onDispose {
                    window.removeWindowFocusListener(foco)
                    window.removeWindowListener(apertura)
                }
            }

            // El vigilante de la inactividad: cierra la boveda si la app se
            // queda abierta encima de la mesa.
            LaunchedEffect(Unit) {
                while (true) {
                    delay(15_000)
                    if (!aplicacion.gestor.abierta) continue
                    if (aplicacion.caducoPorInactividad()) aplicacion.gestor.bloquea()
                }
            }

            CompositionLocalProvider(LocalVentana provides window) {
                TemaBoveda {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        RaizBoveda(aplicacion, modelo)
                    }
                }
            }
        }
    }
}
