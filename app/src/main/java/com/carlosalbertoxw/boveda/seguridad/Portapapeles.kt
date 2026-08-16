package com.carlosalbertoxw.boveda.seguridad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * El portapapeles es el punto mas debil de cualquier gestor de contrasenas: es
 * el unico lugar donde el secreto sale de la app, y en Android es un buzon
 * compartido. Aqui se acota por tres lados.
 *
 *  1. Se marca el contenido como sensible, para que Android 13+ no lo muestre en
 *     la vista previa que aparece al copiar.
 *  2. Se borra solo a los pocos segundos, en vez de quedarse hasta el siguiente
 *     copiado.
 *  3. Antes de borrar se comprueba que lo que hay siga siendo nuestro, para no
 *     pisarle al usuario algo que copio despues en otra app.
 *
 * Lo que no se puede evitar desde aqui: desde Android 10 ninguna app en segundo
 * plano puede leer el portapapeles, pero un teclado activo si. Por eso conviene
 * pegar la contrasena y no dejarla ahi.
 */
object Portapapeles {

    /**
     * Es el valor de ClipDescription.EXTRA_IS_SENSITIVE, que solo existe como
     * constante desde API 33. Escrito a mano funciona igual en todas las
     * versiones: las viejas ignoran la clave y las nuevas la respetan.
     */
    private const val ETIQUETA_SENSIBLE = "android.content.extra.IS_SENSITIVE"

    private var borradoPendiente: Job? = null
    private var ultimoCopiado: String? = null

    fun copia(
        contexto: Context,
        etiqueta: String,
        texto: String,
        alcance: CoroutineScope,
        segundos: Int
    ) {
        val gestor = contexto.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(etiqueta, texto).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ETIQUETA_SENSIBLE, true)
            }
        }
        gestor.setPrimaryClip(clip)
        ultimoCopiado = texto

        borradoPendiente?.cancel()
        if (segundos <= 0) return
        borradoPendiente = alcance.launch {
            delay(segundos * 1000L)
            limpiaSiSigueSiendoNuestro(contexto, texto)
        }
    }

    /**
     * Borra lo ultimo que copio la boveda, si sigue ahi. Se llama al bloquear y
     * al irse a segundo plano.
     *
     * Es "mejor esfuerzo": desde Android 10 una app que ya perdio el foco no
     * puede tocar el portapapeles, asi que si el sistema nos saca demasiado
     * rapido el borrado no pasa. Por eso existe ademas el temporizador, que si
     * corre mientras la app esta al frente.
     */
    fun limpiaTodo(contexto: Context) {
        ultimoCopiado?.let { limpiaSiSigueSiendoNuestro(contexto, it) }
        ultimoCopiado = null
    }

    fun limpiaSiSigueSiendoNuestro(contexto: Context, texto: String) {
        val gestor = contexto.getSystemService(ClipboardManager::class.java) ?: return
        val actual = runCatching {
            gestor.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }.getOrNull()
        if (actual != texto) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { gestor.clearPrimaryClip() }
        } else {
            // API 26-27 no tiene clearPrimaryClip: se pisa con un texto vacio.
            runCatching { gestor.setPrimaryClip(ClipData.newPlainText("", "")) }
        }
    }

    fun cancelaPendiente() {
        borradoPendiente?.cancel()
        borradoPendiente = null
    }
}
