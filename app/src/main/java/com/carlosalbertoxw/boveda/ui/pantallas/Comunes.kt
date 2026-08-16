package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.carlosalbertoxw.boveda.seguridad.Generador
import com.carlosalbertoxw.boveda.seguridad.Kdf

/**
 * Campo de contrasena. Oculto por omision y con teclado de tipo Password, que
 * es lo que le pide al teclado que no guarde lo escrito en su diccionario de
 * autocorreccion ni lo mande a sus servidores de prediccion.
 */
@Composable
fun CampoSecreto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    alEnviar: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { alEnviar?.invoke() },
            onGo = { alEnviar?.invoke() }
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Ocultar" else "Mostrar"
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Medidor de fuerza. Mide entropia aproximada, no si la contrasena "parece"
 * buena: 20 caracteres en minusculas resisten mucho mas que 8 con simbolos, y
 * el medidor debe decirlo aunque suene contraintuitivo.
 */
@Composable
fun MedidorFuerza(contrasena: String, modifier: Modifier = Modifier) {
    val bits = remember(contrasena) { Kdf.fuerza(contrasena) }
    val (texto, color) = when {
        contrasena.isEmpty() -> "" to Color.Gray
        bits < 45 -> "Debil: se rompe en horas" to Color(0xFFE2564E)
        bits < 70 -> "Aceptable" to Color(0xFFF0B44C)
        bits < 100 -> "Fuerte" to Color(0xFF4CAF50)
        else -> "Excelente" to Color(0xFF4CAF50)
    }

    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (bits / 120f).coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
        if (texto.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "$texto · ~$bits bits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DialogoGenerador(alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    var largo by remember { mutableStateOf(20f) }
    var mayusculas by remember { mutableStateOf(true) }
    var digitos by remember { mutableStateOf(true) }
    var simbolos by remember { mutableStateOf(true) }

    val ajustes = Generador.Ajustes(largo.toInt(), mayusculas, digitos, simbolos)
    var propuesta by remember { mutableStateOf(Generador.genera(ajustes)) }

    // Cambiar el largo o los grupos rehace la propuesta al vuelo: si no, el
    // usuario mueve el deslizador y ve una contrasena que ya no corresponde.
    LaunchedEffect(largo, mayusculas, digitos, simbolos) {
        propuesta = Generador.genera(ajustes)
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Generar contrasena") },
        text = {
            Column {
                Text(
                    propuesta,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                MedidorFuerza(propuesta)
                Spacer(Modifier.height(16.dp))

                Text("Largo: ${largo.toInt()}")
                Slider(
                    value = largo,
                    onValueChange = { largo = it },
                    valueRange = 8f..64f,
                    steps = 55
                )

                Interruptor("Mayusculas", mayusculas) { mayusculas = it }
                Interruptor("Digitos", digitos) { digitos = it }
                Interruptor("Simbolos", simbolos) { simbolos = it }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { propuesta = Generador.genera(ajustes) }) {
                    Text("Generar otra")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { alElegir(propuesta); alCerrar() }) { Text("Usar") }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text("Cancelar") }
        }
    )
}

@Composable
fun Interruptor(etiqueta: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta)
        Switch(checked = valor, onCheckedChange = alCambiar)
    }
}
