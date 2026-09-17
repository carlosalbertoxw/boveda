package com.carlosalbertoxw.boveda.ui

import com.carlosalbertoxw.boveda.Textos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lo que en Android daba `ViewModel`: un alcance de corrutinas que vive lo que
 * vive la pantalla, un indicador de "trabajando" y el patron de lanzar una
 * operacion contando cualquier fallo en la barra de avisos en vez de tumbar la
 * app.
 */
abstract class Modelo(private val avisos: Avisos) {

    protected val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    protected fun lanza(bloque: suspend () -> Unit) {
        alcance.launch {
            _ocupado.value = true
            try {
                bloque()
            } catch (e: Exception) {
                avisos.muestra(e.message ?: Textos.AVISO_GENERICO)
            } finally {
                _ocupado.value = false
            }
        }
    }

    /** El `onCleared` de Android: se llama cuando la pantalla sale de la composicion. */
    open fun libera() {
        alcance.cancel()
    }
}
