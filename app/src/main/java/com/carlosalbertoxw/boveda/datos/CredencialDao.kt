package com.carlosalbertoxw.boveda.datos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CredencialDao {

    @Query("SELECT * FROM credenciales ORDER BY titulo COLLATE NOCASE ASC")
    fun todas(): Flow<List<Credencial>>

    @Query("SELECT * FROM credenciales WHERE id = :id")
    fun porId(id: Long): Flow<Credencial?>

    /** Lectura de una sola vez, para el respaldo. */
    @Query("SELECT * FROM credenciales ORDER BY titulo COLLATE NOCASE ASC")
    suspend fun listaCompleta(): List<Credencial>

    @Insert
    suspend fun inserta(credencial: Credencial): Long

    @Update
    suspend fun actualiza(credencial: Credencial)

    @Delete
    suspend fun elimina(credencial: Credencial)

    @Query("DELETE FROM credenciales")
    suspend fun vacia()
}
