package com.carlosalbertoxw.boveda.escritorio

import com.carlosalbertoxw.boveda.Textos
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/**
 * El selector de archivos nativo del sistema, el equivalente del SAF de Android.
 *
 * La app no recorre carpetas ni decide donde escribir: el usuario senala un
 * archivo concreto y la app solo toca ese. Se usa `FileDialog` y no
 * `JFileChooser` porque el primero abre el dialogo propio de Windows y macOS,
 * con sus accesos directos y su confirmacion de sobreescritura.
 */
object SelectorArchivos {

    fun eligeDestino(ventana: Frame?, nombreSugerido: String): Path? {
        val dialogo = FileDialog(ventana, Textos.SELECTOR_EXPORTAR, FileDialog.SAVE)
        dialogo.file = nombreSugerido
        dialogo.isVisible = true
        val archivo = dialogo.file ?: return null
        val directorio = dialogo.directory ?: return null
        // Algunos sistemas no agregan la extension si el usuario la borro.
        val nombre = if (archivo.contains('.')) archivo else "$archivo.json"
        return Path.of(directorio, nombre)
    }

    fun eligeOrigen(ventana: Frame?): Path? {
        val dialogo = FileDialog(ventana, Textos.SELECTOR_IMPORTAR, FileDialog.LOAD)
        // Sin filtro de extension a proposito: segun de donde venga el archivo
        // (una descarga, un chat, una memoria USB) puede llegar sin ".json" y
        // saldria en gris, imposible de elegir. El contenido se valida al leerlo.
        dialogo.isVisible = true
        val archivo = dialogo.file ?: return null
        val directorio = dialogo.directory ?: return null
        return Path.of(directorio, archivo)
    }
}
