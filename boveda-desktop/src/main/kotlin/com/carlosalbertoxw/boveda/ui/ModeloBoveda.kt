package com.carlosalbertoxw.boveda.ui

import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.HistorialContrasena
import com.carlosalbertoxw.boveda.datos.Papelera
import com.carlosalbertoxw.boveda.seguridad.Apertura
import com.carlosalbertoxw.boveda.seguridad.EstadoBoveda
import com.carlosalbertoxw.boveda.seguridad.Portapapeles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import java.nio.file.Path
import java.text.Normalizer

/**
 * El unico camino de la interfaz hacia la boveda.
 *
 * `gestor` es privado a proposito: todo lo que cruza esa linea pasa por un
 * metodo con nombre de este archivo, y los ajustes salen como estado
 * observable en vez de como lecturas de disco a mitad de una recomposicion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeloBoveda(private val aplicacion: AplicacionBoveda) : Modelo(aplicacion.avisos) {

    private val gestor = aplicacion.gestor
    private val avisos = aplicacion.avisos

    val estado: StateFlow<EstadoBoveda> = gestor.estado

    val carpetaDatos: Path get() = gestor.carpeta

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    /**
     * La lista solo existe mientras la boveda esta abierta. Al bloquear, el
     * flujo cambia a lista vacia y Compose descarta de inmediato lo que tenia
     * pintado, sin que quede una copia colgando en el arbol.
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
            .stateIn(alcance, SharingStarted.WhileSubscribed(5_000), emptyList())

    private class Indexada(val credencial: Credencial, val buscable: String)

    /** El texto por el que se busca, normalizado una sola vez por credencial y no una vez por tecla. */
    private val indexadas: StateFlow<List<Indexada>> =
        todas
            .map { lista ->
                lista.map { Indexada(it, sinAcentos("${it.titulo}\n${it.usuario}\n${it.sitio}")) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(alcance, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            .stateIn(alcance, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun busca(texto: String) { _busqueda.value = texto }

    /**
     * La papelera se vacia sola al abrir la boveda. No hay ninguna tarea
     * programada que lo haga, y no puede haberla: sin boveda abierta no hay
     * frase, y sin frase no hay base que purgar.
     */
    init {
        alcance.launch {
            gestor.estado.collect { estado ->
                if (estado !is EstadoBoveda.Abierta) {
                    // Lo buscado no sobrevive al candado: al reabrir, el
                    // listado empieza limpio.
                    _busqueda.value = ""
                    return@collect
                }
                runCatching {
                    estado.db.credencialDao().purga(Papelera.limite(System.currentTimeMillis()))
                }
            }
        }
    }

    // --- Apertura y cierre ----------------------------------------------

    /**
     * Cuanto falta para volver a aceptar intentos. Se vuelve a preguntar en
     * bucle en vez de descontar en memoria porque la espera vive en disco y
     * debe sobrevivir a que el usuario cierre y reabra la app para reintentar.
     */
    val esperaSegundos: StateFlow<Int> =
        flow {
            while (true) {
                emit(gestor.segundosDeEspera())
                delay(500)
            }
        }.stateIn(alcance, SharingStarted.WhileSubscribed(2_000), 0)

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
        Portapapeles.limpiaTodo()
        gestor.bloquea()
    }

    private fun reporta(resultado: Apertura) {
        val texto = mensajeDe(resultado)
        if (texto == null) avisos.limpia() else avisos.muestra(texto)
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
        avisos.muestra(Textos.avisoALaPapelera(Papelera.DIAS_RETENCION))
    }

    fun copia(texto: String) {
        val segundos = _segundosPortapapeles.value
        if (!Portapapeles.copia(texto, aplicacion.alcance, segundos)) {
            avisos.muestra(Textos.AVISO_GENERICO)
            return
        }
        avisos.muestra(
            if (segundos > 0) Textos.avisoCopiadoConBorrado(segundos) else Textos.AVISO_COPIADO
        )
    }

    // --- Historial de contrasenas -----------------------------------------

    /**
     * Las contrasenas que esta entrada tuvo, de la mas reciente a la mas
     * vieja. Solo existe mientras la pantalla del historial esta delante.
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
        avisos.muestra(Textos.AVISO_HISTORIAL_RESTAURADA)
    }

    /**
     * Saca una contrasena vieja del historial para siempre. La condicion de que
     * no sea la actual la impone el DAO, no esta pantalla, asi que aqui se
     * reacciona a lo que de verdad paso.
     */
    fun borraDelHistorial(entrada: HistorialContrasena) = lanza {
        val dao = daoAbierto() ?: return@lanza
        val borradas = dao.borraDelHistorial(entrada.id)
        avisos.muestra(
            if (borradas > 0) Textos.AVISO_HISTORIAL_BORRADA else Textos.AVISO_HISTORIAL_ES_LA_ACTUAL
        )
    }

    // --- Ajustes ----------------------------------------------------------

    private val _minutosAutobloqueo = MutableStateFlow(gestor.prefs.minutosAutobloqueo)
    val minutosAutobloqueo: StateFlow<Int> = _minutosAutobloqueo.asStateFlow()

    private val _segundosPortapapeles = MutableStateFlow(gestor.prefs.segundosPortapapeles)
    val segundosPortapapeles: StateFlow<Int> = _segundosPortapapeles.asStateFlow()

    private val _borrarTrasFallos = MutableStateFlow(gestor.prefs.borrarTrasFallos)
    val borrarTrasFallos: StateFlow<Int> = _borrarTrasFallos.asStateFlow()

    fun cambiaAutobloqueo(minutos: Int) = ajusta {
        gestor.prefs.minutosAutobloqueo = minutos
        _minutosAutobloqueo.value = minutos
    }

    fun cambiaSegundosPortapapeles(segundos: Int) = ajusta {
        gestor.prefs.segundosPortapapeles = segundos
        _segundosPortapapeles.value = segundos
    }

    fun cambiaBorrarTrasFallos(intentos: Int) = ajusta {
        gestor.prefs.borrarTrasFallos = intentos
        _borrarTrasFallos.value = intentos
    }

    /** Un ajuste que no llega al disco no se muestra como cambiado. */
    private fun ajusta(cambio: () -> Unit) {
        runCatching(cambio).onFailure { avisos.muestra(it.message ?: Textos.AVISO_GENERICO) }
    }

    fun cambiaMaestra(actual: String, nueva: String) = lanza {
        val a = actual.toCharArray()
        val n = nueva.toCharArray()
        try {
            avisos.muestra(
                if (gestor.cambiaMaestra(a, n)) Textos.AVISO_MAESTRA_ACTUALIZADA
                else Textos.AVISO_MAESTRA_INCORRECTA_ACTUAL
            )
        } finally {
            a.fill(' ')
            n.fill(' ')
        }
    }

    fun restablece() {
        Portapapeles.limpiaTodo()
        runCatching { gestor.restablece() }
            .onFailure { avisos.muestra(it.message ?: Textos.AVISO_GENERICO) }
        // Los ajustes volvieron a sus valores por omision junto con el resto.
        _minutosAutobloqueo.value = gestor.prefs.minutosAutobloqueo
        _segundosPortapapeles.value = gestor.prefs.segundosPortapapeles
        _borrarTrasFallos.value = gestor.prefs.borrarTrasFallos
    }

    // --- Plomeria ---------------------------------------------------------

    /** El acceso a la base, o null si la boveda se cerro por el camino. */
    private fun daoAbierto() =
        (gestor.estado.value as? EstadoBoveda.Abierta)?.db?.credencialDao()

    private companion object {
        val ACENTOS = Regex("\\p{Mn}+")

        /**
         * Busca ignorando acentos y mayusculas: escribir "correo" debe
         * encontrar "Correo electronico" y tambien "Corréo".
         */
        fun sinAcentos(texto: String): String =
            Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(ACENTOS, "")
    }
}
