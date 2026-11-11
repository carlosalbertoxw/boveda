package com.carlosalbertoxw.boveda.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaAjustes
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaAlta
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaDesbloqueo
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaDetalle
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaEditor
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaHistorial
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaListado
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaPapelera

/** La ventana de la app, para colgarle los dialogos nativos del sistema. */
val LocalVentana = staticCompositionLocalOf<ComposeWindow?> { null }

/**
 * La navegacion cuelga del estado del candado, no al reves.
 *
 * Cuando la boveda se cierra —por inactividad, por cambiar de aplicacion o
 * porque el usuario lo pidio— el arbol de pantallas de dentro se destruye
 * entero, pila de navegacion incluida, y con el toda contrasena que estuviera
 * pintada. No hay forma de que quede una pantalla de detalle viva detras del
 * candado.
 */
@Composable
fun RaizBoveda(aplicacion: AplicacionBoveda, modelo: ModeloBoveda) {
    val estado by modelo.estado.collectAsState()
    val aviso by aplicacion.avisos.mensaje.collectAsState()
    val barra = remember { SnackbarHostState() }

    LaunchedEffect(aviso) {
        aviso?.let {
            aplicacion.avisos.limpia()
            barra.showSnackbar(it)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(barra) }) { relleno ->
        // En una ventana ancha el contenido no se estira de lado a lado: una
        // columna legible, centrada.
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                when (estado) {
                    is EstadoBoveda.SinConfigurar -> PantallaAlta(modelo)
                    is EstadoBoveda.Cerrada -> PantallaDesbloqueo(modelo)
                    is EstadoBoveda.Abierta -> BovedaAbierta(aplicacion, modelo)
                }
            }
        }
    }
}

private sealed interface Destino {
    data object Listado : Destino
    data class Detalle(val id: Long) : Destino
    data class Editor(val id: Long) : Destino
    data class Historial(val id: Long) : Destino
    data object Papelera : Destino
    data object Ajustes : Destino
}

@Composable
private fun BovedaAbierta(aplicacion: AplicacionBoveda, modelo: ModeloBoveda) {
    val pila = remember { mutableStateListOf<Destino>(Destino.Listado) }
    val focoBusqueda = remember { FocusRequester() }

    fun ve(destino: Destino) { pila.add(destino) }
    fun vuelve() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }

    // Atajos de teclado de escritorio. Viven con la boveda abierta y se
    // retiran al cerrarla: con el candado puesto no hay nada a lo que ir.
    DisposableEffect(Unit) {
        aplicacion.atajos = { evento -> atajo(evento, pila, modelo, focoBusqueda) }
        onDispose { aplicacion.atajos = null }
    }

    when (val destino = pila.last()) {
        Destino.Listado -> PantallaListado(
            modelo = modelo,
            focoBusqueda = focoBusqueda,
            alAbrir = { ve(Destino.Detalle(it)) },
            alCrear = { ve(Destino.Editor(0)) },
            alPapelera = { ve(Destino.Papelera) },
            alAjustes = { ve(Destino.Ajustes) }
        )
        is Destino.Detalle -> PantallaDetalle(
            modelo = modelo,
            id = destino.id,
            alVolver = ::vuelve,
            alEditar = { ve(Destino.Editor(destino.id)) },
            alHistorial = { ve(Destino.Historial(destino.id)) }
        )
        is Destino.Historial -> PantallaHistorial(modelo, destino.id, alVolver = ::vuelve)
        Destino.Papelera -> PantallaPapelera(aplicacion, alVolver = ::vuelve)
        is Destino.Editor -> PantallaEditor(modelo, destino.id, alVolver = ::vuelve)
        Destino.Ajustes -> PantallaAjustes(aplicacion, modelo, alVolver = ::vuelve)
    }
}

/**
 * Ctrl+L cierra la boveda, Ctrl+N crea una entrada, Ctrl+F va al buscador y Esc
 * vuelve atras. Devuelve si la tecla era un atajo, para que no siga de largo.
 */
private fun atajo(
    evento: KeyEvent,
    pila: SnapshotStateList<Destino>,
    modelo: ModeloBoveda,
    focoBusqueda: FocusRequester
): Boolean {
    if (evento.type != KeyEventType.KeyDown) return false
    val control = evento.isCtrlPressed || evento.isMetaPressed
    return when {
        control && evento.key == Key.L -> { modelo.bloquea(); true }
        control && evento.key == Key.N -> { pila.add(Destino.Editor(0)); true }
        control && evento.key == Key.F && pila.last() == Destino.Listado -> {
            runCatching { focoBusqueda.requestFocus() }
            true
        }
        evento.key == Key.Escape && pila.size > 1 -> { pila.removeAt(pila.lastIndex); true }
        else -> false
    }
}
