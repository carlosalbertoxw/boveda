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
import kotlinx.coroutines.flow.Flow
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
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Papelera
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

    /**
     * La papelera se vacia sola al abrir la boveda.
     *
     * No hay ninguna tarea programada que lo haga, y no puede haberla: sin
     * boveda abierta no hay frase, sin frase no hay base, y una alarma del
     * sistema que despertara a la app a las tres de la manana no tendria con
     * que descifrar nada. Que el plazo se cumpla al abrir significa que una
     * entrada borrada puede sobrevivir a sus treinta dias si nadie usa la app
     * en meses, lo cual es inofensivo: sigue estando dentro del mismo archivo
     * cifrado, y se destruye en cuanto alguien vuelve a mirar.
     */
    init {
        viewModelScope.launch {
            gestor.estado.collect { estado ->
                if (estado !is EstadoBoveda.Abierta) return@collect
                // Si la boveda se cierra a mitad, el fallo no debe tumbar la app:
                // lo vencido seguira vencido en la proxima apertura.
                runCatching {
                    estado.db.credencialDao().purga(Papelera.limite(System.currentTimeMillis()))
                }
            }
        }
    }

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
        val texto = mensajeDe(getApplication(), resultado)
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
            avisos.muestra(texto(R.string.aviso_huella_invalidada))
            return
        }
        Biometria.pide(
            actividad = actividad,
            cifrador = cifrador,
            titulo = texto(R.string.huella_abrir_titulo),
            subtitulo = texto(R.string.huella_abrir_subtitulo),
            alExito = { autenticado -> lanza { reporta(gestor.abreConBiometrico(autenticado)) } },
            alFallo = { avisos.muestra(it) }
        )
    }

    /** Envuelve la frase de la base con una llave nueva ligada a la huella. */
    fun activaHuella(actividad: FragmentActivity) {
        if (!Biometria.disponible(getApplication())) {
            avisos.muestra(texto(R.string.aviso_huella_no_disponible))
            return
        }
        val cifrador = runCatching {
            AlmacenBiometrico.cifradorParaGuardar(getApplication())
        }.getOrNull()
        if (cifrador == null) {
            avisos.muestra(texto(R.string.aviso_huella_sin_llave))
            return
        }
        Biometria.pide(
            actividad = actividad,
            cifrador = cifrador,
            titulo = texto(R.string.huella_activar_titulo),
            subtitulo = texto(R.string.huella_activar_subtitulo),
            alExito = { autenticado ->
                val ok = gestor.activaBiometrico(autenticado)
                _huellaActiva.value = gestor.prefs.biometricoActivo
                avisos.muestra(
                    texto(
                        if (ok) R.string.aviso_huella_activada
                        else R.string.aviso_huella_no_activada
                    )
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
        if (avisar) avisos.muestra(texto(R.string.aviso_huella_desactivada))
    }

    // --- Credenciales -----------------------------------------------------

    fun guarda(credencial: Credencial) = lanza {
        val dao = daoAbierto() ?: return@lanza
        if (credencial.id == 0L) {
            dao.inserta(credencial)
        } else {
            dao.actualiza(credencial.copy(actualizadoEn = System.currentTimeMillis()))
        }
    }

    /**
     * Borrar ya no borra: aparta. La entrada sale del listado y se queda en la
     * papelera hasta que venza el plazo o alguien decida destruirla a mano.
     */
    fun mandaAPapelera(credencial: Credencial) = lanza {
        val dao = daoAbierto() ?: return@lanza
        dao.mandaAPapelera(credencial.id, System.currentTimeMillis())
        avisos.muestra(texto(R.string.aviso_a_la_papelera, Papelera.DIAS_RETENCION))
    }

    fun copia(etiqueta: String, texto: String) {
        val segundos = _segundosPortapapeles.value
        Portapapeles.copia(getApplication(), etiqueta, texto, aplicacion.alcance, segundos)
        avisos.muestra(
            if (segundos > 0) texto(R.string.aviso_copiado_con_borrado, segundos)
            else texto(R.string.aviso_copiado)
        )
    }

    // --- Historial de contrasenas -----------------------------------------

    /**
     * Las contrasenas que esta entrada tuvo, de la mas reciente a la mas
     * vieja. Se pide por id y no se guarda como estado del modelo: solo existe
     * mientras la pantalla del historial esta delante, y desaparece con ella.
     */
    fun historialDe(id: Long): Flow<List<HistorialContrasena>> =
        gestor.estado.flatMapLatest { estado ->
            if (estado is EstadoBoveda.Abierta) {
                estado.db.credencialDao().historialDe(id).catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }

    /** Devuelve la marca de actual a una contrasena anterior, y con ella la entrada. */
    fun usaDelHistorial(entrada: HistorialContrasena) = lanza {
        val dao = daoAbierto() ?: return@lanza
        dao.usaDelHistorial(entrada, System.currentTimeMillis())
        avisos.muestra(texto(R.string.aviso_historial_restaurada))
    }

    /**
     * Saca una contrasena vieja del historial para siempre.
     *
     * La condicion de que no sea la actual la impone el SQL, no esta pantalla,
     * asi que aqui se reacciona a lo que de verdad paso: si no cayo ninguna
     * fila es que se pidio borrar la marcada, y se dice por que no se hizo.
     */
    fun borraDelHistorial(entrada: HistorialContrasena) = lanza {
        val dao = daoAbierto() ?: return@lanza
        val borradas = dao.borraDelHistorial(entrada.id)
        avisos.muestra(
            texto(
                if (borradas > 0) R.string.aviso_historial_borrada
                else R.string.aviso_historial_es_la_actual
            )
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
                texto(
                    if (gestor.cambiaMaestra(a, n)) R.string.aviso_maestra_actualizada
                    else R.string.aviso_maestra_incorrecta_actual
                )
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

    /** El acceso a la base, o null si la boveda se cerro por el camino. */
    private fun daoAbierto() =
        (gestor.estado.value as? EstadoBoveda.Abierta)?.db?.credencialDao()

    /** Atajo para los textos: el modelo tambien habla, y habla en strings.xml. */
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
