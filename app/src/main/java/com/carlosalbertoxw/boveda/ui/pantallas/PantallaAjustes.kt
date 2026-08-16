package com.carlosalbertoxw.boveda.ui.pantallas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.carlosalbertoxw.boveda.datos.Respaldo
import com.carlosalbertoxw.boveda.seguridad.AlmacenBiometrico
import com.carlosalbertoxw.boveda.seguridad.Biometria
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(modelo: ModeloBoveda, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val actividad = contexto as? FragmentActivity
    val prefs = modelo.gestor.prefs

    var autobloqueo by remember { mutableIntStateOf(prefs.minutosAutobloqueo) }
    var portapapeles by remember { mutableIntStateOf(prefs.segundosPortapapeles) }
    var borrarTras by remember { mutableIntStateOf(prefs.borrarTrasFallos) }
    var huella by remember { mutableStateOf(prefs.biometricoActivo) }

    var cambiandoMaestra by remember { mutableStateOf(false) }
    var confirmandoBorrado by remember { mutableStateOf(false) }

    // Primero se elige el archivo y solo despues se piden las contrasenas: asi
    // ningun secreto queda esperando en memoria mientras el selector del
    // sistema esta al frente.
    var destinoExportar by remember { mutableStateOf<Uri?>(null) }
    var origenImportar by remember { mutableStateOf<Uri?>(null) }

    val abreCreador = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Respaldo.TIPO_MIME)
    ) { uri -> destinoExportar = uri }

    val abreLector = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> origenImportar = uri }

    fun activaHuella() {
        val act = actividad ?: return
        if (!Biometria.disponible(contexto)) {
            modelo.avisa("Este equipo no tiene biometria fuerte registrada.")
            return
        }
        val cifrador = runCatching { AlmacenBiometrico.cifradorParaGuardar(contexto) }.getOrNull()
        if (cifrador == null) {
            modelo.avisa("No se pudo preparar la llave del equipo.")
            return
        }
        Biometria.pide(
            actividad = act,
            cifrador = cifrador,
            titulo = "Activar desbloqueo con huella",
            subtitulo = "Confirma para guardar la llave",
            alExito = {
                modelo.activaBiometrico(it)
                huella = prefs.biometricoActivo
            },
            alFallo = {
                modelo.avisa(it)
                huella = prefs.biometricoActivo
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Seccion("Cierre automatico", "Cuanto aguanta abierta la boveda sin que la uses.") {
                Opciones(
                    opciones = listOf(0 to "Al instante", 1 to "1 min", 5 to "5 min", 15 to "15 min"),
                    elegida = autobloqueo,
                    alElegir = { autobloqueo = it; prefs.minutosAutobloqueo = it }
                )
            }

            Seccion(
                "Borrado del portapapeles",
                "Tiempo antes de limpiar lo que copias. El portapapeles es compartido " +
                    "con todo el sistema: cuanto menos dure ahi una contrasena, mejor."
            ) {
                Opciones(
                    opciones = listOf(0 to "No borrar", 15 to "15 s", 30 to "30 s", 60 to "60 s"),
                    elegida = portapapeles,
                    alElegir = { portapapeles = it; prefs.segundosPortapapeles = it }
                )
            }

            Seccion(
                "Desbloqueo con huella",
                "Atajo para no teclear la maestra. La llave vive en el chip seguro del " +
                    "telefono y se destruye sola si se registra una huella nueva."
            ) {
                Interruptor("Activado", huella) { activar ->
                    if (activar) {
                        activaHuella()
                    } else {
                        modelo.apagaBiometrico()
                        huella = false
                    }
                }
            }

            Seccion(
                "Borrar tras intentos fallidos",
                "Destruye la boveda al llegar a ese numero de contrasenas erroneas " +
                    "seguidas. Protege ante un telefono perdido; usalo solo si tienes " +
                    "tus datos anotados en otro lado."
            ) {
                Opciones(
                    opciones = listOf(0 to "Nunca", 10 to "10", 20 to "20"),
                    elegida = borrarTras,
                    alElegir = { borrarTras = it; prefs.borrarTrasFallos = it }
                )
            }

            OutlinedButton(
                onClick = { cambiandoMaestra = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cambiar contrasena maestra") }

            Seccion(
                "Respaldo",
                "Un archivo cifrado con su propia contrasena, para no perder la boveda " +
                    "si pierdes el telefono. Guardalo donde puedas recuperarlo despues."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // El candado tiene que saber que la salida al
                            // selector de archivos es esperada, o cerraria la
                            // boveda a media exportacion.
                            modelo.permiteSalidaBreve()
                            abreCreador.launch(Respaldo.nombreSugerido())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Exportar respaldo cifrado") }

                    OutlinedButton(
                        onClick = {
                            modelo.permiteSalidaBreve()
                            // "*/*" y no application/json: segun de donde venga
                            // el archivo, el proveedor puede anunciarlo con otro
                            // tipo y saldria en gris, imposible de elegir.
                            abreLector.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Importar respaldo") }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Borrar la boveda",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Elimina todas las entradas, la contrasena maestra y la llave del " +
                            "equipo. No hay copia en ningun lado, asi que no se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmandoBorrado = true }) {
                        Text("Borrar todo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                "Esta app no tiene permiso de internet. No hay sincronizacion, ni copia en " +
                    "la nube, ni telemetria: lo que guardas vive solo en este telefono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (cambiandoMaestra) {
        DialogoCambioMaestra(
            alConfirmar = { actual, nueva ->
                modelo.cambiaMaestra(actual, nueva)
                cambiandoMaestra = false
            },
            alCerrar = { cambiandoMaestra = false }
        )
    }

    destinoExportar?.let { destino ->
        DialogoExportar(
            alConfirmar = { maestra, clave ->
                modelo.exporta(destino, maestra, clave)
                destinoExportar = null
            },
            alCerrar = { destinoExportar = null }
        )
    }

    origenImportar?.let { origen ->
        DialogoImportar(
            alConfirmar = { clave, reemplazar ->
                modelo.importa(origen, clave, reemplazar)
                origenImportar = null
            },
            alCerrar = { origenImportar = null }
        )
    }

    if (confirmandoBorrado) {
        DialogoBorradoTotal(
            alConfirmar = {
                confirmandoBorrado = false
                modelo.restablece()
            },
            alCerrar = { confirmandoBorrado = false }
        )
    }
}

@Composable
private fun Seccion(titulo: String, explicacion: String, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            explicacion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        contenido()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Opciones(
    opciones: List<Pair<Int, String>>,
    elegida: Int,
    alElegir: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (valor, etiqueta) ->
            FilterChip(
                selected = elegida == valor,
                onClick = { alElegir(valor) },
                label = { Text(etiqueta) }
            )
        }
    }
}

@Composable
private fun DialogoCambioMaestra(
    alConfirmar: (String, String) -> Unit,
    alCerrar: () -> Unit
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }

    val valida = actual.isNotEmpty() && nueva.length >= 10 && nueva == repetida

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text("Cambiar contrasena maestra") },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoSecreto(actual, { actual = it }, "Contrasena actual")
                CampoSecreto(nueva, { nueva = it }, "Nueva contrasena")
                MedidorFuerza(nueva)
                CampoSecreto(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = "Repite la nueva",
                    imeAction = ImeAction.Done
                )
                Text(
                    "Tus entradas no se vuelven a cifrar: lo que cambia es la llave que " +
                        "protege la boveda, asi que el cambio es inmediato.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(actual, nueva) }, enabled = valida) {
                Text("Cambiar")
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoExportar(
    alConfirmar: (String, String) -> Unit,
    alCerrar: () -> Unit
) {
    var maestra by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }

    val valida = maestra.isNotEmpty() && clave.length >= 10 && clave == repetida

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text("Exportar respaldo") },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sacar todas tus contrasenas de la boveda es la operacion mas " +
                        "delicada de la app, asi que se pide la maestra otra vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoSecreto(maestra, { maestra = it }, "Contrasena maestra")

                Spacer(Modifier.height(4.dp))
                Text(
                    "El archivo lleva su propia contrasena. Puede ser la misma maestra, " +
                        "pero si algun dia la cambias, los respaldos viejos seguiran " +
                        "abriendose con la de entonces.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoSecreto(clave, { clave = it }, "Contrasena del archivo")
                MedidorFuerza(clave)
                CampoSecreto(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = "Repite la del archivo",
                    imeAction = ImeAction.Done
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(maestra, clave) }, enabled = valida) {
                Text("Exportar")
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoImportar(
    alConfirmar: (String, Boolean) -> Unit,
    alCerrar: () -> Unit
) {
    var clave by remember { mutableStateOf("") }
    var reemplazar by remember { mutableStateOf(false) }

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text("Importar respaldo") },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoSecreto(
                    valor = clave,
                    alCambiar = { clave = it },
                    etiqueta = "Contrasena del archivo",
                    imeAction = ImeAction.Done
                )
                Interruptor("Reemplazar todo lo que tengo", reemplazar) { reemplazar = it }
                Text(
                    if (reemplazar) {
                        "Se borran las entradas actuales y quedan solo las del archivo."
                    } else {
                        "Se agregan al final. Lo que ya este identico no se duplica."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(clave, reemplazar) }, enabled = clave.isNotEmpty()) {
                Text("Importar")
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoBorradoTotal(alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    var escrito by remember { mutableStateOf("") }
    // Se pide teclear la palabra: un borrado irreversible no puede quedar a un
    // solo toque de distancia.
    val confirmado = escrito.trim().equals("BORRAR", ignoreCase = false)

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text("Borrar la boveda") },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Se perderan todas las entradas. Escribe BORRAR para confirmar.")
                OutlinedTextField(
                    value = escrito,
                    onValueChange = { escrito = it },
                    singleLine = true,
                    label = { Text("BORRAR") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = alConfirmar, enabled = confirmado) {
                Text("Borrar todo", color = MaterialTheme.colorScheme.error)
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
