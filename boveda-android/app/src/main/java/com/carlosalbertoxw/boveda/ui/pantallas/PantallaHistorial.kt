package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Las contrasenas que esta entrada tuvo, de la mas reciente a la mas vieja.
 *
 * Es la pantalla con mas secretos por centimetro de toda la app, asi que hereda
 * las mismas reglas del detalle y las aprieta un poco mas: solo se puede
 * descubrir una contrasena a la vez —descubrir la siguiente tapa la anterior— y
 * la descubierta se vuelve a tapar sola. Ver varias en pantalla al mismo tiempo
 * no le sirve a nadie y es justo lo que aprovecha quien mira de reojo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorial(modelo: ModeloBoveda, id: Long, alVolver: () -> Unit) {
    val flujo = remember(id) { modelo.historialDe(id) }
    val historial by flujo.collectAsStateWithLifecycle(emptyList())

    var visible by remember { mutableStateOf(0L) }
    var aUsar by remember { mutableStateOf<HistorialContrasena?>(null) }
    var aBorrar by remember { mutableStateOf<HistorialContrasena?>(null) }

    LaunchedEffect(visible) {
        if (visible != 0L) {
            delay(SEGUNDOS_VISIBLE * 1000L)
            visible = 0L
        }
    }

    val formato = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.historial_titulo), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                }
            )
        }
    ) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (historial.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.historial_vacio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(historial, key = { it.id }) { anotacion ->
                val etiqueta = stringResource(R.string.detalle_contrasena)
                FichaHistorial(
                    anotacion = anotacion,
                    visible = visible == anotacion.id,
                    fecha = formato.format(Date(anotacion.creadoEn)),
                    alAlternar = { visible = if (visible == anotacion.id) 0L else anotacion.id },
                    alCopiar = { modelo.copia(etiqueta, anotacion.contrasena) },
                    alUsar = { aUsar = anotacion },
                    alBorrar = { aBorrar = anotacion }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.historial_explicacion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    aUsar?.let { anotacion ->
        DialogoSeguro(
            alCerrar = { aUsar = null },
            titulo = { Text(stringResource(R.string.historial_usar_titulo)) },
            contenido = { Text(stringResource(R.string.historial_usar_aviso)) },
            botonConfirmar = {
                TextButton(onClick = {
                    aUsar = null
                    modelo.usaDelHistorial(anotacion)
                }) { Text(stringResource(R.string.historial_usar)) }
            },
            botonCancelar = {
                TextButton(onClick = { aUsar = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }

    aBorrar?.let { anotacion ->
        DialogoSeguro(
            alCerrar = { aBorrar = null },
            titulo = { Text(stringResource(R.string.historial_borrar_titulo)) },
            contenido = { Text(stringResource(R.string.historial_borrar_aviso)) },
            botonConfirmar = {
                TextButton(onClick = {
                    aBorrar = null
                    modelo.borraDelHistorial(anotacion)
                }) {
                    Text(
                        stringResource(R.string.eliminar),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            botonCancelar = {
                TextButton(onClick = { aBorrar = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun FichaHistorial(
    anotacion: HistorialContrasena,
    visible: Boolean,
    fecha: String,
    alAlternar: () -> Unit,
    alCopiar: () -> Unit,
    alUsar: () -> Unit,
    alBorrar: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (anotacion.actual) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.historial_actual)) }
                )
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
                        contentDescription = stringResource(
                            if (visible) R.string.ocultar else R.string.mostrar
                        )
                    )
                }
                IconButton(onClick = alCopiar) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.copiar)
                    )
                }
            }

            Text(
                stringResource(R.string.historial_desde, fecha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // La marcada como actual no ofrece ninguna de las dos acciones:
            // "usar esta" no haria nada y borrarla dejaria a la entrada sin la
            // fila que le corresponde. El SQL lo impide de todas formas; esto
            // solo evita ofrecer un boton que no va a funcionar.
            if (!anotacion.actual) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = alUsar) {
                        Text(stringResource(R.string.historial_usar))
                    }
                    TextButton(onClick = alBorrar) {
                        Text(
                            stringResource(R.string.historial_borrar),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
