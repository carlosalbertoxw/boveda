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

    /**
     * Que entra, que se actualiza y que se omite al fusionar un respaldo con lo
     * que ya hay.
     *
     * Va aparte y sin tocar la base a proposito: es la unica regla del respaldo
     * con criterio propio —cuando dos entradas son "la misma" y cual de las dos
     * manda— y asi se puede probar sola. Es identica en la app de Android: un
     * mismo archivo tiene que dar el mismo resultado en el telefono y aqui.
     */
    object Fusion {

        /**
         * [actualizadas] llevan el id de la entrada local que reemplazan, con la
         * credencial ya combinada y su historial completo.
         */
        data class Plan(
            val nuevas: List<Respaldo.Entrada>,
            val actualizadas: List<Respaldo.Entrada>,
            val omitidas: Int
        )

        /**
         * Dos entradas son la misma cuenta si coinciden **titulo y usuario**,
         * sin distinguir mayusculas ni espacios de sobra.
         *
         * No basta el titulo solo: nada impide tener dos "Correo", el personal y
         * el del trabajo, y fusionarlos pisaria la contrasena de una cuenta con
         * la de otra. Y no se exige que coincida la contrasena, que era la regla
         * anterior: cambiarla en el telefono e importar en la computadora dejaba
         * dos "Banco" lado a lado sin forma de saber cual valia.
         *
         * Cuando la misma cuenta llega con datos distintos, [combina] decide.
         * Los duplicados dentro del propio archivo se combinan entre si primero
         * y cuentan como omitidos, asi que todo lo entrante queda contado:
         * `entrantes.size == nuevas + actualizadas + omitidas`.
         */
        fun planifica(existentes: List<Respaldo.Entrada>, entrantes: List<Respaldo.Entrada>): Plan {
            var omitidas = 0
            val agrupadas = LinkedHashMap<Pair<String, String>, Respaldo.Entrada>()
            entrantes.forEach { entrada ->
                val clave = clave(entrada.credencial)
                val previa = agrupadas[clave]
                if (previa == null) {
                    agrupadas[clave] = entrada
                } else {
                    agrupadas[clave] = combina(previa, entrada) ?: previa
                    omitidas++
                }
            }

            val porClave = existentes.groupBy { clave(it.credencial) }
            val nuevas = mutableListOf<Respaldo.Entrada>()
            val actualizadas = mutableListOf<Respaldo.Entrada>()
            agrupadas.forEach { (clave, entrante) ->
                val candidatas = porClave[clave]
                if (candidatas.isNullOrEmpty()) {
                    nuevas += entrante
                    return@forEach
                }
                val combinada = combina(eligeLocal(candidatas, entrante), entrante)
                if (combinada == null) omitidas++ else actualizadas += combinada
            }
            return Plan(nuevas, actualizadas, omitidas)
        }

        /**
         * La misma cuenta, con lo que traen las dos versiones.
         *
         *  - Titulo, usuario, contrasena, sitio y notas salen de la version con
         *    `actualizadoEn` mas reciente. Si empatan, se queda la local.
         *  - **Ninguna contrasena se pierde:** la de la version que no gana y
         *    todo el historial del archivo que esta boveda no tenga entran al
         *    historial, con la marca de "actual" en la que gano.
         *  - El id y la papelera son siempre los de la local: lo que se borro
         *    aqui sigue borrado, y lo activo no se manda a la papelera porque el
         *    otro equipo lo borrara.
         *
         * Devuelve null si no hay nada que cambiar: mismo contenido y ninguna
         * contrasena nueva. Las fechas solas no cuentan como cambio, o importar
         * dos veces el mismo archivo reescribiria la boveda entera.
         */
        fun combina(local: Respaldo.Entrada, entrante: Respaldo.Entrada): Respaldo.Entrada? {
            val base = local.credencial
            val otra = entrante.credencial
            val ganaLaOtra = otra.actualizadoEn > base.actualizadoEn
            val ganadora = if (ganaLaOtra) otra else base
            val perdedora = if (ganaLaOtra) base else otra

            val conocidas = local.historial.mapTo(HashSet()) { it.contrasena }
            val historial = local.historial.toMutableList()
            entrante.historial.forEach { anotacion ->
                if (conocidas.add(anotacion.contrasena)) {
                    historial += anotacion.copy(id = 0, credencialId = base.id, actual = false)
                }
            }
            if (conocidas.add(perdedora.contrasena)) {
                historial += HistorialContrasena(
                    credencialId = base.id,
                    contrasena = perdedora.contrasena,
                    creadoEn = perdedora.actualizadoEn,
                    actual = false
                )
            }

            val mismoContenido = base.titulo == ganadora.titulo &&
                base.usuario == ganadora.usuario &&
                base.contrasena == ganadora.contrasena &&
                base.sitio == ganadora.sitio &&
                base.notas == ganadora.notas
            if (mismoContenido && historial.size == local.historial.size) return null

            val credencial = base.copy(
                titulo = ganadora.titulo,
                usuario = ganadora.usuario,
                contrasena = ganadora.contrasena,
                sitio = ganadora.sitio,
                notas = ganadora.notas,
                actualizadoEn = maxOf(base.actualizadoEn, otra.actualizadoEn)
            )
            // Normalizar deja una sola marca, en la contrasena que gano.
            return Respaldo.normaliza(credencial, historial)
        }

        /**
         * Si esta boveda ya tiene la cuenta repetida (de antes de esta regla),
         * se combina con la que mas se le parece y las demas no se tocan.
         */
        private fun eligeLocal(
            candidatas: List<Respaldo.Entrada>,
            entrante: Respaldo.Entrada
        ): Respaldo.Entrada {
            val contrasena = entrante.credencial.contrasena
            return candidatas.firstOrNull { it.credencial.contrasena == contrasena }
                ?: candidatas.firstOrNull { local ->
                    local.historial.any { it.contrasena == contrasena }
                }
                ?: candidatas.maxBy { it.credencial.actualizadoEn }
        }

        private fun clave(credencial: Credencial) =
            normaliza(credencial.titulo) to normaliza(credencial.usuario)

        private val ESPACIOS = Regex("\\s+")

        private fun normaliza(texto: String) = texto.trim().replace(ESPACIOS, " ").lowercase()
    }
}
