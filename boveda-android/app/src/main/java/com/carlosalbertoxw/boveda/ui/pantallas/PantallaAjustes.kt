package com.carlosalbertoxw.boveda.ui.pantallas

import android.net.Uri
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.Respaldo
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import com.carlosalbertoxw.boveda.ui.ModeloRespaldo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    modelo: ModeloBoveda,
    alVolver: () -> Unit,
    respaldo: ModeloRespaldo = viewModel()
) {
    val actividad = LocalActivity.current as? FragmentActivity

    val autobloqueo by modelo.minutosAutobloqueo.collectAsStateWithLifecycle()
    val portapapeles by modelo.segundosPortapapeles.collectAsStateWithLifecycle()
    val borrarTras by modelo.borrarTrasFallos.collectAsStateWithLifecycle()
    val huella by modelo.huellaActiva.collectAsStateWithLifecycle()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ajustes_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
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
            Seccion(
                stringResource(R.string.ajustes_cierre_titulo),
                stringResource(R.string.ajustes_cierre_explicacion)
            ) {
                Opciones(
                    opciones = listOf(
                        0 to stringResource(R.string.ajustes_cierre_instante),
                        1 to stringResource(R.string.ajustes_cierre_1),
                        5 to stringResource(R.string.ajustes_cierre_5),
                        15 to stringResource(R.string.ajustes_cierre_15)
                    ),
                    elegida = autobloqueo,
                    alElegir = modelo::cambiaAutobloqueo
                )
            }

            Seccion(
                stringResource(R.string.ajustes_portapapeles_titulo),
                stringResource(R.string.ajustes_portapapeles_explicacion)
            ) {
                Opciones(
                    opciones = listOf(
                        0 to stringResource(R.string.ajustes_portapapeles_nunca),
                        15 to stringResource(R.string.ajustes_portapapeles_15),
                        30 to stringResource(R.string.ajustes_portapapeles_30),
                        60 to stringResource(R.string.ajustes_portapapeles_60)
                    ),
                    elegida = portapapeles,
                    alElegir = modelo::cambiaSegundosPortapapeles
                )
            }

            Seccion(
                stringResource(R.string.ajustes_huella_titulo),
                stringResource(R.string.ajustes_huella_explicacion)
            ) {
                Interruptor(stringResource(R.string.ajustes_activado), huella) { activar ->
                    when {
                        !activar -> modelo.apagaHuella()
                        actividad != null -> modelo.activaHuella(actividad)
                    }
                }
            }

            Seccion(
                stringResource(R.string.ajustes_borrado_titulo),
                stringResource(R.string.ajustes_borrado_explicacion)
            ) {
                Opciones(
                    opciones = listOf(
                        0 to stringResource(R.string.ajustes_borrado_nunca),
                        10 to stringResource(R.string.ajustes_borrado_10),
                        20 to stringResource(R.string.ajustes_borrado_20)
                    ),
                    elegida = borrarTras,
                    alElegir = modelo::cambiaBorrarTrasFallos
                )
            }

            OutlinedButton(
                onClick = { cambiandoMaestra = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ajustes_cambiar_maestra)) }

            Seccion(
                stringResource(R.string.ajustes_respaldo_titulo),
                stringResource(R.string.ajustes_respaldo_explicacion)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // El candado tiene que saber que la salida al
                            // selector de archivos es esperada, o cerraria la
                            // boveda a media exportacion.
                            respaldo.permiteSalidaBreve()
                            abreCreador.launch(Respaldo.nombreSugerido())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.ajustes_exportar)) }

                    OutlinedButton(
                        onClick = {
                            respaldo.permiteSalidaBreve()
                            // "*/*" y no application/json: segun de donde venga
                            // el archivo, el proveedor puede anunciarlo con otro
                            // tipo y saldria en gris, imposible de elegir.
                            abreLector.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.ajustes_importar)) }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.ajustes_borrar_titulo),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ajustes_borrar_explicacion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmandoBorrado = true }) {
                        Text(
                            stringResource(R.string.ajustes_borrar_boton),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.ajustes_sin_internet),
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
                respaldo.exporta(destino, maestra, clave)
                destinoExportar = null
            },
            alCerrar = { destinoExportar = null }
        )
    }

    origenImportar?.let { origen ->
        DialogoImportar(
            alConfirmar = { clave, reemplazar ->
                respaldo.importa(origen, clave, reemplazar)
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
        titulo = { Text(stringResource(R.string.ajustes_cambiar_maestra)) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoSecreto(actual, { actual = it }, stringResource(R.string.cambio_actual))
                CampoSecreto(nueva, { nueva = it }, stringResource(R.string.cambio_nueva))
                MedidorFuerza(nueva)
                CampoSecreto(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = stringResource(R.string.cambio_repite),
                    imeAction = ImeAction.Done
                )
                Text(
                    stringResource(R.string.cambio_explicacion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(actual, nueva) }, enabled = valida) {
                Text(stringResource(R.string.cambio_confirmar))
            }
        },
        botonCancelar = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) }
        }
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
        titulo = { Text(stringResource(R.string.exportar_titulo)) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.exportar_explicacion_maestra),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoSecreto(
                    maestra,
                    { maestra = it },
                    stringResource(R.string.contrasena_maestra)
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.exportar_explicacion_archivo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoSecreto(clave, { clave = it }, stringResource(R.string.exportar_clave_archivo))
                MedidorFuerza(clave)
                CampoSecreto(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = stringResource(R.string.exportar_repite_archivo),
                    imeAction = ImeAction.Done
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(maestra, clave) }, enabled = valida) {
                Text(stringResource(R.string.exportar_confirmar))
            }
        },
        botonCancelar = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) }
        }
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
        titulo = { Text(stringResource(R.string.importar_titulo)) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoSecreto(
                    valor = clave,
                    alCambiar = { clave = it },
                    etiqueta = stringResource(R.string.importar_clave),
                    imeAction = ImeAction.Done
                )
                Interruptor(stringResource(R.string.importar_reemplazar), reemplazar) {
                    reemplazar = it
                }
                Text(
                    stringResource(
                        if (reemplazar) R.string.importar_explicacion_reemplazar
                        else R.string.importar_explicacion_fusionar
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(clave, reemplazar) }, enabled = clave.isNotEmpty()) {
                Text(stringResource(R.string.importar_confirmar))
            }
        },
        botonCancelar = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

@Composable
private fun DialogoBorradoTotal(alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    var escrito by remember { mutableStateOf("") }
    // Se pide teclear la palabra: un borrado irreversible no puede quedar a un
    // solo toque de distancia.
    val palabra = stringResource(R.string.borrado_palabra)
    val confirmado = escrito.trim() == palabra

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text(stringResource(R.string.ajustes_borrar_titulo)) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.borrado_instruccion))
                OutlinedTextField(
                    value = escrito,
                    onValueChange = { escrito = it },
                    singleLine = true,
                    label = { Text(palabra) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = alConfirmar, enabled = confirmado) {
                Text(
                    stringResource(R.string.ajustes_borrar_boton),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        botonCancelar = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) }
        }
    )
}
