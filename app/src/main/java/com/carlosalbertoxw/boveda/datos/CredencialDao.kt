package com.carlosalbertoxw.boveda.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * El acceso a la boveda.
 *
 * Es una clase abstracta y no una interfaz porque varias operaciones tienen que
 * tocar dos tablas de una sola pieza —mandar a la papelera, mover la marca del
 * historial, borrar de verdad— y `@Transaction` con cuerpo se apoya en eso.
 * Que la atomicidad viva aqui y no en el modelo de vista es deliberado: una
 * credencial cuya contrasena no coincide con la fila marcada de su historial es
 * un estado que no debe poder existir ni un instante, ni siquiera si la boveda
 * se cierra a mitad de la operacion.
 *
 * El borrado normal ya no borra: mueve a la papelera. Las unicas rutas que
 * destruyen datos se llaman `borra...DeVerdad`, `vacia...` o `purga`, y todas
 * nacen de un gesto explicito del usuario o del vencimiento del plazo.
 */
@Dao
abstract class CredencialDao {

    // --- Listados ---------------------------------------------------------

    /** Lo que ve el listado: la papelera no aparece aqui. */
    @Query(
        "SELECT * FROM credenciales WHERE eliminadoEn IS NULL " +
            "ORDER BY titulo COLLATE NOCASE ASC"
    )
    abstract fun todas(): Flow<List<Credencial>>

    /** Lo borrado, con lo mas reciente arriba: es el orden en que se busca. */
    @Query(
        "SELECT * FROM credenciales WHERE eliminadoEn IS NOT NULL " +
            "ORDER BY eliminadoEn DESC"
    )
    abstract fun papelera(): Flow<List<Credencial>>

    /** Lectura de una sola vez de lo activo. */
    @Query(
        "SELECT * FROM credenciales WHERE eliminadoEn IS NULL " +
            "ORDER BY titulo COLLATE NOCASE ASC"
    )
    abstract suspend fun listaCompleta(): List<Credencial>

    /**
     * Todo, papelera incluida. Es lo que se exporta y contra lo que se decide
     * si un respaldo trae algo repetido: si la papelera no contara, importar un
     * archivo dejaria una copia activa al lado de la que se acaba de borrar.
     */
    @Query("SELECT * FROM credenciales ORDER BY titulo COLLATE NOCASE ASC")
    abstract suspend fun listaConPapelera(): List<Credencial>

    @Query(
        "SELECT * FROM historial_contrasenas WHERE credencialId = :credencialId " +
            "ORDER BY creadoEn DESC, id DESC"
    )
    abstract fun historialDe(credencialId: Long): Flow<List<HistorialContrasena>>

    /** Todo el historial de una vez, para el respaldo. */
    @Query("SELECT * FROM historial_contrasenas ORDER BY creadoEn DESC, id DESC")
    abstract suspend fun historialCompleto(): List<HistorialContrasena>

    // --- Alta y edicion ---------------------------------------------------

    /**
     * Guarda una credencial nueva y abre su historial con la contrasena que
     * trae, ya marcada como actual. Toda credencial tiene historial desde el
     * primer instante: si el historial empezara solo al primer cambio, la
     * contrasena original —justo la que uno quiere recuperar— seria la unica
     * que nunca estuvo ahi.
     */
    @Transaction
    open suspend fun inserta(credencial: Credencial): Long {
        val id = insertaFila(credencial)
        insertaEnHistorial(
            HistorialContrasena(
                credencialId = id,
                contrasena = credencial.contrasena,
                creadoEn = credencial.actualizadoEn,
                actual = true
            )
        )
        return id
    }

    /**
     * Guarda los cambios y, solo si la contrasena cambio, anota la nueva y le
     * pasa la marca. Corregir el titulo o las notas no ensucia el historial.
     */
    @Transaction
    open suspend fun actualiza(credencial: Credencial) {
        val anterior = filaPorId(credencial.id)
        actualizaFila(credencial)
        if (anterior != null && anterior.contrasena == credencial.contrasena) return
        desmarcaHistorial(credencial.id)
        insertaEnHistorial(
            HistorialContrasena(
                credencialId = credencial.id,
                contrasena = credencial.contrasena,
                creadoEn = credencial.actualizadoEn,
                actual = true
            )
        )
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
    @Transaction
    open suspend fun insertaConHistorial(
        credencial: Credencial,
        historial: List<HistorialContrasena>
    ): Long {
        if (historial.isEmpty()) return inserta(credencial)
        val id = insertaFila(credencial)
        insertaEnHistorial(historial.map { it.copy(id = 0, credencialId = id) })
        return id
    }

    /**
     * Mueve la marca a una contrasena anterior y la vuelve la de la entrada.
     *
     * No se crea una fila nueva: la marca se desplaza a la que ya estaba. Si se
     * anotara otra vez, ir y volver dos veces llenaria el historial de copias de
     * las mismas dos contrasenas y dejaria de leerse.
     */
    @Transaction
    open suspend fun usaDelHistorial(entrada: HistorialContrasena, ahora: Long) {
        val credencial = filaPorId(entrada.credencialId) ?: return
        actualizaFila(credencial.copy(contrasena = entrada.contrasena, actualizadoEn = ahora))
        desmarcaHistorial(entrada.credencialId)
        marcaComoActual(entrada.id)
    }

    // --- Papelera ---------------------------------------------------------

    @Query("UPDATE credenciales SET eliminadoEn = :cuando WHERE id = :id")
    abstract suspend fun mandaAPapelera(id: Long, cuando: Long)

    @Query("UPDATE credenciales SET eliminadoEn = NULL WHERE id = :id")
    abstract suspend fun restaura(id: Long)

    /**
     * Borrado definitivo de una entrada, con su historial detras. El historial
     * se borra a mano y no solo por la clave foranea: la cascada depende de que
     * el pragma de claves foraneas este activo, y dejar contrasenas huerfanas en
     * la base es justo lo que este boton promete que no pasa.
     */
    @Transaction
    open suspend fun borraDeVerdad(id: Long) {
        borraHistorialDe(id)
        borraFila(id)
    }

    /** Vacia la papelera entera, sin esperar al plazo. */
    @Transaction
    open suspend fun vaciaPapelera() {
        borraHistorialVencido(Long.MAX_VALUE)
        borraCredencialesVencidas(Long.MAX_VALUE)
    }

    /**
     * Borra lo que ya cumplio su plazo. Corre al abrir la boveda, que es el
     * unico momento en que la app puede tocar la base: sin boveda abierta no hay
     * llave, y sin llave no hay nada que purgar.
     */
    @Transaction
    open suspend fun purga(limite: Long) {
        borraHistorialVencido(limite)
        borraCredencialesVencidas(limite)
    }

    // --- Historial --------------------------------------------------------

    /**
     * Saca una contrasena vieja del historial para siempre. Devuelve cuantas
     * filas cayeron: cero significa que no se borro nada.
     *
     * La marcada como actual no se toca, y por eso la condicion vive en el SQL y
     * no en la pantalla: es la contrasena que la entrada usa hoy, y borrarla
     * dejaria a la credencial sin la fila que le corresponde. Quien quiera
     * deshacerse de ella tiene que cambiar antes la contrasena.
     */
    @Query("DELETE FROM historial_contrasenas WHERE id = :id AND actual = 0")
    abstract suspend fun borraDelHistorial(id: Long): Int

    @Insert
    abstract suspend fun insertaEnHistorial(entrada: HistorialContrasena)

    @Insert
    abstract suspend fun insertaEnHistorial(entradas: List<HistorialContrasena>)

    // --- Borrado total ----------------------------------------------------

    @Transaction
    open suspend fun vacia() {
        borraTodoElHistorial()
        borraTodasLasFilas()
    }

    // --- Plomeria ---------------------------------------------------------
    //
    // Lo que sigue son los ladrillos de las operaciones de arriba. No estan
    // pensados para llamarse sueltos: casi todos dejan las dos tablas
    // descuadradas si se usan por su cuenta.

    @Insert
    protected abstract suspend fun insertaFila(credencial: Credencial): Long

    @Update
    protected abstract suspend fun actualizaFila(credencial: Credencial)

    @Query("SELECT * FROM credenciales WHERE id = :id")
    protected abstract suspend fun filaPorId(id: Long): Credencial?

    @Query("DELETE FROM credenciales WHERE id = :id")
    protected abstract suspend fun borraFila(id: Long)

    @Query("UPDATE historial_contrasenas SET actual = 0 WHERE credencialId = :credencialId")
    protected abstract suspend fun desmarcaHistorial(credencialId: Long)

    @Query("UPDATE historial_contrasenas SET actual = 1 WHERE id = :id")
    protected abstract suspend fun marcaComoActual(id: Long)

    @Query("DELETE FROM historial_contrasenas WHERE credencialId = :credencialId")
    protected abstract suspend fun borraHistorialDe(credencialId: Long)

    @Query(
        "DELETE FROM historial_contrasenas WHERE credencialId IN " +
            "(SELECT id FROM credenciales WHERE eliminadoEn IS NOT NULL AND eliminadoEn <= :limite)"
    )
    protected abstract suspend fun borraHistorialVencido(limite: Long)

    @Query("DELETE FROM credenciales WHERE eliminadoEn IS NOT NULL AND eliminadoEn <= :limite")
    protected abstract suspend fun borraCredencialesVencidas(limite: Long)

    @Query("DELETE FROM historial_contrasenas")
    protected abstract suspend fun borraTodoElHistorial()

    @Query("DELETE FROM credenciales")
    protected abstract suspend fun borraTodasLasFilas()
}
