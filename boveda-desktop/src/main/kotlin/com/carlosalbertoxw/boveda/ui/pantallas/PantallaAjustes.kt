package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.AplicacionBoveda
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.Respaldo
import com.carlosalbertoxw.boveda.escritorio.SelectorArchivos
import com.carlosalbertoxw.boveda.ui.LocalVentana
import com.carlosalbertoxw.boveda.ui.ModeloBoveda
import com.carlosalbertoxw.boveda.ui.ModeloRespaldo
import java.nio.file.Path

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    aplicacion: AplicacionBoveda,
    modelo: ModeloBoveda,
    alVolver: () -> Unit
) {
    val respaldo = remember { ModeloRespaldo(aplicacion) }
    DisposableEffect(respaldo) { onDispose { respaldo.libera() } }
    val ventana = LocalVentana.current

    val autobloqueo by modelo.minutosAutobloqueo.collectAsState()
    val portapapeles by modelo.segundosPortapapeles.collectAsState()
    val borrarTras by modelo.borrarTrasFallos.collectAsState()

    var cambiandoMaestra by remember { mutableStateOf(false) }
    var confirmandoBorrado by remember { mutableStateOf(false) }

    // Primero se elige el archivo y solo despues se piden las contrasenas: asi
    // ningun secreto queda esperando en memoria mientras el selector del
    // sistema esta al frente.
    var destinoExportar by remember { mutableStateOf<Path?>(null) }
    var origenImportar by remember { mutableStateOf<Path?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Textos.AJUSTES_TITULO) },
                navigationIcon = { BotonVolver(alVolver) }
            )
        }
    ) { relleno ->
        ColumnaDesplazable(Modifier.fillMaxSize().padding(relleno), espaciado = 16.dp) {
            // Primero el respaldo: en escritorio es a lo que se viene a Ajustes
            // la mayoria de las veces (pasar la boveda desde o hacia el telefono),
            // y no debe quedar escondido debajo del borde de una ventana baja.
            Seccion(Textos.AJUSTES_RESPALDO_TITULO, Textos.AJUSTES_RESPALDO_EXPLICACION) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // El candado tiene que saber que perder el foco por
                            // el selector es esperado, o cerraria la boveda a
                            // media exportacion.
                            respaldo.permiteSalidaBreve()
                            destinoExportar = SelectorArchivos.eligeDestino(ventana, Respaldo.nombreSugerido())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(Textos.AJUSTES_EXPORTAR) }

                    OutlinedButton(
                        onClick = {
                            respaldo.permiteSalidaBreve()
                            origenImportar = SelectorArchivos.eligeOrigen(ventana)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(Textos.AJUSTES_IMPORTAR) }
                }
            }

            Seccion(Textos.AJUSTES_CIERRE_TITULO, Textos.AJUSTES_CIERRE_EXPLICACION) {
                Opciones(
                    opciones = listOf(
                        0 to Textos.AJUSTES_CIERRE_INSTANTE,
                        1 to Textos.AJUSTES_CIERRE_1,
                        5 to Textos.AJUSTES_CIERRE_5,
                        15 to Textos.AJUSTES_CIERRE_15
                    ),
                    elegida = autobloqueo,
                    alElegir = modelo::cambiaAutobloqueo
                )
            }

            Seccion(Textos.AJUSTES_PORTAPAPELES_TITULO, Textos.AJUSTES_PORTAPAPELES_EXPLICACION) {
                Opciones(
                    opciones = listOf(
                        0 to Textos.AJUSTES_PORTAPAPELES_NUNCA,
                        15 to Textos.AJUSTES_PORTAPAPELES_15,
                        30 to Textos.AJUSTES_PORTAPAPELES_30,
                        60 to Textos.AJUSTES_PORTAPAPELES_60
                    ),
                    elegida = portapapeles,
                    alElegir = modelo::cambiaSegundosPortapapeles
                )
            }

            Seccion(Textos.AJUSTES_BORRADO_TITULO, Textos.AJUSTES_BORRADO_EXPLICACION) {
                Opciones(
                    opciones = listOf(
                        0 to Textos.AJUSTES_BORRADO_NUNCA,
                        10 to Textos.AJUSTES_BORRADO_10,
                        20 to Textos.AJUSTES_BORRADO_20
                    ),
                    elegida = borrarTras,
                    alElegir = modelo::cambiaBorrarTrasFallos
                )
            }

            OutlinedButton(onClick = { cambiandoMaestra = true }, modifier = Modifier.fillMaxWidth()) {
                Text(Textos.AJUSTES_CAMBIAR_MAESTRA)
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        Textos.AJUSTES_BORRAR_TITULO,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Textos.AJUSTES_BORRAR_EXPLICACION,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmandoBorrado = true }) {
                        Text(Textos.AJUSTES_BORRAR_BOTON, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                Textos.AJUSTES_SIN_INTERNET,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    Textos.ajustesCarpetaDatos(modelo.carpetaDatos.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            destino = destino,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Opciones(opciones: List<Pair<Int, String>>, elegida: Int, alElegir: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun DialogoCambioMaestra(alConfirmar: (String, String) -> Unit, alCerrar: () -> Unit) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }

    val valida = actual.isNotEmpty() && nueva.length >= 10 && nueva == repetida

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text(Textos.AJUSTES_CAMBIAR_MAESTRA) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoSecreto(actual, { actual = it }, Textos.CAMBIO_ACTUAL)
                CampoSecreto(nueva, { nueva = it }, Textos.CAMBIO_NUEVA)
                MedidorFuerza(nueva)
                CampoSecreto(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = Textos.CAMBIO_REPITE,
                    alEnviar = { if (valida) alConfirmar(actual, nueva) }
                )
                Text(
                    Textos.CAMBIO_EXPLICACION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(actual, nueva) }, enabled = valida) {
                Text(Textos.CAMBIO_CONFIRMAR)
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text(Textos.CANCELAR) } }
    )
}

@Composable
private fun DialogoExportar(destino: Path, alConfirmar: (String, String) -> Unit, alCerrar: () -> Unit) {
    var maestra by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }

    val valida = maestra.isNotEmpty() && clave.length >= 10 && clave == repetida

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text(Textos.EXPORTAR_TITULO) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    Textos.exportarDestino(destino.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    Textos.EXPORTAR_EXPLICACION_MAESTRA,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoSecreto(maestra, { maestra = it }, Textos.CONTRASENA_MAESTRA)

                Spacer(Modifier.height(4.dp))
                Text(
                    Textos.EXPORTAR_EXPLICACION_ARCHIVO,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoSecreto(clave, { clave = it }, Textos.EXPORTAR_CLAVE_ARCHIVO)
                MedidorFuerza(clave)
                CampoSecreto(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = Textos.EXPORTAR_REPITE_ARCHIVO,
                    alEnviar = { if (valida) alConfirmar(maestra, clave) }
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(maestra, clave) }, enabled = valida) {
                Text(Textos.EXPORTAR_CONFIRMAR)
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text(Textos.CANCELAR) } }
    )
}

@Composable
private fun DialogoImportar(alConfirmar: (String, Boolean) -> Unit, alCerrar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    var reemplazar by remember { mutableStateOf(false) }

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text(Textos.IMPORTAR_TITULO) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoSecreto(
                    valor = clave,
                    alCambiar = { clave = it },
                    etiqueta = Textos.IMPORTAR_CLAVE,
                    alEnviar = { if (clave.isNotEmpty()) alConfirmar(clave, reemplazar) }
                )
                Interruptor(Textos.IMPORTAR_REEMPLAZAR, reemplazar) { reemplazar = it }
                Text(
                    if (reemplazar) Textos.IMPORTAR_EXPLICACION_REEMPLAZAR
                    else Textos.IMPORTAR_EXPLICACION_FUSIONAR,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = { alConfirmar(clave, reemplazar) }, enabled = clave.isNotEmpty()) {
                Text(Textos.IMPORTAR_CONFIRMAR)
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text(Textos.CANCELAR) } }
    )
}

@Composable
private fun DialogoBorradoTotal(alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    var escrito by remember { mutableStateOf("") }
    // Se pide teclear la palabra: un borrado irreversible no puede quedar a un
    // solo clic de distancia.
    val confirmado = escrito.trim() == Textos.BORRADO_PALABRA

    DialogoSeguro(
        alCerrar = alCerrar,
        titulo = { Text(Textos.AJUSTES_BORRAR_TITULO) },
        contenido = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Textos.BORRADO_INSTRUCCION)
                OutlinedTextField(
                    value = escrito,
                    onValueChange = { escrito = it },
                    singleLine = true,
                    label = { Text(Textos.BORRADO_PALABRA) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        botonConfirmar = {
            TextButton(onClick = alConfirmar, enabled = confirmado) {
                Text(Textos.AJUSTES_BORRAR_BOTON, color = MaterialTheme.colorScheme.error)
            }
        },
        botonCancelar = { TextButton(onClick = alCerrar) { Text(Textos.CANCELAR) } }
    )
}
