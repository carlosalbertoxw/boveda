package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

/**
 * Los campos que no son la contrasena tambien llevan secretos: en las notas es
 * donde la gente apunta respuestas de preguntas de seguridad y codigos de
 * recuperacion. Con el teclado por omision, lo tecleado ahi acaba en el
 * diccionario de autocorreccion, que en algunos teclados se sincroniza. Solo el
 * campo de contrasena estaba a salvo, por usar KeyboardType.Password.
 *
 * En los campos de una sola linea se usa ese mismo tipo Password, que es la
 * senal mas fuerte que entiende cualquier teclado para no aprender ni sugerir.
 */
private val SIN_APRENDIZAJE = KeyboardOptions(
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password
)

/**
 * Las notas son multilinea, y el tipo Password deja al teclado sin tecla de
 * salto de linea. Aqui se queda en texto normal con la autocorreccion apagada,
 * que es lo mas que se puede pedir sin estropear el campo.
 */
private val NOTAS = KeyboardOptions(
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text
)

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
                title = {
                    Text(
                        stringResource(
                            if (id == 0L) R.string.editor_nueva else R.string.editor_editar
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { guarda() }, enabled = puedeGuardar) {
                        Text(stringResource(R.string.guardar))
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
                label = { Text(stringResource(R.string.editor_titulo_campo)) },
                placeholder = { Text(stringResource(R.string.editor_titulo_ejemplo)) },
                singleLine = true,
                keyboardOptions = SIN_APRENDIZAJE,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text(stringResource(R.string.editor_usuario)) },
                singleLine = true,
                keyboardOptions = SIN_APRENDIZAJE,
                modifier = Modifier.fillMaxWidth()
            )

            CampoSecreto(
                valor = contrasena,
                alCambiar = { contrasena = it },
                etiqueta = stringResource(R.string.editor_contrasena)
            )
            MedidorFuerza(contrasena)

            OutlinedButton(
                onClick = { generando = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_generar))
            }

            OutlinedTextField(
                value = sitio,
                onValueChange = { sitio = it },
                label = { Text(stringResource(R.string.editor_sitio)) },
                singleLine = true,
                keyboardOptions = SIN_APRENDIZAJE,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text(stringResource(R.string.editor_notas)) },
                minLines = 3,
                keyboardOptions = NOTAS,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                stringResource(R.string.editor_nota_pie),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
