package com.carlosalbertoxw.boveda.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El buzon de mensajes para el usuario, uno solo para toda la app.
 *
 * Vive colgado de la aplicacion y no de un modelo de vista concreto porque hay
 * mas de un modelo que tiene cosas que decir —el candado y el respaldo— y una
 * sola barra de avisos abajo que las muestra. Con esto, quien informa no
 * necesita saber quien pinta.
 */
class Avisos {

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun muestra(texto: String) { _mensaje.value = texto }

    fun limpia() { _mensaje.value = null }
}
