package com.carlosalbertoxw.boveda.ui

import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import java.nio.file.Path

/**
 * Exportar e importar, aparte del modelo principal.
 *
 * Es la unica parte de la app que habla con archivos de fuera y la que mas
 * pasos tiene —elegir archivo, pedir dos contrasenas, decidir fusionar o
 * reemplazar—, asi que se lleva su propio estado. Los mensajes van al buzon
 * compartido ([Avisos]).
 */
class ModeloRespaldo(private val aplicacion: AplicacionBoveda) : Modelo(aplicacion.avisos) {

    private val avisos = aplicacion.avisos
    private val respaldo = GestorRespaldo(aplicacion.gestor)

    /**
     * Avisa al candado de que perder el foco por el selector de archivos es
     * esperado. Sin esto, con "al instante" la boveda se cerraria al abrirlo.
     */
    fun permiteSalidaBreve() = aplicacion.permiteSalidaBreve()

    fun exporta(destino: Path, maestra: String, contrasenaArchivo: String) = lanza {
        val m = maestra.toCharArray()
        val c = contrasenaArchivo.toCharArray()
        try {
            avisos.muestra(mensajeDe(respaldo.exporta(destino, m, c)))
        } finally {
            m.fill(' ')
            c.fill(' ')
        }
    }

    fun importa(origen: Path, contrasenaArchivo: String, reemplazar: Boolean) = lanza {
        val c = contrasenaArchivo.toCharArray()
        try {
            avisos.muestra(mensajeDe(respaldo.importa(origen, c, reemplazar)))
        } finally {
            c.fill(' ')
        }
    }
}
