package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.Papelera
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalle(
    modelo: ModeloBoveda,
    id: Long,
    alVolver: () -> Unit,
    alEditar: () -> Unit,
    alHistorial: () -> Unit
) {
    val todas by modelo.todas.collectAsState()
    val credencial = remember(todas, id) { todas.firstOrNull { it.id == id } }

    var visible by remember { mutableStateOf(false) }
    var confirmaBorrado by remember { mutableStateOf(false) }

    // La contrasena revelada se vuelve a tapar sola.
    LaunchedEffect(visible) {
        if (visible) {
            delay(SEGUNDOS_VISIBLE * 1000L)
            visible = false
        }
    }

    // Si la entrada no esta —aun no carga la lista, o ya se borro— no se pinta
    // nada mas que la flecha de volver.
    val actual = credencial ?: run {
        PantallaVacia(alVolver)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(actual.titulo, maxLines = 1) },
                navigationIcon = { BotonVolver(alVolver) },
                actions = {
                    IconButton(onClick = { confirmaBorrado = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = Textos.ELIMINAR)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = alEditar) {
                Icon(Icons.Filled.Edit, contentDescription = Textos.DETALLE_EDITAR)
            }
        }
    ) { relleno ->
        ColumnaDesplazable(Modifier.fillMaxSize().padding(relleno), espaciado = 12.dp) {
            if (actual.usuario.isNotBlank()) {
                CampoCopiable(
                    etiqueta = Textos.DETALLE_USUARIO,
                    valor = actual.usuario,
                    alCopiar = { modelo.copia(actual.usuario) }
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        Textos.DETALLE_CONTRASENA,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (visible) actual.contrasena else "•".repeat(12),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (visible) Textos.OCULTAR else Textos.MOSTRAR
                            )
                        }
                        IconButton(onClick = { modelo.copia(actual.contrasena) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = Textos.COPIAR)
                        }
                    }
                    if (visible) {
                        Text(
                            Textos.detalleSeOculta(SEGUNDOS_VISIBLE),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // El historial no se despliega aqui dentro: son mas contrasenas en
            // la misma pantalla, y esta ya tiene una.
            OutlinedButton(onClick = alHistorial, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(Textos.DETALLE_HISTORIAL)
            }

            if (actual.sitio.isNotBlank()) {
                // Solo texto copiable: no se ofrece abrir el enlace. Lanzar un
                // navegador seria justo lo que esta app no hace, hablar con
                // otras apps, y ademas filtraria a que sitios tienes cuenta.
                CampoCopiable(
                    etiqueta = Textos.DETALLE_SITIO,
                    valor = actual.sitio,
                    alCopiar = { modelo.copia(actual.sitio) }
                )
            }

            if (actual.notas.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            Textos.DETALLE_NOTAS,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        // Las notas si se pueden seleccionar a trozos: suelen
                        // llevar codigos de recuperacion y se copia solo uno.
                        SelectionContainer {
                            Text(actual.notas, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(
                Textos.detalleActualizada(fecha(actual.actualizadoEn)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmaBorrado) {
        DialogoSeguro(
            alCerrar = { confirmaBorrado = false },
            titulo = { Text(Textos.detalleEliminarTitulo(actual.titulo)) },
            contenido = { Text(Textos.detalleEliminarAviso(Papelera.DIAS_RETENCION)) },
            botonConfirmar = {
                TextButton(onClick = {
                    confirmaBorrado = false
                    modelo.mandaAPapelera(actual)
                    alVolver()
                }) { Text(Textos.ELIMINAR) }
            },
            botonCancelar = {
                TextButton(onClick = { confirmaBorrado = false }) { Text(Textos.CANCELAR) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaVacia(alVolver: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("") }, navigationIcon = { BotonVolver(alVolver) }) }
    ) { relleno -> Spacer(Modifier.padding(relleno)) }
}

@Composable
private fun CampoCopiable(etiqueta: String, valor: String, alCopiar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(valor, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = alCopiar) {
                Icon(Icons.Filled.ContentCopy, contentDescription = Textos.copiarCampo(etiqueta))
            }
        }
    }
}
