package com.carlosalbertoxw.boveda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.Credencial
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda

/**
 * Lo borrado, aparte del modelo principal.
 *
 * Va en su propio modelo por la misma razon que el respaldo: son las tres
 * operaciones que destruyen datos de verdad, y tenerlas en un archivo pequeno
 * —donde se leen las tres seguidas— vale mas que ahorrarse una clase. En
 * [ModeloBoveda] quedarian diluidas entre el candado, la huella y los ajustes.
 *
 * Vive solo mientras la pantalla de la papelera esta delante. Al salir, la
 * lista de lo borrado deja de observarse y no queda ninguna copia colgando.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeloPapelera(app: Application) : AndroidViewModel(app) {

    private val aplicacion = AplicacionBoveda.de(app)
    private val gestor = aplicacion.gestor
    private val avisos = aplicacion.avisos

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    /**
     * Igual que el listado: solo existe mientras la boveda esta abierta, y el
     * catch cubre la carrera entre el cierre y la ultima consulta de Room.
     */
    val entradas: StateFlow<List<Credencial>> =
        gestor.estado
            .flatMapLatest { estado ->
                if (estado is EstadoBoveda.Abierta) {
                    estado.db.credencialDao().papelera().catch { emit(emptyList()) }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restaura(credencial: Credencial) = lanza {
        val dao = daoAbierto() ?: return@lanza
        dao.restaura(credencial.id)
        avisos.muestra(texto(R.string.aviso_restaurada, credencial.titulo))
    }

    /** Esta si borra. Con su historial detras y sin vuelta atras. */
    fun borraDeVerdad(credencial: Credencial) = lanza {
        val dao = daoAbierto() ?: return@lanza
        dao.borraDeVerdad(credencial.id)
        avisos.muestra(texto(R.string.aviso_borrada_para_siempre, credencial.titulo))
    }

    fun vacia() = lanza {
        val dao = daoAbierto() ?: return@lanza
        val cuantas = entradas.value.size
        dao.vaciaPapelera()
        avisos.muestra(texto(R.string.aviso_papelera_vaciada, cuantas))
    }

    private fun daoAbierto() =
        (gestor.estado.value as? EstadoBoveda.Abierta)?.db?.credencialDao()

    private fun texto(id: Int, vararg formato: Any): String =
        getApplication<Application>().getString(id, *formato)

    private fun lanza(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _ocupado.value = true
            try {
                bloque()
            } catch (e: Exception) {
                avisos.muestra(e.message ?: texto(R.string.aviso_generico))
            } finally {
                _ocupado.value = false
            }
        }
    }
}
