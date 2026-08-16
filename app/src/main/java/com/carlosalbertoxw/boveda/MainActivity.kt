package com.carlosalbertoxw.boveda

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.carlosalbertoxw.boveda.ui.RaizBoveda
import com.carlosalbertoxw.boveda.ui.tema.TemaBoveda

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE antes de que exista contenido que capturar. Bloquea las
        // capturas de pantalla, la grabacion, la miniatura que sale en la lista
        // de apps recientes y el volcado a pantallas externas no seguras.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        super.onCreate(savedInstanceState)

        // Ignora los toques que llegan a traves de una ventana sobrepuesta.
        // Es la defensa contra tapjacking: una app con permiso de dibujar encima
        // podria tapar la pantalla y hacerte pulsar "mostrar contrasena" o
        // "borrar boveda" creyendo que pulsas otra cosa.
        window.decorView.filterTouchesWhenObscured = true

        // Nada de esta pantalla se ofrece al servicio de autocompletado del
        // sistema. Si no, otro gestor de contrasenas instalado podria pedir
        // guardar lo que aqui se escribe, y los secretos saldrian de la app por
        // la puerta de servicio.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        setContent {
            TemaBoveda(oscuro = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RaizBoveda()
                }
            }
        }

        // El vigilante de la inactividad: cierra la boveda si la app se queda
        // abierta encima de la mesa. Vive fuera de la composicion y atado al
        // ciclo de vida, asi que solo despierta mientras la pantalla se ve;
        // antes seguia corriendo tambien en segundo plano, donde el cierre ya
        // lo decide AplicacionBoveda por el ciclo de vida del proceso.
        val aplicacion = AplicacionBoveda.de(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(15_000)
                    if (!aplicacion.gestor.abierta) continue
                    if (aplicacion.caducoPorInactividad()) aplicacion.gestor.bloquea()
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        AplicacionBoveda.de(this).registraToque()
    }
}
