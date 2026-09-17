package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.seguridad.Generador
import com.carlosalbertoxw.boveda.seguridad.Kdf
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Segundos que una contrasena descubierta aguanta antes de taparse sola.
 *
 * Hay dos sitios donde se puede descubrir una —el detalle y el historial— y las
 * dos tienen que taparse con el mismo criterio: descubrir una contrasena es un
 * acto deliberado y momentaneo, no un estado en el que se deje la pantalla.
 */
const val SEGUNDOS_VISIBLE = 20

private val formatoFecha: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es"))

fun fecha(milisegundos: Long): String = synchronized(formatoFecha) { formatoFecha.format(Date(milisegundos)) }

/**
 * Un dialogo de confirmacion. En escritorio se pinta dentro de la misma ventana,
 * asi que hereda la proteccion contra capturas que se le puso a esta.
 */
@Composable
fun DialogoSeguro(
    alCerrar: () -> Unit,
    titulo: @Composable () -> Unit,
    contenido: @Composable () -> Unit,
    botonConfirmar: @Composable () -> Unit,
    botonCancelar: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = titulo,
        text = contenido,
        confirmButton = botonConfirmar,
        dismissButton = botonCancelar,
        modifier = Modifier.widthIn(max = 520.dp)
    )
}

/**
 * Una columna que se desplaza y lo dice. En el telefono el contenido que sigue
 * se intuye al deslizar; en escritorio, sin barra visible, una pantalla mas
 * alta que la ventana parece cortada y lo de abajo no existe para quien mira.
 */
@Composable
fun ColumnaDesplazable(
    modifier: Modifier = Modifier,
    espaciado: Dp = 0.dp,
    relleno: Dp = 16.dp,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val desplazamiento = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(desplazamiento)
                .padding(start = relleno, top = relleno, bottom = relleno, end = relleno + ANCHO_BARRA),
            verticalArrangement = Arrangement.spacedBy(espaciado),
            content = contenido
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(desplazamiento),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

/** Para las listas: la misma barra, sobre un LazyListState. */
@Composable
fun BoxScope.BarraDeLista(estado: LazyListState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(estado),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
    )
}

private val ANCHO_BARRA = 8.dp

@Composable
fun BotonVolver(alVolver: () -> Unit) {
    IconButton(onClick = alVolver) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Textos.VOLVER)
    }
}

/** Enter en un campo de una linea: lo que en el telefono era la tecla de accion del teclado. */
fun Modifier.alPulsarEnter(accion: (() -> Unit)?): Modifier =
    if (accion == null) {
        this
    } else {
        onPreviewKeyEvent { evento ->
            val esEnter = evento.key == Key.Enter || evento.key == Key.NumPadEnter
            if (esEnter && evento.type == KeyEventType.KeyDown) {
                accion()
                true
            } else {
                false
            }
        }
    }

/**
 * Campo de contrasena. Oculto por omision; el tipo Password le dice a los
 * metodos de entrada que no aprendan ni sugieran lo que se escribe.
 */
@Composable
fun CampoSecreto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
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
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) Textos.OCULTAR else Textos.MOSTRAR
                )
            }
        },
        modifier = modifier.fillMaxWidth().alPulsarEnter(alEnviar)
    )
}

/**
 * Medidor de fuerza. Mide entropia aproximada, no si la contrasena "parece"
 * buena: 20 caracteres en minusculas resisten mucho mas que 8 con simbolos.
 */
@Composable
fun MedidorFuerza(contrasena: String, modifier: Modifier = Modifier) {
    val bits = remember(contrasena) { Kdf.fuerza(contrasena) }
    val (etiqueta, color) = when {
        contrasena.isEmpty() -> null to Color.Gray
        bits < 45 -> Textos.FUERZA_DEBIL to Color(0xFFE2564E)
        bits < 70 -> Textos.FUERZA_ACEPTABLE to Color(0xFFF0B44C)
        bits < 100 -> Textos.FUERZA_FUERTE to Color(0xFF4CAF50)
        else -> Textos.FUERZA_EXCELENTE to Color(0xFF4CAF50)
    }

    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (bits / 120f).coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
        if (etiqueta != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                Textos.fuerzaBits(etiqueta, bits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DialogoGenerador(alElegir: (String) -> Unit, alCerrar: () -> Unit) {
    var largo by remember { mutableFloatStateOf(20f) }
    var mayusculas by remember { mutableStateOf(true) }
    var digitos by remember { mutableStateOf(true) }
    var simbolos by remember { mutableStateOf(true) }

    val ajustes = Generador.Ajustes(largo.toInt(), mayusculas, digitos, simbolos)
    var propuesta by remember { mutableStateOf(Generador.genera(ajustes)) }

    // Cambiar el largo o los grupos rehace la propuesta al vuelo: si no, el
    // usuario mueve el deslizador y ve una contrasena que ya no corresponde.
    LaunchedEffect(largo.toInt(), mayusculas, digitos, simbolos) {
        propuesta = Generador.genera(ajustes)
    }

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text(Textos.GENERADOR_TITULO) },
        contenido = {
            Column {
                Text(
                    propuesta,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                MedidorFuerza(propuesta)
                Spacer(Modifier.height(16.dp))

                Text(Textos.generadorLargo(largo.toInt()))
                Slider(
                    value = largo,
                    onValueChange = { largo = it },
                    valueRange = 8f..64f,
                    steps = 55
                )

                Interruptor(Textos.GENERADOR_MAYUSCULAS, mayusculas) { mayusculas = it }
                Interruptor(Textos.GENERADOR_DIGITOS, digitos) { digitos = it }
                Interruptor(Textos.GENERADOR_SIMBOLOS, simbolos) { simbolos = it }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { propuesta = Generador.genera(ajustes) }) {
                    Text(Textos.GENERADOR_OTRA)
                }
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alElegir(propuesta); alCerrar() }) { Text(Textos.GENERADOR_USAR) }
        },
        botonCancelar = {
            TextButton(onClick = alCerrar) { Text(Textos.CANCELAR) }
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
