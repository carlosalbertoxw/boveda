package com.carlosalbertoxw.boveda.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.datos.GestorRespaldo

/**
 * Exportar e importar, aparte del modelo principal.
 *
 * Es la unica parte de la app que habla con el mundo exterior y la que mas
 * pasos tiene —elegir archivo, pedir dos contrasenas, decidir fusionar o
 * reemplazar—, asi que se lleva su propio estado en vez de engordar el modelo
 * del candado. Los mensajes van al buzon compartido ([Avisos]), que es lo que
 * permite que la barra de abajo siga siendo una sola.
 */
class ModeloRespaldo(app: Application) : AndroidViewModel(app) {

    private val aplicacion = AplicacionBoveda.de(app)
    private val avisos = aplicacion.avisos
    private val respaldo = GestorRespaldo(app, aplicacion.gestor)

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    /**
     * Avisa al candado de que la salida al selector de archivos del sistema es
     * esperada. Sin esto, abrir el selector manda la app a segundo plano y el
     * cierre automatico la bloquearia a media exportacion.
     */
    fun permiteSalidaBreve() = aplicacion.permiteSalidaBreve()

    fun exporta(destino: Uri, maestra: String, contrasenaArchivo: String) = lanza {
        val m = maestra.toCharArray()
        val c = contrasenaArchivo.toCharArray()
        try {
            avisos.muestra(mensajeDe(respaldo.exporta(destino, m, c)))
        } finally {
            m.fill(' ')
            c.fill(' ')
        }
    }

    fun importa(origen: Uri, contrasenaArchivo: String, reemplazar: Boolean) = lanza {
        val c = contrasenaArchivo.toCharArray()
        try {
            avisos.muestra(mensajeDe(respaldo.importa(origen, c, reemplazar)))
        } finally {
            c.fill(' ')
        }
    }

    private fun lanza(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _ocupado.value = true
            try {
                bloque()
            } catch (e: Exception) {
                avisos.muestra(e.message ?: "Algo salio mal.")
            } finally {
                _ocupado.value = false
            }
        }
    }
}
