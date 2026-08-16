package com.carlosalbertoxw.boveda

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import com.carlosalbertoxw.boveda.ui.RaizBoveda
import com.carlosalbertoxw.boveda.ui.tema.TemaBoveda

class MainActivity : AppCompatActivity() {

    /**
     * Ultimo toque del usuario. Sirve para el bloqueo por inactividad con la app
     * al frente: dejar la boveda abierta encima de la mesa es tan malo como
     * dejarla abierta en segundo plano.
     */
    @Volatile
    private var ultimoToque = SystemClock.elapsedRealtime()

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

            val aplicacion = AplicacionBoveda.de(this)
            val gestor = aplicacion.gestor
            LaunchedEffect(Unit) {
                while (true) {
                    delay(15_000)
                    if (!gestor.abierta) continue
                    if (aplicacion.salidaPermitida) {
                        // El selector de archivos esta al frente: no hay toques
                        // que registrar, y eso no es haber dejado la app sola.
                        ultimoToque = SystemClock.elapsedRealtime()
                        continue
                    }
                    // Con "al instante" configurado, en primer plano se toma un
                    // minuto: cerrar la boveda mientras la miras seria absurdo.
                    val minutos = gestor.prefs.minutosAutobloqueo.coerceAtLeast(1)
                    if (SystemClock.elapsedRealtime() - ultimoToque >= minutos * 60_000L) {
                        gestor.bloquea()
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        ultimoToque = SystemClock.elapsedRealtime()
    }
}
