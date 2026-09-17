package com.carlosalbertoxw.boveda.seguridad

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * La llave del atajo biometrico, guardada en el Keystore de hardware.
 *
 * Aqui esta la diferencia entre "la huella abre la app" y "la huella abre la
 * boveda". Esta llave nace con setUserAuthenticationRequired(true) y validez de
 * un solo uso: el hardware seguro se niega a operar con ella si no hubo una
 * autenticacion biometrica valida en ese mismo instante. No es la app la que
 * decide si la huella fue correcta —eso se podria saltar parcheando el APK—
 * sino el chip, que sin autenticacion simplemente no descifra.
 *
 * Ademas se marca setInvalidatedByBiometricEnrollment(true): si alguien registra
 * una huella nueva en el telefono, el sistema destruye esta llave. El atajo deja
 * de funcionar y solo queda la contrasena maestra. Es a proposito: sin eso, meter
 * tu huella en el telefono ajeno bastaria para abrir la boveda.
 */
object AlmacenBiometrico {

    private const val ALIAS = "boveda_desbloqueo_biometrico"
    private const val PROVEEDOR = "AndroidKeyStore"

    private fun almacen(): KeyStore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }

    fun existeLlave(): Boolean = runCatching { almacen().containsAlias(ALIAS) }.getOrDefault(false)

    fun borraLlave() {
        runCatching { almacen().deleteEntry(ALIAS) }
    }

    private fun llave(): SecretKey? =
        runCatching { (almacen().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey }
            .getOrNull()

    /**
     * Crea la llave desde cero. Se borra la anterior primero para que activar el
     * atajo dos veces no deje una llave huerfana en el hardware.
     */
    private fun creaLlave(contexto: Context): SecretKey {
        borraLlave()
        val conStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        return try {
            genera(conStrongBox)
        } catch (_: StrongBoxUnavailableException) {
            // Algunos equipos anuncian StrongBox y luego lo rechazan para AES.
            // Se cae al Keystore normal, que sigue siendo respaldado por TEE.
            genera(false)
        }
    }

    private fun genera(strongBox: Boolean): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // timeout 0 = hay que autenticarse para cada operacion.
                    // Solo biometria fuerte: se descarta el reconocimiento
                    // facial de clase 2 y el PIN del dispositivo.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
        generador.init(spec)
        return generador.generateKey()
    }

    /**
     * Cifrador para ENVOLVER la frase al activar el atajo. Devuelve el Cipher
     * sin usar: hay que pasarlo por BiometricPrompt y solo despues llamar a
     * doFinal, porque el hardware exige la autenticacion entre el init y el uso.
     */
    fun cifradorParaGuardar(contexto: Context): Cipher {
        val cifrador = Cipher.getInstance(Sobre.TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, creaLlave(contexto))
        return cifrador
    }

    /**
     * Cifrador para DESENVOLVER. Devuelve null si la llave ya no existe o quedo
     * invalidada porque cambiaron las huellas del equipo; en ese caso el atajo
     * se apaga y solo queda la contrasena maestra.
     */
    fun cifradorParaLeer(sobre: ByteArray): Cipher? {
        val secreta = llave() ?: return null
        return runCatching {
            Cipher.getInstance(Sobre.TRANSFORMACION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secreta,
                    GCMParameterSpec(Sobre.BITS_ETIQUETA, sobre, 0, Sobre.BYTES_IV)
                )
            }
        }.getOrNull()
    }
}
