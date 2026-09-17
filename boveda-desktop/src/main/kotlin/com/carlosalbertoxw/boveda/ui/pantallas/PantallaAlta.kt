package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

private const val LARGO_MINIMO = 10

@Composable
fun PantallaAlta(modelo: ModeloBoveda) {
    var contrasena by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    val ocupado by modelo.ocupado.collectAsState()

    val coincide = contrasena.isNotEmpty() && contrasena == confirmacion
    val suficiente = contrasena.length >= LARGO_MINIMO
    val puedeCrear = coincide && suficiente && !ocupado

    ColumnaDesplazable(Modifier.fillMaxSize(), relleno = 24.dp) {
        Text(Textos.ALTA_TITULO, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            Textos.ALTA_EXPLICACION,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        CampoSecreto(
            valor = contrasena,
            alCambiar = { contrasena = it },
            etiqueta = Textos.CONTRASENA_MAESTRA
        )
        Spacer(Modifier.height(8.dp))
        MedidorFuerza(contrasena)

        Spacer(Modifier.height(16.dp))

        CampoSecreto(
            valor = confirmacion,
            alCambiar = { confirmacion = it },
            etiqueta = Textos.ALTA_REPITE,
            alEnviar = { if (puedeCrear) modelo.crea(contrasena) }
        )

        Spacer(Modifier.height(8.dp))
        when {
            !suficiente && contrasena.isNotEmpty() -> Aviso(Textos.altaMuyCorta(LARGO_MINIMO))
            confirmacion.isNotEmpty() && !coincide -> Aviso(Textos.ALTA_NO_COINCIDEN)
        }

        Spacer(Modifier.height(24.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    Textos.ALTA_SIN_RECUPERACION_TITULO,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Textos.ALTA_SIN_RECUPERACION,
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
            Text(if (ocupado) Textos.ALTA_CREANDO else Textos.ALTA_CREAR)
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
