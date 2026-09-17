package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.Papelera
import com.carlosalbertoxw.boveda.ui.ModeloPapelera

/**
 * Lo borrado, mientras dura el plazo.
 *
 * La contrasena no aparece aqui, ni tapada con puntos, ni hay forma de copiarla
 * ni de abrir el detalle: la papelera sirve para reconocer que se borro y
 * decidir si vuelve, no para consultar secretos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPapelera(aplicacion: AplicacionBoveda, alVolver: () -> Unit) {
    val modelo = remember { ModeloPapelera(aplicacion) }
    DisposableEffect(modelo) { onDispose { modelo.libera() } }

    val entradas by modelo.entradas.collectAsState()
    var confirmaVaciado by remember { mutableStateOf(false) }
    var aBorrar by remember { mutableStateOf<Credencial?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Textos.PAPELERA_TITULO) },
                navigationIcon = { BotonVolver(alVolver) },
                actions = {
                    if (entradas.isNotEmpty()) {
                        IconButton(onClick = { confirmaVaciado = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = Textos.PAPELERA_VACIAR)
                        }
                    }
                }
            )
        }
    ) { relleno ->
        if (entradas.isEmpty()) {
            PapeleraVacia(Modifier.fillMaxSize().padding(relleno))
            return@Scaffold
        }

        val estadoLista = rememberLazyListState()
        Box(Modifier.fillMaxSize().padding(relleno)) {
            LazyColumn(
                state = estadoLista,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entradas, key = { it.id }) { credencial ->
                    FichaBorrada(
                        credencial = credencial,
                        alRestaurar = { modelo.restaura(credencial) },
                        alBorrar = { aBorrar = credencial }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Textos.PAPELERA_EXPLICACION,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BarraDeLista(estadoLista)
        }
    }

    aBorrar?.let { credencial ->
        DialogoSeguro(
            alCerrar = { aBorrar = null },
            titulo = { Text(Textos.papeleraBorrarTitulo(credencial.titulo)) },
            contenido = { Text(Textos.PAPELERA_BORRAR_AVISO) },
            botonConfirmar = {
                TextButton(onClick = {
                    aBorrar = null
                    modelo.borraDeVerdad(credencial)
                }) { Text(Textos.PAPELERA_BORRAR_AHORA, color = MaterialTheme.colorScheme.error) }
            },
            botonCancelar = { TextButton(onClick = { aBorrar = null }) { Text(Textos.CANCELAR) } }
        )
    }

    if (confirmaVaciado) {
        DialogoSeguro(
            alCerrar = { confirmaVaciado = false },
            titulo = { Text(Textos.PAPELERA_VACIAR_TITULO) },
            contenido = { Text(Textos.papeleraVaciarAviso(entradas.size)) },
            botonConfirmar = {
                TextButton(onClick = {
                    confirmaVaciado = false
                    modelo.vacia()
                }) { Text(Textos.PAPELERA_VACIAR, color = MaterialTheme.colorScheme.error) }
            },
            botonCancelar = { TextButton(onClick = { confirmaVaciado = false }) { Text(Textos.CANCELAR) } }
        )
    }
}

@Composable
private fun PapeleraVacia(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(Textos.PAPELERA_VACIA, style = MaterialTheme.typography.titleMedium)
        Text(
            Textos.papeleraVaciaAyuda(Papelera.DIAS_RETENCION),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FichaBorrada(credencial: Credencial, alRestaurar: () -> Unit, alBorrar: () -> Unit) {
    // La cuenta atras se calcula al pintar, contra el reloj de ahora.
    val eliminadoEn = credencial.eliminadoEn
    val dias = remember(eliminadoEn) {
        eliminadoEn?.let { Papelera.diasRestantes(it, System.currentTimeMillis()) } ?: 0
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                credencial.titulo,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (credencial.usuario.isNotBlank()) {
                Text(
                    credencial.usuario,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (dias > 0) Textos.papeleraRestante(dias) else Textos.PAPELERA_VENCE_YA,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = alRestaurar) { Text(Textos.PAPELERA_RESTAURAR) }
                TextButton(onClick = alBorrar) {
                    Text(Textos.PAPELERA_BORRAR_AHORA, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
