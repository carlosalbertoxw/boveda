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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@Composable
fun PantallaDesbloqueo(modelo: ModeloBoveda) {
    val contexto = LocalContext.current
    val actividad = contexto as? FragmentActivity
    var contrasena by remember { mutableStateOf("") }
    val ocupado by modelo.ocupado.collectAsStateWithLifecycle()

    // Cuenta atras del castigo por intentos fallidos, para que el boton no se
    // vea muerto sin explicacion. El bucle que la mantiene al dia vive en el
    // modelo y solo corre mientras esta pantalla lo observa.
    val espera by modelo.esperaSegundos.collectAsStateWithLifecycle()

    // Se resuelve una sola vez: preguntarlo abre el Keystore, y antes se hacia
    // en cada recomposicion, es decir con cada tecla del campo de contrasena.
    val conHuella = remember { modelo.puedeOfrecerHuella() }

    // Si el atajo esta activo, se ofrece de entrada: es el camino normal.
    LaunchedEffect(conHuella) {
        if (conHuella && espera == 0 && actividad != null) {
            modelo.desbloqueaConHuella(actividad)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
        Text(
            stringResource(R.string.desbloqueo_titulo),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(32.dp))

        CampoSecreto(
            valor = contrasena,
            alCambiar = { contrasena = it },
            etiqueta = stringResource(R.string.contrasena_maestra),
            imeAction = ImeAction.Go,
            alEnviar = { if (espera == 0 && !ocupado) modelo.abre(contrasena) }
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { modelo.abre(contrasena) },
            enabled = contrasena.isNotEmpty() && espera == 0 && !ocupado,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    espera > 0 -> stringResource(R.string.desbloqueo_espera, espera)
                    ocupado -> stringResource(R.string.desbloqueo_abriendo)
                    else -> stringResource(R.string.desbloqueo_abrir)
                }
            )
        }

        if (conHuella && actividad != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { modelo.desbloqueaConHuella(actividad) },
                enabled = espera == 0 && !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.desbloqueo_usar_huella))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.desbloqueo_explicacion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
