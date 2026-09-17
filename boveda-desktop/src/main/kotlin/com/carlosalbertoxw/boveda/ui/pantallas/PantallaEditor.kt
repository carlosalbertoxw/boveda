package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

/**
 * Los campos que no son la contrasena tambien llevan secretos: en las notas es
 * donde la gente apunta respuestas de preguntas de seguridad y codigos de
 * recuperacion. Se pide a los metodos de entrada que no aprendan lo tecleado.
 */
private val SIN_APRENDIZAJE = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)
private val NOTAS = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Text)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditor(modelo: ModeloBoveda, id: Long, alVolver: () -> Unit) {
    val todas by modelo.todas.collectAsState()
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
        if (!puedeGuardar) return
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

    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) Textos.EDITOR_NUEVA else Textos.EDITOR_EDITAR) },
                navigationIcon = { BotonVolver(alVolver) },
                actions = {
                    TextButton(onClick = { guarda() }, enabled = puedeGuardar) { Text(Textos.GUARDAR) }
                }
            )
        }
    ) { relleno ->
        ColumnaDesplazable(Modifier.fillMaxSize().padding(relleno), espaciado = 12.dp) {
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text(Textos.EDITOR_TITULO_CAMPO) },
                placeholder = { Text(Textos.EDITOR_TITULO_EJEMPLO) },
                singleLine = true,
                keyboardOptions = SIN_APRENDIZAJE,
                modifier = Modifier.fillMaxWidth().focusRequester(foco).alPulsarEnter(::guarda)
            )

            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text(Textos.EDITOR_USUARIO) },
                singleLine = true,
                keyboardOptions = SIN_APRENDIZAJE,
                modifier = Modifier.fillMaxWidth().alPulsarEnter(::guarda)
            )

            CampoSecreto(
                valor = contrasena,
                alCambiar = { contrasena = it },
                etiqueta = Textos.EDITOR_CONTRASENA,
                alEnviar = ::guarda
            )
            MedidorFuerza(contrasena)

            OutlinedButton(onClick = { generando = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Casino, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(Textos.EDITOR_GENERAR)
            }

            OutlinedTextField(
                value = sitio,
                onValueChange = { sitio = it },
                label = { Text(Textos.EDITOR_SITIO) },
                singleLine = true,
                keyboardOptions = SIN_APRENDIZAJE,
                modifier = Modifier.fillMaxWidth().alPulsarEnter(::guarda)
            )

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text(Textos.EDITOR_NOTAS) },
                minLines = 3,
                keyboardOptions = NOTAS,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                Textos.EDITOR_NOTA_PIE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (generando) {
        DialogoGenerador(alElegir = { contrasena = it }, alCerrar = { generando = false })
    }
}
