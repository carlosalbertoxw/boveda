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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.carlosalbertoxw.boveda.seguridad.AlmacenBiometrico
import com.carlosalbertoxw.boveda.seguridad.Biometria
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@Composable
fun PantallaDesbloqueo(modelo: ModeloBoveda) {
    val contexto = LocalContext.current
    val actividad = contexto as? FragmentActivity
    var contrasena by remember { mutableStateOf("") }
    val ocupado by modelo.ocupado.collectAsStateWithLifecycle()

    var espera by remember { mutableIntStateOf(modelo.gestor.segundosDeEspera()) }
    // Cuenta atras del castigo por intentos fallidos, para que el boton no se
    // vea muerto sin explicacion. Se relee en bucle en vez de descontar en
    // memoria: la espera vive en preferencias y debe sobrevivir a que el
    // usuario cierre y reabra la app para volver a intentar.
    LaunchedEffect(Unit) {
        while (true) {
            espera = modelo.gestor.segundosDeEspera()
            delay(500)
        }
    }

    val conHuella = modelo.gestor.prefs.biometricoActivo && Biometria.disponible(contexto)

    fun pideHuella() {
        val act = actividad ?: return
        val sobre = modelo.gestor.prefs.sobreBiometrico ?: return
        val cifrador = AlmacenBiometrico.cifradorParaLeer(sobre)
        if (cifrador == null) {
            // La llave del Keystore ya no sirve: casi siempre porque se
            // registro una huella nueva en el telefono, que la invalida a
            // proposito. Queda la contrasena maestra.
            modelo.gestor.prefs.apagaBiometrico()
            modelo.avisa("El atajo de huella se invalido. Usa tu contrasena maestra.")
            return
        }
        Biometria.pide(
            actividad = act,
            cifrador = cifrador,
            titulo = "Abrir la boveda",
            subtitulo = "Confirma tu identidad",
            alExito = { modelo.abreConBiometrico(it) },
            alFallo = { modelo.avisa(it) }
        )
    }

    // Si el atajo esta activo, se ofrece de entrada: es el camino normal.
    LaunchedEffect(conHuella) {
        if (conHuella && espera == 0) pideHuella()
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
        Text("Boveda cerrada", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(32.dp))

        CampoSecreto(
            valor = contrasena,
            alCambiar = { contrasena = it },
            etiqueta = "Contrasena maestra",
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
                    espera > 0 -> "Espera $espera s"
                    ocupado -> "Abriendo..."
                    else -> "Abrir"
                }
            )
        }

        if (conHuella) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { pideHuella() },
                enabled = espera == 0 && !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Usar huella")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Descifrar la boveda tarda un momento a proposito: cada intento cuesta " +
                "el mismo trabajo, y eso es lo que hace inviable probar contrasenas en serie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
