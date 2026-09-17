package com.carlosalbertoxw.boveda.datos

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Escritura de archivos que no puede quedar a medias.
 *
 * En Android este trabajo lo hacian `SharedPreferences.commit()` y el diario de
 * SQLite. En escritorio no hay nada de eso gratis, y es justo donde un corte de
 * luz a mitad de escritura dejaria la boveda ilegible para siempre: con la sal
 * nueva y el sobre viejo, o con la mitad del archivo cifrado.
 *
 * El truco es el de siempre: se escribe entero en un temporal junto al destino,
 * se fuerza al disco y solo entonces se renombra encima. El renombrado es
 * atomico en los tres sistemas, asi que en cualquier instante el destino es el
 * archivo viejo completo o el nuevo completo, nunca una mezcla.
 */
object ArchivoSeguro {

    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    fun escribe(destino: Path, bytes: ByteArray, privado: Boolean = true) {
        val temporal = destino.resolveSibling("${destino.fileName}.tmp")
        try {
            FileChannel.open(
                temporal,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ).use { canal ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) canal.write(buffer)
                canal.force(true)
            }
            if (privado) restringe(temporal)
            try {
                Files.move(
                    temporal,
                    destino,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Algunas unidades de red o USB no saben renombrar de forma
                // atomica. Se pierde la garantia, pero no la escritura.
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(temporal) }
            throw e
        }
    }

    /**
     * Solo el dueno puede leer. En Windows no hace falta: la carpeta de datos
     * de la app vive dentro del perfil del usuario, cuyos permisos ya excluyen
     * a los demas usuarios del equipo.
     */
    fun restringe(ruta: Path) {
        if (!posix) return
        val permisos = if (Files.isDirectory(ruta)) "rwx------" else "rw-------"
        runCatching { Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString(permisos)) }
    }

    fun creaDirectorioPrivado(ruta: Path) {
        Files.createDirectories(ruta)
        restringe(ruta)
    }
}
