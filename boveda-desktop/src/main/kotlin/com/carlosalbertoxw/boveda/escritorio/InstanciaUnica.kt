package com.carlosalbertoxw.boveda.escritorio

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Una sola copia de la app por boveda.
 *
 * En Android el sistema garantiza un solo proceso por app. En escritorio nada
 * impide abrir la app dos veces, y dos procesos con la misma boveda abierta se
 * pisarian: cada uno escribe el archivo entero con lo que tiene en memoria, y
 * el ultimo en guardar borraria en silencio lo que guardo el otro.
 *
 * El cerrojo lo suelta el sistema operativo cuando el proceso termina, incluso
 * si termina a la fuerza, asi que no puede quedarse pegado.
 */
class InstanciaUnica private constructor(
    private val canal: FileChannel,
    private val cerrojo: FileLock
) {

    fun suelta() {
        runCatching { cerrojo.release() }
        runCatching { canal.close() }
    }

    companion object {
        private const val ARCHIVO = "boveda.lock"

        /** Devuelve null si otra copia ya tiene la boveda. */
        fun adquiere(directorio: Path): InstanciaUnica? {
            val canal = FileChannel.open(
                directorio.resolve(ARCHIVO),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )
            val cerrojo = try {
                canal.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (cerrojo == null) {
                canal.close()
                return null
            }
            return InstanciaUnica(canal, cerrojo)
        }
    }
}
