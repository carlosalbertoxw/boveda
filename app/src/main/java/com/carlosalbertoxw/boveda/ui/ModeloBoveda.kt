package com.carlosalbertoxw.boveda.ui

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.seguridad.AlmacenBiometrico
import com.carlosalbertoxw.boveda.seguridad.Apertura
import com.carlosalbertoxw.boveda.seguridad.Biometria
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.Portapapeles
import java.text.Normalizer

/**
 * El unico camino de la interfaz hacia la boveda.
 *
 * `gestor` es privado a proposito: antes era publico y las pantallas escribian
 * directamente en preferencias y pedian cifradores al Keystore desde un
 * `onClick`. Con eso, la capa que guarda los secretos no tenia frontera y
 * cualquier pantalla nueva podia tocarla sin que se notara en la revision.
 * Ahora todo lo que cruza esa linea pasa por un metodo con nombre de este
 * archivo, y los ajustes salen como estado observable en vez de como lecturas
 * de disco a mitad de una recomposicion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeloBoveda(app: Application) : AndroidViewModel(app) {

    private val aplicacion = AplicacionBoveda.de(app)
    private val gestor = aplicacion.gestor
    private val avisos = aplicacion.avisos

    val estado: StateFlow<EstadoBoveda> = gestor.estado

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

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

    private class Indexada(val credencial: Credencial, val buscable: String)

    /**
     * El texto por el que se busca, normalizado una sola vez por credencial y
     * no una vez por tecla. Antes cada pulsacion normalizaba tres campos de
     * cada entrada y compilaba una expresion regular nueva para cada uno, todo
     * en el hilo principal: con una boveda grande, escribir en el buscador se
     * arrastraba.
     */
    private val indexadas: StateFlow<List<Indexada>> =
        todas
            .map { lista ->
                lista.map { Indexada(it, sinAcentos("${it.titulo}\n${it.usuario}\n${it.sitio}")) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** La lista que ve el listado: la anterior, ya pasada por el buscador. */
    val credenciales: StateFlow<List<Credencial>> =
        combine(indexadas, _busqueda) { lista, texto ->
            if (texto.isBlank()) {
                lista.map { it.credencial }
            } else {
                val aguja = sinAcentos(texto)
                lista.filter { it.buscable.contains(aguja) }.map { it.credencial }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun busca(texto: String) { _busqueda.value = texto }

    fun avisa(texto: String) = avisos.muestra(texto)

    // --- Apertura y cierre ----------------------------------------------

    /**
     * Cuanto falta para volver a aceptar intentos. Se vuelve a preguntar en
     * bucle en vez de descontar en memoria porque la espera vive en
     * preferencias y debe sobrevivir a que el usuario cierre y reabra la app
     * para reintentar. El bucle solo corre mientras alguien mira la pantalla
     * de desbloqueo.
     */
    val esperaSegundos: StateFlow<Int> =
        flow {
            while (true) {
                emit(gestor.segundosDeEspera())
                delay(500)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), 0)

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

    fun bloquea() {
        Portapapeles.limpiaTodo(getApplication())
        gestor.bloquea()
    }

    private fun reporta(resultado: Apertura) {
        val texto = mensajeDe(resultado)
        if (texto == null) avisos.limpia() else avisos.muestra(texto)
    }

    // --- Huella -----------------------------------------------------------

    private val _huellaActiva = MutableStateFlow(gestor.prefs.biometricoActivo)
    val huellaActiva: StateFlow<Boolean> = _huellaActiva.asStateFlow()

    /**
     * Si el atajo se puede ofrecer. Se calcula una vez y no en cada
     * recomposicion: preguntarlo abre el Keystore y lee preferencias, y antes
     * eso pasaba con cada tecla escrita en el campo de la maestra.
     */
    fun puedeOfrecerHuella(): Boolean =
        _huellaActiva.value && Biometria.disponible(getApplication())

    /** Pide la huella y, si el hardware suelta el cifrador, abre la boveda. */
    fun desbloqueaConHuella(actividad: FragmentActivity) {
        val sobre = gestor.prefs.sobreBiometrico ?: return
        val cifrador = AlmacenBiometrico.cifradorParaLeer(sobre)
        if (cifrador == null) {
            // La llave del Keystore ya no sirve: casi siempre porque se
            // registro una huella nueva en el telefono, que la invalida a
            // proposito. Queda la contrasena maestra.
            apagaHuella(avisar = false)
            avisos.muestra("El atajo de huella se invalido. Usa tu contrasena maestra.")
            return
        }
        Biometria.pide(
            actividad = actividad,
            cifrador = cifrador,
            titulo = "Abrir la boveda",
            subtitulo = "Confirma tu identidad",
            alExito = { autenticado -> lanza { reporta(gestor.abreConBiometrico(autenticado)) } },
            alFallo = { avisos.muestra(it) }
        )
    }

    /** Envuelve la frase de la base con una llave nueva ligada a la huella. */
    fun activaHuella(actividad: FragmentActivity) {
        if (!Biometria.disponible(getApplication())) {
            avisos.muestra("Este equipo no tiene biometria fuerte registrada.")
            return
        }
        val cifrador = runCatching {
            AlmacenBiometrico.cifradorParaGuardar(getApplication())
        }.getOrNull()
        if (cifrador == null) {
            avisos.muestra("No se pudo preparar la llave del equipo.")
            return
        }
        Biometria.pide(
            actividad = actividad,
            cifrador = cifrador,
            titulo = "Activar desbloqueo con huella",
            subtitulo = "Confirma para guardar la llave",
            alExito = { autenticado ->
                val ok = gestor.activaBiometrico(autenticado)
                _huellaActiva.value = gestor.prefs.biometricoActivo
                avisos.muestra(
                    if (ok) "Desbloqueo con huella activado."
                    else "No se pudo activar el desbloqueo con huella."
                )
            },
            alFallo = {
                _huellaActiva.value = gestor.prefs.biometricoActivo
                avisos.muestra(it)
            }
        )
    }

    fun apagaHuella(avisar: Boolean = true) {
        gestor.prefs.apagaBiometrico()
        _huellaActiva.value = false
        if (avisar) avisos.muestra("Desbloqueo con huella desactivado.")
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
        val segundos = _segundosPortapapeles.value
        Portapapeles.copia(getApplication(), etiqueta, texto, aplicacion.alcance, segundos)
        avisos.muestra(
            if (segundos > 0) "Copiado. Se borra del portapapeles en $segundos s." else "Copiado."
        )
    }

    // --- Ajustes ----------------------------------------------------------
    //
    // Se leen de preferencias una vez, al construir el modelo, y a partir de
    // ahi viven como estado observable. La pantalla de ajustes ya no escribe
    // en disco por su cuenta: pide el cambio y lo ve reflejado.

    private val _minutosAutobloqueo = MutableStateFlow(gestor.prefs.minutosAutobloqueo)
    val minutosAutobloqueo: StateFlow<Int> = _minutosAutobloqueo.asStateFlow()

    private val _segundosPortapapeles = MutableStateFlow(gestor.prefs.segundosPortapapeles)
    val segundosPortapapeles: StateFlow<Int> = _segundosPortapapeles.asStateFlow()

    private val _borrarTrasFallos = MutableStateFlow(gestor.prefs.borrarTrasFallos)
    val borrarTrasFallos: StateFlow<Int> = _borrarTrasFallos.asStateFlow()

    fun cambiaAutobloqueo(minutos: Int) {
        gestor.prefs.minutosAutobloqueo = minutos
        _minutosAutobloqueo.value = minutos
    }

    fun cambiaSegundosPortapapeles(segundos: Int) {
        gestor.prefs.segundosPortapapeles = segundos
        _segundosPortapapeles.value = segundos
    }

    fun cambiaBorrarTrasFallos(intentos: Int) {
        gestor.prefs.borrarTrasFallos = intentos
        _borrarTrasFallos.value = intentos
    }

    fun cambiaMaestra(actual: String, nueva: String) = lanza {
        val a = actual.toCharArray()
        val n = nueva.toCharArray()
        try {
            avisos.muestra(
                if (gestor.cambiaMaestra(a, n)) "Contrasena maestra actualizada."
                else "La contrasena actual no es correcta."
            )
        } finally {
            a.fill(' ')
            n.fill(' ')
        }
    }

    fun restablece() {
        Portapapeles.limpiaTodo(getApplication())
        gestor.restablece()
        _huellaActiva.value = false
    }

    // --- Plomeria ---------------------------------------------------------

    private fun lanza(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _ocupado.value = true
            try {
                bloque()
            } catch (e: Exception) {
                avisos.muestra(e.message ?: "Algo salio mal.")
            } finally {
                _ocupado.value = false
            }
        }
    }

    private companion object {
        /**
         * Las marcas de acento que deja NFD al separar cada letra de su tilde.
         * Constante y no local: compilarla en cada llamada era el grueso del
         * costo de buscar.
         */
        val ACENTOS = Regex("\\p{Mn}+")

        /**
         * Busca ignorando acentos y mayusculas: escribir "correo" debe
         * encontrar "Correo electronico" y tambien "Corréo".
         */
        fun sinAcentos(texto: String): String =
            Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(ACENTOS, "")
    }
}
