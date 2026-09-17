package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@Composable
fun PantallaDesbloqueo(modelo: ModeloBoveda) {
    var contrasena by remember { mutableStateOf("") }
    val ocupado by modelo.ocupado.collectAsState()

    // Cuenta atras del castigo por intentos fallidos, para que el boton no se
    // vea muerto sin explicacion.
    val espera by modelo.esperaSegundos.collectAsState()

    // En escritorio no hay huella que ofrecer de entrada: el camino normal es
    // teclear, asi que el cursor ya espera en el campo.
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    val puedeAbrir = contrasena.isNotEmpty() && espera == 0 && !ocupado

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(Textos.DESBLOQUEO_TITULO, style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(32.dp))

        CampoSecreto(
            valor = contrasena,
            alCambiar = { contrasena = it },
            etiqueta = Textos.CONTRASENA_MAESTRA,
            modifier = Modifier.focusRequester(foco),
            alEnviar = { if (puedeAbrir) modelo.abre(contrasena) }
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { modelo.abre(contrasena) },
            enabled = puedeAbrir,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    espera > 0 -> Textos.desbloqueoEspera(espera)
                    ocupado -> Textos.DESBLOQUEO_ABRIENDO
                    else -> Textos.DESBLOQUEO_ABRIR
                }
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            Textos.DESBLOQUEO_EXPLICACION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
