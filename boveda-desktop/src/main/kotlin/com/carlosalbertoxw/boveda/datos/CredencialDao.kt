package com.carlosalbertoxw.boveda.datos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * El acceso a la boveda. Misma API que el DAO de Room de la version de Android.
 *
 * Varias operaciones tienen que tocar las credenciales y el historial de una
 * sola pieza —mandar a la papelera, mover la marca del historial, borrar de
 * verdad—, y aqui cada una es **una** transaccion de [BaseBoveda]: se calcula
 * el contenido nuevo entero y se escribe de una vez, o no se escribe. Una
 * credencial cuya contrasena no coincide con la fila marcada de su historial es
 * un estado que no debe poder existir ni un instante, ni siquiera si la boveda
 * se cierra a mitad de la operacion.
 *
 * El borrado normal ya no borra: mueve a la papelera. Las unicas rutas que
 * destruyen datos se llaman `borra...DeVerdad`, `vacia...` o `purga`, y todas
 * nacen de un gesto explicito del usuario o del vencimiento del plazo.
 */
class CredencialDao internal constructor(private val base: BaseBoveda) {

    // --- Listados ---------------------------------------------------------

    /** Lo que ve el listado: la papelera no aparece aqui. */
    fun todas(): Flow<List<Credencial>> =
        base.contenido.map { activas(it) }.distinctUntilChanged()

    /** Lo borrado, con lo mas reciente arriba: es el orden en que se busca. */
    fun papelera(): Flow<List<Credencial>> =
        base.contenido.map { enPapelera(it) }.distinctUntilChanged()

    /** Lectura de una sola vez de lo activo. */
    suspend fun listaCompleta(): List<Credencial> = activas(base.lee())

    /**
     * Todo, papelera incluida. Es lo que se exporta y contra lo que se decide
     * si un respaldo trae algo repetido: si la papelera no contara, importar un
     * archivo dejaria una copia activa al lado de la que se acaba de borrar.
     */
    suspend fun listaConPapelera(): List<Credencial> =
        base.lee().credenciales.sortedWith(POR_TITULO)

    fun historialDe(credencialId: Long): Flow<List<HistorialContrasena>> =
        base.contenido
            .map { c -> c.historial.filter { it.credencialId == credencialId }.sortedWith(RECIENTE_PRIMERO) }
            .distinctUntilChanged()

    /** Todo el historial de una vez, para el respaldo. */
    suspend fun historialCompleto(): List<HistorialContrasena> =
        base.lee().historial.sortedWith(RECIENTE_PRIMERO)

    // --- Alta y edicion ---------------------------------------------------

    /**
     * Guarda una credencial nueva y abre su historial con la contrasena que
     * trae, ya marcada como actual. Toda credencial tiene historial desde el
     * primer instante: si el historial empezara solo al primer cambio, la
     * contrasena original —justo la que uno quiere recuperar— seria la unica
     * que nunca estuvo ahi.
     */
    suspend fun inserta(credencial: Credencial): Long = base.transaccion { c ->
        insertaEn(
            c,
            credencial,
            listOf(
                HistorialContrasena(
                    credencialId = 0,
                    contrasena = credencial.contrasena,
                    creadoEn = credencial.actualizadoEn,
                    actual = true
                )
            )
        )
    }

    /**
     * Guarda los cambios y, solo si la contrasena cambio, anota la nueva y le
     * pasa la marca. Corregir el titulo o las notas no ensucia el historial.
     */
    suspend fun actualiza(credencial: Credencial) = base.transaccion { c ->
        val anterior = c.credenciales.firstOrNull { it.id == credencial.id }
            ?: return@transaccion c to Unit
        val credenciales = c.credenciales.map { if (it.id == credencial.id) credencial else it }
        if (anterior.contrasena == credencial.contrasena) {
            return@transaccion c.copy(credenciales = credenciales) to Unit
        }
        val nueva = HistorialContrasena(
            id = c.siguienteIdHistorial,
            credencialId = credencial.id,
            contrasena = credencial.contrasena,
            creadoEn = credencial.actualizadoEn,
            actual = true
        )
        c.copy(
            credenciales = credenciales,
            historial = desmarca(c.historial, credencial.id) + nueva,
            siguienteIdHistorial = c.siguienteIdHistorial + 1
        ) to Unit
    }

    /**
     * Mete una credencial con el historial que ya trae, en vez del que
     * [inserta] abriria por su cuenta. Es el camino de la importacion: el
     * respaldo trae las contrasenas viejas de cada entrada y seria absurdo
     * tirarlas para anotar una sola.
     *
     * El historial entrante se reetiqueta con el id recien asignado y pierde el
     * suyo: los ids del archivo no significan nada en esta base.
     */
    suspend fun insertaConHistorial(
        credencial: Credencial,
        historial: List<HistorialContrasena>
    ): Long {
        if (historial.isEmpty()) return inserta(credencial)
        return base.transaccion { c -> insertaEn(c, credencial, historial) }
    }

    /**
     * Sustituye una entrada que ya existe y su historial entero, de una pieza.
     * Es el camino de la importacion cuando el archivo trae la misma cuenta
     * con datos distintos ([GestorRespaldo.Fusion.combina]): la credencial y la
     * marca de "actual" cambian juntas o no cambian.
     */
    suspend fun reemplazaConHistorial(
        credencial: Credencial,
        historial: List<HistorialContrasena>
    ) = base.transaccion { c ->
        if (c.credenciales.none { it.id == credencial.id }) return@transaccion c to Unit
        val filas = historial.mapIndexed { i, h ->
            h.copy(id = c.siguienteIdHistorial + i, credencialId = credencial.id)
        }
        c.copy(
            credenciales = c.credenciales.map { if (it.id == credencial.id) credencial else it },
            historial = c.historial.filterNot { it.credencialId == credencial.id } + filas,
            siguienteIdHistorial = c.siguienteIdHistorial + filas.size
        ) to Unit
    }

    /**
     * Mueve la marca a una contrasena anterior y la vuelve la de la entrada.
     *
     * No se crea una fila nueva: la marca se desplaza a la que ya estaba. Si se
     * anotara otra vez, ir y volver dos veces llenaria el historial de copias de
     * las mismas dos contrasenas y dejaria de leerse.
     */
    suspend fun usaDelHistorial(entrada: HistorialContrasena, ahora: Long) = base.transaccion { c ->
        val fila = c.historial.firstOrNull { it.id == entrada.id }
        val credencial = c.credenciales.firstOrNull { it.id == entrada.credencialId }
        if (fila == null || credencial == null || fila.credencialId != credencial.id) {
            return@transaccion c to Unit
        }
        c.copy(
            credenciales = c.credenciales.map {
                if (it.id == credencial.id) it.copy(contrasena = fila.contrasena, actualizadoEn = ahora) else it
            },
            historial = c.historial.map {
                when {
                    it.credencialId != credencial.id -> it
                    it.id == fila.id -> it.copy(actual = true)
                    else -> it.copy(actual = false)
                }
            }
        ) to Unit
    }

    // --- Papelera ---------------------------------------------------------

    suspend fun mandaAPapelera(id: Long, cuando: Long) = base.transaccion { c ->
        if (c.credenciales.none { it.id == id }) return@transaccion c to Unit
        c.copy(credenciales = c.credenciales.map { if (it.id == id) it.copy(eliminadoEn = cuando) else it }) to Unit
    }

    suspend fun restaura(id: Long) = base.transaccion { c ->
        if (c.credenciales.none { it.id == id }) return@transaccion c to Unit
        c.copy(credenciales = c.credenciales.map { if (it.id == id) it.copy(eliminadoEn = null) else it }) to Unit
    }

    /** Borrado definitivo de una entrada, con su historial detras. */
    suspend fun borraDeVerdad(id: Long) = base.transaccion { c ->
        if (c.credenciales.none { it.id == id }) return@transaccion c to Unit
        c.copy(
            credenciales = c.credenciales.filterNot { it.id == id },
            historial = c.historial.filterNot { it.credencialId == id }
        ) to Unit
    }

    /** Vacia la papelera entera, sin esperar al plazo. */
    suspend fun vaciaPapelera() = purga(Long.MAX_VALUE)

    /**
     * Borra lo que ya cumplio su plazo. Corre al abrir la boveda, que es el
     * unico momento en que la app puede tocar la base: sin boveda abierta no hay
     * llave, y sin llave no hay nada que purgar.
     */
    suspend fun purga(limite: Long) = base.transaccion { c ->
        val vencidas = c.credenciales
            .filter { (it.eliminadoEn ?: return@filter false) <= limite }
            .mapTo(HashSet()) { it.id }
        if (vencidas.isEmpty()) return@transaccion c to Unit
        c.copy(
            credenciales = c.credenciales.filterNot { it.id in vencidas },
            historial = c.historial.filterNot { it.credencialId in vencidas }
        ) to Unit
    }

    // --- Historial --------------------------------------------------------

    /**
     * Saca una contrasena vieja del historial para siempre. Devuelve cuantas
     * filas cayeron: cero significa que no se borro nada.
     *
     * La marcada como actual no se toca, y la condicion vive aqui y no en la
     * pantalla: es la contrasena que la entrada usa hoy, y borrarla dejaria a
     * la credencial sin la fila que le corresponde.
     */
    suspend fun borraDelHistorial(id: Long): Int = base.transaccion { c ->
        val fila = c.historial.firstOrNull { it.id == id && !it.actual }
            ?: return@transaccion c to 0
        c.copy(historial = c.historial - fila) to 1
    }

    // --- Borrado total ----------------------------------------------------

    suspend fun vacia() = base.transaccion { c ->
        // Los contadores se conservan: un id no se reusa ni tras vaciar.
        c.copy(credenciales = emptyList(), historial = emptyList()) to Unit
    }

    // --- Plomeria ---------------------------------------------------------

    private fun insertaEn(
        c: Contenido,
        credencial: Credencial,
        historial: List<HistorialContrasena>
    ): Pair<Contenido, Long> {
        val id = c.siguienteIdCredencial
        val filas = historial.mapIndexed { i, h ->
            h.copy(id = c.siguienteIdHistorial + i, credencialId = id)
        }
        return c.copy(
            credenciales = c.credenciales + credencial.copy(id = id),
            historial = c.historial + filas,
            siguienteIdCredencial = id + 1,
            siguienteIdHistorial = c.siguienteIdHistorial + filas.size
        ) to id
    }

    private fun desmarca(historial: List<HistorialContrasena>, credencialId: Long) =
        historial.map { if (it.credencialId == credencialId && it.actual) it.copy(actual = false) else it }

    private companion object {
        /** El `COLLATE NOCASE` de las consultas de Room. */
        val POR_TITULO = compareBy<Credencial, String>(String.CASE_INSENSITIVE_ORDER) { it.titulo }
            .thenBy { it.id }

        val RECIENTE_PRIMERO = compareByDescending<HistorialContrasena> { it.creadoEn }
            .thenByDescending { it.id }

        fun activas(c: Contenido) = c.credenciales.filter { it.eliminadoEn == null }.sortedWith(POR_TITULO)

        fun enPapelera(c: Contenido) = c.credenciales.filter { it.eliminadoEn != null }
            .sortedWith(compareByDescending<Credencial> { it.eliminadoEn }.thenByDescending { it.id })
    }
}
