package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditor(modelo: ModeloBoveda, id: Long, alVolver: () -> Unit) {
    val todas by modelo.todas.collectAsStateWithLifecycle()
    val original = remember(todas, id) { todas.firstOrNull { it.id == id } }

    // remember con clave en el original: en cuanto la entrada carga se rellena
    // el formulario, y despues no se vuelve a pisar lo que el usuario teclea.
    var titulo by remember(original) { mutableStateOf(original?.titulo ?: "") }
    var usuario by remember(original) { mutableStateOf(original?.usuario ?: "") }
    var contrasena by remember(original) { mutableStateOf(original?.contrasena ?: "") }
    var sitio by remember(original) { mutableStateOf(original?.sitio ?: "") }
    var notas by remember(original) { mutableStateOf(original?.notas ?: "") }

    var generando by remember { mutableStateOf(false) }

    val puedeGuardar = titulo.isNotBlank() && contrasena.isNotEmpty()

    fun guarda() {
        modelo.guarda(
            (original ?: Credencial(titulo = "", usuario = "", contrasena = "")).copy(
                titulo = titulo.trim(),
                usuario = usuario.trim(),
                contrasena = contrasena,
                sitio = sitio.trim(),
                notas = notas.trim()
            )
        )
        alVolver()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Nueva entrada" else "Editar") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { guarda() }, enabled = puedeGuardar) {
                        Text("Guardar")
                    }
                }
            )
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
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Titulo") },
                placeholder = { Text("Banco, correo, wifi de casa...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text("Usuario o correo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            CampoSecreto(
                valor = contrasena,
                alCambiar = { contrasena = it },
                etiqueta = "Contrasena"
            )
            MedidorFuerza(contrasena)

            OutlinedButton(
                onClick = { generando = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null)
                Text("  Generar una segura")
            }

            OutlinedTextField(
                value = sitio,
                onValueChange = { sitio = it },
                label = { Text("Sitio o app (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Todo lo que escribas aqui se guarda cifrado dentro de la boveda y no " +
                    "sale del telefono.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (generando) {
        DialogoGenerador(
            alElegir = { contrasena = it },
            alCerrar = { generando = false }
        )
    }
}
