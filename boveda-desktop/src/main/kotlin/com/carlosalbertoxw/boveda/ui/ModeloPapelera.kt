package com.carlosalbertoxw.boveda.ui

import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Lo borrado, aparte del modelo principal.
 *
 * Va en su propio modelo por la misma razon que el respaldo: son las tres
 * operaciones que destruyen datos de verdad, y tenerlas en un archivo pequeno
 * —donde se leen las tres seguidas— vale mas que ahorrarse una clase.
 *
 * Vive solo mientras la pantalla de la papelera esta delante.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeloPapelera(aplicacion: AplicacionBoveda) : Modelo(aplicacion.avisos) {

    private val gestor = aplicacion.gestor
    private val avisos = aplicacion.avisos

    val entradas: StateFlow<List<Credencial>> =
        gestor.estado
            .flatMapLatest { estado ->
                if (estado is EstadoBoveda.Abierta) {
                    estado.db.credencialDao().papelera().catch { emit(emptyList()) }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(alcance, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restaura(credencial: Credencial) = lanza {
        val dao = daoAbierto() ?: return@lanza
        dao.restaura(credencial.id)
        avisos.muestra(Textos.avisoRestaurada(credencial.titulo))
    }

    /** Esta si borra. Con su historial detras y sin vuelta atras. */
    fun borraDeVerdad(credencial: Credencial) = lanza {
        val dao = daoAbierto() ?: return@lanza
        dao.borraDeVerdad(credencial.id)
        avisos.muestra(Textos.avisoBorradaParaSiempre(credencial.titulo))
    }

    fun vacia() = lanza {
        val dao = daoAbierto() ?: return@lanza
        val cuantas = entradas.value.size
        dao.vaciaPapelera()
        avisos.muestra(Textos.avisoPapeleraVaciada(cuantas))
    }

    private fun daoAbierto() =
        (gestor.estado.value as? EstadoBoveda.Abierta)?.db?.credencialDao()
}
