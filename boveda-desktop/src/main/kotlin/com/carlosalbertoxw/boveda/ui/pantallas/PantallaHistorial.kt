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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import kotlinx.coroutines.delay

/**
 * Las contrasenas que esta entrada tuvo, de la mas reciente a la mas vieja.
 *
 * Es la pantalla con mas secretos por centimetro de toda la app: solo se puede
 * descubrir una contrasena a la vez —descubrir la siguiente tapa la anterior— y
 * la descubierta se vuelve a tapar sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorial(modelo: ModeloBoveda, id: Long, alVolver: () -> Unit) {
    val flujo = remember(id) { modelo.historialDe(id) }
    val historial by flujo.collectAsState(emptyList())

    var visible by remember { mutableLongStateOf(0L) }
    var aUsar by remember { mutableStateOf<HistorialContrasena?>(null) }
    var aBorrar by remember { mutableStateOf<HistorialContrasena?>(null) }

    LaunchedEffect(visible) {
        if (visible != 0L) {
            delay(SEGUNDOS_VISIBLE * 1000L)
            visible = 0L
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Textos.HISTORIAL_TITULO, maxLines = 1) },
                navigationIcon = { BotonVolver(alVolver) }
            )
        }
    ) { relleno ->
        val estadoLista = rememberLazyListState()
        Box(Modifier.fillMaxSize().padding(relleno)) {
            LazyColumn(
                state = estadoLista,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (historial.isEmpty()) {
                    item {
                        Text(
                            Textos.HISTORIAL_VACIO,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(historial, key = { it.id }) { anotacion ->
                    FichaHistorial(
                        anotacion = anotacion,
                        visible = visible == anotacion.id,
                        alAlternar = { visible = if (visible == anotacion.id) 0L else anotacion.id },
                        alCopiar = { modelo.copia(anotacion.contrasena) },
                        alUsar = { aUsar = anotacion },
                        alBorrar = { aBorrar = anotacion }
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Textos.HISTORIAL_EXPLICACION,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BarraDeLista(estadoLista)
        }
    }

    aUsar?.let { anotacion ->
        DialogoSeguro(
            alCerrar = { aUsar = null },
            titulo = { Text(Textos.HISTORIAL_USAR_TITULO) },
            contenido = { Text(Textos.HISTORIAL_USAR_AVISO) },
            botonConfirmar = {
                TextButton(onClick = {
                    aUsar = null
                    modelo.usaDelHistorial(anotacion)
                }) { Text(Textos.HISTORIAL_USAR) }
            },
            botonCancelar = { TextButton(onClick = { aUsar = null }) { Text(Textos.CANCELAR) } }
        )
    }

    aBorrar?.let { anotacion ->
        DialogoSeguro(
            alCerrar = { aBorrar = null },
            titulo = { Text(Textos.HISTORIAL_BORRAR_TITULO) },
            contenido = { Text(Textos.HISTORIAL_BORRAR_AVISO) },
            botonConfirmar = {
                TextButton(onClick = {
                    aBorrar = null
                    modelo.borraDelHistorial(anotacion)
                }) { Text(Textos.ELIMINAR, color = MaterialTheme.colorScheme.error) }
            },
            botonCancelar = { TextButton(onClick = { aBorrar = null }) { Text(Textos.CANCELAR) } }
        )
    }
}

@Composable
private fun FichaHistorial(
    anotacion: HistorialContrasena,
    visible: Boolean,
    alAlternar: () -> Unit,
    alCopiar: () -> Unit,
    alUsar: () -> Unit,
    alBorrar: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (anotacion.actual) {
                AssistChip(onClick = {}, enabled = false, label = { Text(Textos.HISTORIAL_ACTUAL) })
                Spacer(Modifier.height(4.dp))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (visible) anotacion.contrasena else "•".repeat(12),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = alAlternar) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) Textos.OCULTAR else Textos.MOSTRAR
                    )
                }
                IconButton(onClick = alCopiar) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = Textos.COPIAR)
                }
            }

            Text(
                Textos.historialDesde(fecha(anotacion.creadoEn)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // La marcada como actual no ofrece ninguna de las dos acciones:
            // "usar esta" no haria nada y borrarla dejaria a la entrada sin la
            // fila que le corresponde. El DAO lo impide de todas formas.
            if (!anotacion.actual) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = alUsar) { Text(Textos.HISTORIAL_USAR) }
                    TextButton(onClick = alBorrar) {
                        Text(Textos.HISTORIAL_BORRAR, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
