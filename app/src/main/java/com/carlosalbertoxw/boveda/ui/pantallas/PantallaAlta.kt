package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

private const val LARGO_MINIMO = 10

@Composable
fun PantallaAlta(modelo: ModeloBoveda) {
    var contrasena by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    val ocupado by modelo.ocupado.collectAsStateWithLifecycle()

    val coincide = contrasena.isNotEmpty() && contrasena == confirmacion
    val suficiente = contrasena.length >= LARGO_MINIMO
    val puedeCrear = coincide && suficiente && !ocupado

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Crea tu contrasena maestra", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Es la unica llave de la boveda. Todo lo que guardes se cifra con ella.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        CampoSecreto(
            valor = contrasena,
            alCambiar = { contrasena = it },
            etiqueta = "Contrasena maestra"
        )
        Spacer(Modifier.height(8.dp))
        MedidorFuerza(contrasena)

        Spacer(Modifier.height(16.dp))

        CampoSecreto(
            valor = confirmacion,
            alCambiar = { confirmacion = it },
            etiqueta = "Repitela",
            imeAction = ImeAction.Done,
            alEnviar = { if (puedeCrear) modelo.crea(contrasena) }
        )

        Spacer(Modifier.height(8.dp))
        when {
            !suficiente && contrasena.isNotEmpty() ->
                Aviso("Usa al menos $LARGO_MINIMO caracteres.")
            confirmacion.isNotEmpty() && !coincide ->
                Aviso("Las dos contrasenas no coinciden.")
        }

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "No hay forma de recuperarla",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Esta app no guarda tu contrasena maestra en ningun lado ni la manda " +
                        "a ningun servidor: por eso nadie mas puede leer tu boveda. La otra " +
                        "cara de esa moneda es que si la olvidas, los datos se pierden. " +
                        "Apuntala en un lugar fisico seguro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { modelo.crea(contrasena) },
            enabled = puedeCrear,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ocupado) "Creando..." else "Crear boveda")
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
