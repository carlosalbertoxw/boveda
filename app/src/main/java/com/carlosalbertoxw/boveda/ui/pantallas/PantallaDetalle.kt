package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import java.text.DateFormat
import java.util.Date

/** Segundos que la contrasena permanece visible antes de taparse sola. */
private const val SEGUNDOS_VISIBLE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalle(
    modelo: ModeloBoveda,
    id: Long,
    alVolver: () -> Unit,
    alEditar: () -> Unit
) {
    val todas by modelo.todas.collectAsStateWithLifecycle()
    val credencial = remember(todas, id) { todas.firstOrNull { it.id == id } }

    var visible by remember { mutableStateOf(false) }
    var confirmaBorrado by remember { mutableStateOf(false) }

    // La contrasena revelada se vuelve a tapar sola. Descubrirla es un acto
    // deliberado y momentaneo, no un estado en el que se deje la pantalla.
    LaunchedEffect(visible) {
        if (visible) {
            delay(SEGUNDOS_VISIBLE * 1000L)
            visible = false
        }
    }

    // Si la entrada no esta —aun no carga la lista, o ya se borro— no se pinta
    // nada. Se evita a proposito volver atras solo: al bloquearse la boveda el
    // arbol entero se destruye desde la raiz, y una navegacion automatica aqui
    // competiria con esa destruccion.
    val actual = credencial ?: run {
        PantallaVacia(alVolver)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(actual.titulo, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmaBorrado = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = alEditar) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar")
            }
        }
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (actual.usuario.isNotBlank()) {
                CampoCopiable(
                    etiqueta = "Usuario",
                    valor = actual.usuario,
                    alCopiar = { modelo.copia("Usuario", actual.usuario) }
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Contrasena",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (visible) actual.contrasena else "•".repeat(12),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (visible) "Ocultar" else "Mostrar"
                            )
                        }
                        IconButton(onClick = { modelo.copia("Contrasena", actual.contrasena) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar")
                        }
                    }
                    if (visible) {
                        Text(
                            "Se ocultara sola en $SEGUNDOS_VISIBLE s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (actual.sitio.isNotBlank()) {
                // Solo texto copiable: no se ofrece abrir el enlace. Lanzar un
                // navegador seria justo lo que esta app no hace, hablar con
                // otras apps, y ademas filtraria a que sitios tienes cuenta.
                CampoCopiable(
                    etiqueta = "Sitio o app",
                    valor = actual.sitio,
                    alCopiar = { modelo.copia("Sitio", actual.sitio) }
                )
            }

            if (actual.notas.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Notas",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(actual.notas, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val formato = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            Text(
                "Actualizada el ${formato.format(Date(actual.actualizadoEn))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmaBorrado) {
        DialogoSeguro(
            alCerrar = { confirmaBorrado = false },
            titulo = { Text("Eliminar \"${actual.titulo}\"?") },
            contenido = { Text("Se borra de la boveda y no se puede deshacer.") },
            botonConfirmar = {
                TextButton(onClick = {
                    confirmaBorrado = false
                    modelo.elimina(actual)
                    alVolver()
                }) { Text("Eliminar") }
            },
            botonCancelar = {
                TextButton(onClick = { confirmaBorrado = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaVacia(alVolver: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
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
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar $etiqueta")
            }
        }
    }
}
