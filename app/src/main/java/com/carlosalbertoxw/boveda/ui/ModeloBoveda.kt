package com.carlosalbertoxw.boveda.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import com.carlosalbertoxw.boveda.seguridad.Apertura
import com.carlosalbertoxw.boveda.seguridad.Biometria
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.Portapapeles
import java.text.Normalizer
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
class ModeloBoveda(app: Application) : AndroidViewModel(app) {

    private val aplicacion = AplicacionBoveda.de(app)
    val gestor = aplicacion.gestor

    val estado: StateFlow<EstadoBoveda> = gestor.estado

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    /**
     * La lista solo existe mientras la boveda esta abierta. Al bloquear, el
     * flujo cambia a lista vacia y Compose descarta de inmediato lo que tenia
     * pintado, sin que quede una copia colgando en el arbol.
     *
     * El catch cubre la carrera del cierre: entre que se anuncia el bloqueo y
     * que Room deja de emitir puede colarse una consulta contra una base ya
     * cerrada, y eso no debe tumbar la app.
     */
    val todas: StateFlow<List<Credencial>> =
        gestor.estado
            .flatMapLatest { estado ->
                if (estado is EstadoBoveda.Abierta) {
                    estado.db.credencialDao().todas().catch { emit(emptyList()) }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** La lista que ve el listado: la anterior, ya pasada por el buscador. */
    val credenciales: StateFlow<List<Credencial>> =
        combine(todas, _busqueda) { lista, texto -> filtra(lista, texto) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun busca(texto: String) { _busqueda.value = texto }

    fun limpiaAviso() { _aviso.value = null }

    fun avisa(texto: String) { _aviso.value = texto }

    // --- Apertura y cierre ----------------------------------------------

    fun crea(maestra: String) = lanza {
        val chars = maestra.toCharArray()
        try {
            reporta(gestor.crea(chars))
        } finally {
            chars.fill(' ')
        }
    }

    fun abre(maestra: String) = lanza {
        val chars = maestra.toCharArray()
        try {
            reporta(gestor.abreConMaestra(chars))
        } finally {
            chars.fill(' ')
        }
    }

    fun abreConBiometrico(cifrador: Cipher) = lanza {
        reporta(gestor.abreConBiometrico(cifrador))
    }

    fun bloquea() {
        Portapapeles.limpiaTodo(getApplication())
        gestor.bloquea()
    }

    private fun reporta(resultado: Apertura) {
        when (resultado) {
            is Apertura.Ok -> _aviso.value = null
            is Apertura.Incorrecta -> _aviso.value = when {
                resultado.esperaSegundos > 0 ->
                    "Contrasena incorrecta. Espera ${resultado.esperaSegundos} s antes de reintentar."
                else ->
                    "Contrasena incorrecta. Intento ${resultado.fallos}."
            }
            is Apertura.EnEspera ->
                _aviso.value = "Demasiados intentos. Espera ${resultado.segundos} s."
            is Apertura.Borrada ->
                _aviso.value = "Se alcanzo el limite de intentos: la boveda se borro."
            is Apertura.Falla -> _aviso.value = resultado.mensaje
        }
    }

    // --- Credenciales -----------------------------------------------------

    fun guarda(credencial: Credencial) = lanza {
        val dao = (gestor.estado.value as? EstadoBoveda.Abierta)?.db?.credencialDao() ?: return@lanza
        if (credencial.id == 0L) {
            dao.inserta(credencial)
        } else {
            dao.actualiza(credencial.copy(actualizadoEn = System.currentTimeMillis()))
        }
    }

    fun elimina(credencial: Credencial) = lanza {
        val dao = (gestor.estado.value as? EstadoBoveda.Abierta)?.db?.credencialDao() ?: return@lanza
        dao.elimina(credencial)
    }

    fun copia(etiqueta: String, texto: String) {
        val segundos = gestor.prefs.segundosPortapapeles
        Portapapeles.copia(getApplication(), etiqueta, texto, aplicacion.alcance, segundos)
        _aviso.value = if (segundos > 0) {
            "Copiado. Se borra del portapapeles en $segundos s."
        } else {
            "Copiado."
        }
    }

    // --- Ajustes ----------------------------------------------------------

    fun biometricoDisponible(): Boolean = Biometria.disponible(getApplication())

    fun activaBiometrico(cifrador: Cipher) {
        _aviso.value = if (gestor.activaBiometrico(cifrador)) {
            "Desbloqueo con huella activado."
        } else {
            "No se pudo activar el desbloqueo con huella."
        }
    }

    fun apagaBiometrico() {
        gestor.prefs.apagaBiometrico()
        _aviso.value = "Desbloqueo con huella desactivado."
    }

    fun cambiaMaestra(actual: String, nueva: String) = lanza {
        val a = actual.toCharArray()
        val n = nueva.toCharArray()
        try {
            _aviso.value = if (gestor.cambiaMaestra(a, n)) {
                "Contrasena maestra actualizada."
            } else {
                "La contrasena actual no es correcta."
            }
        } finally {
            a.fill(' ')
            n.fill(' ')
        }
    }

    fun restablece() {
        Portapapeles.limpiaTodo(getApplication())
        gestor.restablece()
    }

    // --- Respaldo ---------------------------------------------------------

    private val respaldo = GestorRespaldo(app, gestor)

    /** Avisa al candado de que la salida al selector de archivos es esperada. */
    fun permiteSalidaBreve() = aplicacion.permiteSalidaBreve()

    fun exporta(destino: Uri, maestra: String, contrasenaArchivo: String) = lanza {
        val m = maestra.toCharArray()
        val c = contrasenaArchivo.toCharArray()
        try {
            _aviso.value = when (val r = respaldo.exporta(destino, m, c)) {
                is GestorRespaldo.Exportacion.Ok ->
                    "Respaldo creado con ${r.entradas} entradas."
                is GestorRespaldo.Exportacion.MaestraIncorrecta ->
                    "La contrasena maestra no es correcta."
                is GestorRespaldo.Exportacion.BovedaCerrada ->
                    "La boveda se cerro antes de terminar."
                is GestorRespaldo.Exportacion.Falla -> r.mensaje
            }
        } finally {
            m.fill(' ')
            c.fill(' ')
        }
    }

    fun importa(origen: Uri, contrasenaArchivo: String, reemplazar: Boolean) = lanza {
        val c = contrasenaArchivo.toCharArray()
        try {
            _aviso.value = when (val r = respaldo.importa(origen, c, reemplazar)) {
                is GestorRespaldo.Importacion.Ok -> when {
                    r.omitidas > 0 ->
                        "Importadas ${r.agregadas}. Se omitieron ${r.omitidas} que ya estaban."
                    else -> "Importadas ${r.agregadas} entradas."
                }
                is GestorRespaldo.Importacion.ContrasenaIncorrecta ->
                    "La contrasena del archivo no es correcta, o el archivo fue alterado."
                is GestorRespaldo.Importacion.NoEsUnRespaldo ->
                    "Ese archivo no es un respaldo de Boveda."
                is GestorRespaldo.Importacion.BovedaCerrada ->
                    "La boveda se cerro antes de terminar."
                is GestorRespaldo.Importacion.Falla -> r.mensaje
            }
        } finally {
            c.fill(' ')
        }
    }

    // --- Plomeria ---------------------------------------------------------

    private fun lanza(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _ocupado.value = true
            try {
                bloque()
            } catch (e: Exception) {
                _aviso.value = e.message ?: "Algo salio mal."
            } finally {
                _ocupado.value = false
            }
        }
    }

    /**
     * Busca ignorando acentos y mayusculas: escribir "correo" debe encontrar
     * "Correo electronico" y tambien "Corréo".
     */
    private fun filtra(lista: List<Credencial>, texto: String): List<Credencial> {
        if (texto.isBlank()) return lista
        val aguja = sinAcentos(texto)
        return lista.filter {
            sinAcentos(it.titulo).contains(aguja) ||
                sinAcentos(it.usuario).contains(aguja) ||
                sinAcentos(it.sitio).contains(aguja)
        }
    }

    private fun sinAcentos(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
