package com.carlosalbertoxw.boveda.seguridad

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Envoltorio de BiometricPrompt.
 *
 * Siempre con CryptoObject. Un BiometricPrompt sin CryptoObject solo devuelve un
 * "si" o un "no" que la app decide creerse, y eso se salta parcheando el binario
 * o enganchandose al proceso; con CryptoObject el resultado util no es el "si",
 * sino un Cipher que el hardware desbloqueo. Sin huella valida no hay Cipher, y
 * sin Cipher no hay frase.
 *
 * Solo BIOMETRIC_STRONG (clase 3). Se deja fuera el reconocimiento facial debil
 * de muchos equipos, que se enganan con una foto, y tambien el PIN del sistema:
 * quien conozca el PIN del telefono no debe por eso poder abrir la boveda.
 */
object Biometria {

    private const val TIPO = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun disponible(contexto: Context): Boolean =
        BiometricManager.from(contexto).canAuthenticate(TIPO) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun pide(
        actividad: FragmentActivity,
        cifrador: Cipher,
        titulo: String,
        subtitulo: String,
        alExito: (Cipher) -> Unit,
        alFallo: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            actividad,
            ContextCompat.getMainExecutor(actividad),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                    val autenticado = resultado.cryptoObject?.cipher
                    if (autenticado == null) alFallo("El sistema no devolvio la llave.")
                    else alExito(autenticado)
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    // Cancelar no es un error que valga la pena gritar.
                    if (codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        codigo == BiometricPrompt.ERROR_USER_CANCELED ||
                        codigo == BiometricPrompt.ERROR_CANCELED
                    ) return
                    alFallo(mensaje.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText("Usar contrasena")
            .setAllowedAuthenticators(TIPO)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cifrador))
    }
}
