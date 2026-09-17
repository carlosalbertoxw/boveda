package com.carlosalbertoxw.boveda.escritorio

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window

/**
 * El `FLAG_SECURE` de escritorio.
 *
 * En Android una bandera bloquea capturas, grabacion y la miniatura de
 * recientes. En Windows 10 (2004) y posteriores lo mas parecido es
 * `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)`: la ventana sale en negro
 * —o no sale— en capturas, grabaciones, Recortes, Teams y la vista de tareas.
 * En versiones anteriores se cae a `WDA_MONITOR`, que la pinta en negro.
 *
 * En macOS y Linux no se aplica nada. macOS tiene un equivalente
 * (`NSWindow.sharingType`), pero desde la JVM no hay una forma estable de
 * llegar a la NSWindow de una ventana de AWT; y en X11/Wayland no existe tal
 * cosa. Mejor decirlo que fingirlo: ver el README.
 */
object ProteccionPantalla {

    private const val WDA_MONITOR = 0x01
    private const val WDA_EXCLUDEFROMCAPTURE = 0x11

    @Suppress("FunctionName")
    private interface User32Afinidad : StdCallLibrary {
        fun SetWindowDisplayAffinity(ventana: WinDef.HWND, afinidad: Int): Boolean
    }

    private val esWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private val user32: User32Afinidad? by lazy {
        if (!esWindows) return@lazy null
        runCatching {
            Native.load("user32", User32Afinidad::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }

    /** Devuelve si quedo aplicada. La ventana tiene que estar ya visible. */
    fun aplica(ventana: Window): Boolean {
        val biblioteca = user32 ?: return false
        return runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(ventana))
            biblioteca.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE) ||
                biblioteca.SetWindowDisplayAffinity(hwnd, WDA_MONITOR)
        }.getOrDefault(false)
    }
}
