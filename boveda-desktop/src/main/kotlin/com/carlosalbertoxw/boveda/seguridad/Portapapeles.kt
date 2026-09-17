package com.carlosalbertoxw.boveda.seguridad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * El portapapeles es el punto mas debil de cualquier gestor de contrasenas: es
 * el unico lugar donde el secreto sale de la app, y en cualquier sistema es un
 * buzon compartido. Aqui se acota por tres lados, igual que en Android:
 *
 *  1. En Windows se marca el contenido para que no entre en el historial del
 *     portapapeles (Win+V) ni se sincronice con otros equipos. Es el
 *     equivalente de la marca de "sensible" de Android 13.
 *  2. Se borra solo a los pocos segundos, en vez de quedarse hasta el siguiente
 *     copiado.
 *  3. Antes de borrar se comprueba que lo que hay siga siendo nuestro, para no
 *     pisarle al usuario algo que copio despues en otra app.
 *
 * Una diferencia deliberada con Android: aqui **no** se limpia al perder el
 * foco. En el telefono el sistema ya no deja tocar el portapapeles desde segundo
 * plano, asi que alli era un gesto sin efecto; en escritorio si funcionaria, y
 * borraria la contrasena justo al cambiar al navegador para pegarla. Manda el
 * temporizador, que sigue corriendo con la ventana en segundo plano.
 */
object Portapapeles {

    private var borradoPendiente: Job? = null

    /**
     * Del ultimo texto copiado se guarda su SHA-256, no el texto: para
     * responder a "lo que hay en el portapapeles, ¿sigue siendo lo nuestro?"
     * comparar huellas vale igual que comparar cadenas, y asi no queda un
     * segundo ejemplar de la contrasena vivo en memoria.
     */
    @Volatile
    private var huellaUltimoCopiado: ByteArray? = null

    private val esWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun huella(texto: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8))

    private fun sistema(): Clipboard? =
        runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()

    fun copia(texto: String, alcance: CoroutineScope, segundos: Int): Boolean {
        val portapapeles = sistema() ?: return false
        // Otra app puede tener el portapapeles abierto en ese instante; en
        // Windows eso lanza IllegalStateException. Se reintenta un par de veces.
        val puesto = (1..3).any {
            runCatching { portapapeles.setContents(TransferibleSensible(texto), TransferibleSensible) }
                .onFailure { Thread.sleep(40) }
                .isSuccess
        }
        if (!puesto) return false

        val huella = huella(texto)
        huellaUltimoCopiado = huella

        borradoPendiente?.cancel()
        if (segundos <= 0) return true
        borradoPendiente = alcance.launch {
            delay(segundos * 1000L)
            limpiaSiEsNuestro(huella)
        }
        return true
    }

    /** Borra lo ultimo que copio la boveda, si sigue ahi. Se llama al bloquear y al salir. */
    fun limpiaTodo() {
        borradoPendiente?.cancel()
        borradoPendiente = null
        huellaUltimoCopiado?.let { limpiaSiEsNuestro(it) }
        huellaUltimoCopiado = null
    }

    private fun limpiaSiEsNuestro(huellaEsperada: ByteArray) {
        val portapapeles = sistema() ?: return
        val actual = runCatching {
            portapapeles.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull() ?: return
        if (!MessageDigest.isEqual(huella(actual), huellaEsperada)) return
        runCatching { portapapeles.setContents(TransferibleSensible(""), TransferibleSensible) }
        if (MessageDigest.isEqual(huellaUltimoCopiado ?: ByteArray(0), huellaEsperada)) {
            huellaUltimoCopiado = null
        }
    }

    /**
     * El texto, mas los formatos con los que Windows reconoce contenido que no
     * debe guardar: `ExcludeClipboardContentFromMonitorProcessing` (lo respetan
     * los visores del portapapeles), `CanIncludeInClipboardHistory = 0` (fuera
     * del historial Win+V) y `CanUploadToCloudClipboard = 0` (sin sincronizar).
     * Los tres son los mismos que usan KeePass y los gestores de Microsoft.
     */
    private class TransferibleSensible(private val texto: String) : Transferable {

        override fun getTransferDataFlavors(): Array<DataFlavor> =
            arrayOf(DataFlavor.stringFlavor) + FORMATOS_WINDOWS.keys

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            flavor == DataFlavor.stringFlavor || flavor in FORMATOS_WINDOWS

        override fun getTransferData(flavor: DataFlavor): Any = when {
            flavor == DataFlavor.stringFlavor -> texto
            flavor in FORMATOS_WINDOWS -> ByteArrayInputStream(CERO_DWORD)
            else -> throw UnsupportedFlavorException(flavor)
        }

        companion object : ClipboardOwner {
            override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) = Unit

            private val CERO_DWORD = ByteArray(4)

            /**
             * Cada formato nativo se ata a un DataFlavor propio. AWT registra el
             * nombre con RegisterClipboardFormat y publica los bytes tal cual.
             * Fuera de Windows no se declara ninguno: esos nombres no significan
             * nada en macOS ni en Linux.
             */
            val FORMATOS_WINDOWS: Map<DataFlavor, String> =
                if (!esWindows) {
                    emptyMap()
                } else {
                    runCatching {
                        val mapa = SystemFlavorMap.getDefaultFlavorMap() as SystemFlavorMap
                        listOf(
                            "ExcludeClipboardContentFromMonitorProcessing",
                            "CanIncludeInClipboardHistory",
                            "CanUploadToCloudClipboard"
                        ).associateBy { nativo ->
                            DataFlavor("application/x-boveda-${nativo.lowercase()};class=java.io.InputStream")
                                .also { sabor ->
                                    mapa.addUnencodedNativeForFlavor(sabor, nativo)
                                    mapa.addFlavorForUnencodedNative(nativo, sabor)
                                }
                        }
                    }.getOrDefault(emptyMap())
                }
        }
    }
}
