package com.carlosalbertoxw.boveda.datos

import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exportar e importar el respaldo.
 *
 * Esta es la unica parte de la app que toca archivos fuera de su carpeta, y lo
 * hace por el selector de archivos del sistema: la app no recorre el disco ni
 * elige donde escribir. Recibe una ruta concreta que el usuario acaba de
 * senalar, escribe ahi y se acaba.
 *
 * Aun asi, en cuanto el archivo existe deja de estar bajo el control de la app.
 * Por eso sale cifrado con su propia contrasena y no en claro: si acaba en la
 * carpeta de descargas, en una carpeta sincronizada con la nube o en un chat,
 * sigue siendo un bloque ilegible.
 *
 * El formato es exactamente el de la app de Android ([Respaldo]): un archivo
 * exportado en el telefono se importa aqui, y al reves.
 */
class GestorRespaldo(private val gestor: GestorBoveda) {

    /** Tope defensivo: un respaldo real pesa kilobytes, no megabytes. */
    private val limiteBytes = 16 * 1024 * 1024

    sealed interface Exportacion {
        data class Ok(val entradas: Int) : Exportacion
        data object MaestraIncorrecta : Exportacion
        data object BovedaCerrada : Exportacion
        data class Falla(val mensaje: String) : Exportacion
    }

    sealed interface Importacion {
        data class Ok(
            val agregadas: Int,
            val omitidas: Int,
            val actualizadas: Int = 0
        ) : Importacion
        data object ContrasenaIncorrecta : Importacion
        data object NoEsUnRespaldo : Importacion
        data object BovedaCerrada : Importacion
        data class Falla(val mensaje: String) : Importacion
    }

    suspend fun exporta(
        destino: Path,
        maestra: CharArray,
        contrasenaArchivo: CharArray
    ): Exportacion = withContext(Dispatchers.IO) {
        val db = (gestor.estado.value as? EstadoBoveda.Abierta)?.db
            ?: return@withContext Exportacion.BovedaCerrada

        // Sacar todas las contrasenas de golpe es la operacion mas peligrosa de
        // la app. Se vuelve a pedir la maestra aunque la boveda ya este abierta.
        if (!gestor.verificaMaestra(maestra)) return@withContext Exportacion.MaestraIncorrecta

        // Se exporta la boveda entera, papelera incluida, con el historial de
        // cada entrada. Un respaldo que solo llevara lo activo convertiria
        // "restaurar" en un segundo borrado: lo que estaba en la papelera
        // esperando su plazo desapareceria sin que nadie lo decidiera.
        val dao = db.credencialDao()
        val credenciales = try {
            dao.listaConPapelera()
        } catch (_: IllegalStateException) {
            return@withContext Exportacion.BovedaCerrada
        }
        val historial = dao.historialCompleto().groupBy { it.credencialId }
        val entradas = credenciales.map {
            Respaldo.Entrada(it, historial[it.id].orEmpty())
        }
        val archivo = Respaldo.escribe(entradas, contrasenaArchivo)

        try {
            // Temporal y renombrado: si el disco se llena a mitad, el respaldo
            // anterior con ese nombre no queda sustituido por uno truncado.
            ArchivoSeguro.escribe(destino, archivo, privado = false)
        } catch (_: AccessDeniedException) {
            return@withContext Exportacion.Falla(Textos.ERROR_SIN_PERMISO_ESCRITURA)
        } catch (e: IOException) {
            return@withContext Exportacion.Falla(e.message ?: Textos.ERROR_ESCRIBIR_ARCHIVO)
        } catch (_: SecurityException) {
            return@withContext Exportacion.Falla(Textos.ERROR_SIN_PERMISO_ESCRITURA)
        }

        Exportacion.Ok(credenciales.size)
    }

    suspend fun importa(
        origen: Path,
        contrasenaArchivo: CharArray,
        reemplazar: Boolean
    ): Importacion = withContext(Dispatchers.IO) {
        val db = (gestor.estado.value as? EstadoBoveda.Abierta)?.db
            ?: return@withContext Importacion.BovedaCerrada

        val bytes = try {
            Files.newInputStream(origen).use { flujo ->
                // Se lee un byte de mas que el limite y se corta ahi: elegir por
                // error un video no debe tumbar la app por falta de memoria
                // justo antes de poder explicar el problema.
                val leido = flujo.readNBytes(limiteBytes + 1)
                if (leido.size > limiteBytes) {
                    return@withContext Importacion.Falla(Textos.ERROR_ARCHIVO_GRANDE)
                }
                leido
            }
        } catch (_: AccessDeniedException) {
            return@withContext Importacion.Falla(Textos.ERROR_SIN_PERMISO_LECTURA)
        } catch (_: IOException) {
            return@withContext Importacion.Falla(Textos.ERROR_LEER_ARCHIVO)
        } catch (_: SecurityException) {
            return@withContext Importacion.Falla(Textos.ERROR_SIN_PERMISO_LECTURA)
        }

        when (val lectura = Respaldo.lee(bytes, contrasenaArchivo)) {
            is Respaldo.Lectura.ContrasenaIncorrecta -> Importacion.ContrasenaIncorrecta
            is Respaldo.Lectura.NoEsUnRespaldo -> Importacion.NoEsUnRespaldo
            is Respaldo.Lectura.VersionNoSoportada -> Importacion.Falla(
                Textos.errorVersionNoSoportada(lectura.version, Respaldo.VERSION)
            )
            is Respaldo.Lectura.Corrupto ->
                Importacion.Falla(Textos.errorRespaldoDanado(lectura.detalle))
            is Respaldo.Lectura.Ok -> try {
                vuelca(db, lectura.entradas, reemplazar)
            } catch (_: IllegalStateException) {
                Importacion.BovedaCerrada
            }
        }
    }

    private suspend fun vuelca(
        db: BaseBoveda,
        entrantes: List<Respaldo.Entrada>,
        reemplazar: Boolean
    ): Importacion {
        val dao = db.credencialDao()

        if (reemplazar) {
            dao.vacia()
            entrantes.forEach { dao.insertaConHistorial(it.credencial, it.historial) }
            return Importacion.Ok(entrantes.size, 0)
        }

        // Se compara contra la boveda entera, papelera incluida: si solo se
        // mirara lo activo, importar dejaria una copia nueva al lado de la que
        // el usuario acaba de mandar a la papelera, y el borrado se desharia
        // solo sin decir nada. Lo que sigue en la papelera se recupera desde
        // ahi, que es donde el usuario lo dejo.
        val historial = dao.historialCompleto().groupBy { it.credencialId }
        val existentes = dao.listaConPapelera().map {
            Respaldo.Entrada(it, historial[it.id].orEmpty())
        }
        val plan = Fusion.planifica(existentes, entrantes)
        plan.nuevas.forEach { dao.insertaConHistorial(it.credencial, it.historial) }
        plan.actualizadas.forEach { dao.reemplazaConHistorial(it.credencial, it.historial) }
        return Importacion.Ok(plan.nuevas.size, plan.omitidas, plan.actualizadas.size)
    }
}
