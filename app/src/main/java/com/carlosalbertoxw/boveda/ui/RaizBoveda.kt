package com.carlosalbertoxw.boveda.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaAjustes
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaAlta
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaDesbloqueo
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaDetalle
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaEditor
import com.carlosalbertoxw.boveda.ui.pantallas.PantallaListado

/**
 * La navegacion cuelga del estado del candado, no al reves.
 *
 * Cuando la boveda se cierra —por inactividad, por irse a segundo plano o
 * porque el usuario lo pidio— el arbol de pantallas de dentro se destruye
 * entero y con el toda contrasena que estuviera pintada. No hay forma de que
 * quede una pantalla de detalle viva detras del candado.
 */
@Composable
fun RaizBoveda(modelo: ModeloBoveda = viewModel()) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val aviso by modelo.aviso.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(aviso) {
        aviso?.let {
            avisos.showSnackbar(it)
            modelo.limpiaAviso()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when (estado) {
                is EstadoBoveda.SinConfigurar -> PantallaAlta(modelo)
                is EstadoBoveda.Cerrada -> PantallaDesbloqueo(modelo)
                is EstadoBoveda.Abierta -> BovedaAbierta(modelo)
            }
        }
    }
}

private const val LISTADO = "listado"
private const val AJUSTES = "ajustes"
private const val DETALLE = "detalle"
private const val EDITOR = "editor"

@Composable
private fun BovedaAbierta(modelo: ModeloBoveda) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = LISTADO) {
        composable(LISTADO) {
            PantallaListado(
                modelo = modelo,
                alAbrir = { id -> nav.navigate("$DETALLE/$id") },
                alCrear = { nav.navigate("$EDITOR/0") },
                alAjustes = { nav.navigate(AJUSTES) }
            )
        }
        composable("$DETALLE/{id}") { pila ->
            val id = pila.arguments?.getString("id")?.toLongOrNull() ?: 0L
            PantallaDetalle(
                modelo = modelo,
                id = id,
                alVolver = { nav.popBackStack() },
                alEditar = { nav.navigate("$EDITOR/$id") }
            )
        }
        composable("$EDITOR/{id}") { pila ->
            val id = pila.arguments?.getString("id")?.toLongOrNull() ?: 0L
            PantallaEditor(
                modelo = modelo,
                id = id,
                alVolver = { nav.popBackStack() }
            )
        }
        composable(AJUSTES) {
            PantallaAjustes(modelo = modelo, alVolver = { nav.popBackStack() })
        }
    }
}
