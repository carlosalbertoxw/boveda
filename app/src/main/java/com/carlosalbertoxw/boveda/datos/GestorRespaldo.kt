package com.carlosalbertoxw.boveda.datos

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.GestorBoveda
import java.io.IOException
import com.carlosalbertoxw.boveda.R

/**
 * Exportar e importar el respaldo.
 *
 * Esta es la unica parte de la app que toca el mundo exterior, y lo hace por el
 * selector de archivos del sistema: la app no pide permisos de almacenamiento,
 * no ve el disco y no elige donde escribir. Recibe un Uri concreto que el
 * usuario acaba de senalar con el dedo, escribe ahi y se acaba el acceso.
 *
 * Aun asi, en cuanto el archivo existe deja de estar bajo el control de la app.
 * Por eso sale cifrado con su propia contrasena y no en claro: si acaba en la
 * carpeta de descargas, en una copia automatica a la nube o en un chat, sigue
 * siendo un bloque ilegible.
 */
class GestorRespaldo(private val app: Context, private val gestor: GestorBoveda) {

    /** Tope defensivo: un respaldo real pesa kilobytes, no megabytes. */
    private val limiteBytes = 16 * 1024 * 1024

    sealed interface Exportacion {
        data class Ok(val entradas: Int) : Exportacion
        data object MaestraIncorrecta : Exportacion
        data object BovedaCerrada : Exportacion
        data class Falla(val mensaje: String) : Exportacion
    }

    sealed interface Importacion {
        data class Ok(val agregadas: Int, val omitidas: Int) : Importacion
        data object ContrasenaIncorrecta : Importacion
        data object NoEsUnRespaldo : Importacion
        data object BovedaCerrada : Importacion
        data class Falla(val mensaje: String) : Importacion
    }

    suspend fun exporta(
        destino: Uri,
        maestra: CharArray,
        contrasenaArchivo: CharArray
    ): Exportacion = withContext(Dispatchers.IO) {
        val db = (gestor.estado.value as? EstadoBoveda.Abierta)?.db
            ?: return@withContext Exportacion.BovedaCerrada

        // Sacar todas las contrasenas de golpe es la operacion mas peligrosa de
        // la app. Se vuelve a pedir la maestra aunque la boveda ya este abierta.
        if (!gestor.verificaMaestra(maestra)) return@withContext Exportacion.MaestraIncorrecta

        val credenciales = db.credencialDao().listaCompleta()
        val archivo = Respaldo.escribe(credenciales, contrasenaArchivo)

        try {
            app.contentResolver.openOutputStream(destino, "wt")?.use { it.write(archivo) }
                ?: return@withContext Exportacion.Falla(texto(R.string.error_destino_no_escribible))
        } catch (e: IOException) {
            return@withContext Exportacion.Falla(e.message ?: texto(R.string.error_escribir_archivo))
        } catch (e: SecurityException) {
            return@withContext Exportacion.Falla(texto(R.string.error_sin_permiso_escritura))
        }

        Exportacion.Ok(credenciales.size)
    }

    suspend fun importa(
        origen: Uri,
        contrasenaArchivo: CharArray,
        reemplazar: Boolean
    ): Importacion = withContext(Dispatchers.IO) {
        val db = (gestor.estado.value as? EstadoBoveda.Abierta)?.db
            ?: return@withContext Importacion.BovedaCerrada

        val bytes = try {
            app.contentResolver.openInputStream(origen)?.use { flujo ->
                // Se lee un byte de mas que el limite y se corta ahi. Antes se
                // cargaba el archivo entero y solo despues se comparaba con el
                // limite, asi que elegir por error un video en el selector
                // tumbaba el proceso por falta de memoria justo antes de poder
                // mostrar el mensaje que explica el problema.
                val leido = flujo.readNBytes(limiteBytes + 1)
                if (leido.size > limiteBytes) {
                    return@withContext Importacion.Falla(texto(R.string.error_archivo_grande))
                }
                leido
            } ?: return@withContext Importacion.Falla(texto(R.string.error_leer_archivo))
        } catch (e: IOException) {
            return@withContext Importacion.Falla(e.message ?: texto(R.string.error_leer_archivo))
        } catch (e: SecurityException) {
            return@withContext Importacion.Falla(texto(R.string.error_sin_permiso_lectura))
        }

        when (val lectura = Respaldo.lee(bytes, contrasenaArchivo)) {
            is Respaldo.Lectura.ContrasenaIncorrecta -> Importacion.ContrasenaIncorrecta
            is Respaldo.Lectura.NoEsUnRespaldo -> Importacion.NoEsUnRespaldo
            is Respaldo.Lectura.VersionNoSoportada -> Importacion.Falla(
                app.getString(
                    R.string.error_version_no_soportada,
                    lectura.version,
                    Respaldo.VERSION
                )
            )
            is Respaldo.Lectura.Corrupto ->
                Importacion.Falla(app.getString(R.string.error_respaldo_danado, lectura.detalle))
            is Respaldo.Lectura.Ok -> vuelca(db, lectura.credenciales, reemplazar)
        }
    }

    private fun texto(id: Int): String = app.getString(id)

    private suspend fun vuelca(
        db: BovedaDatabase,
        entrantes: List<Credencial>,
        reemplazar: Boolean
    ): Importacion {
        val dao = db.credencialDao()

        if (reemplazar) {
            dao.vacia()
            entrantes.forEach { dao.inserta(it) }
            return Importacion.Ok(entrantes.size, 0)
        }

        // Al fusionar se omite lo que ya esta identico. Sin esto, importar dos
        // veces el mismo archivo duplicaria la boveda entera, que es justo lo
        // que uno hace cuando no esta seguro de si el respaldo entro.
        val existentes = dao.listaCompleta()
            .map { Triple(it.titulo, it.usuario, it.contrasena) }
            .toHashSet()

        var agregadas = 0
        var omitidas = 0
        entrantes.forEach { credencial ->
            val huella = Triple(credencial.titulo, credencial.usuario, credencial.contrasena)
            if (huella in existentes) {
                omitidas++
            } else {
                dao.inserta(credencial)
                existentes.add(huella)
                agregadas++
            }
        }
        return Importacion.Ok(agregadas, omitidas)
    }
}
