package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.Credencial
import com.carlosalbertoxw.boveda.datos.Papelera
import com.carlosalbertoxw.boveda.ui.ModeloPapelera

/**
 * Lo borrado, mientras dura el plazo.
 *
 * La contrasena no aparece aqui, ni tapada con puntos, ni hay forma de copiarla
 * ni de abrir el detalle: la papelera sirve para reconocer que se borro y
 * decidir si vuelve, no para consultar secretos. Quien necesite la contrasena
 * restaura la entrada primero, y entonces vuelve a estar donde siempre estuvo.
 * Es la misma regla del listado, y por el mismo motivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPapelera(alVolver: () -> Unit, modelo: ModeloPapelera = viewModel()) {
    val entradas by modelo.entradas.collectAsStateWithLifecycle()
    var confirmaVaciado by remember { mutableStateOf(false) }
    var aBorrar by remember { mutableStateOf<Credencial?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.papelera_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                },
                actions = {
                    if (entradas.isNotEmpty()) {
                        IconButton(onClick = { confirmaVaciado = true }) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = stringResource(R.string.papelera_vaciar)
                            )
                        }
                    }
                }
            )
        }
    ) { relleno ->
        if (entradas.isEmpty()) {
            PapeleraVacia(Modifier.fillMaxSize().padding(relleno))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entradas, key = { it.id }) { credencial ->
                FichaBorrada(
                    credencial = credencial,
                    alRestaurar = { modelo.restaura(credencial) },
                    alBorrar = { aBorrar = credencial }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.papelera_explicacion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    aBorrar?.let { credencial ->
        DialogoSeguro(
            alCerrar = { aBorrar = null },
            titulo = {
                Text(stringResource(R.string.papelera_borrar_titulo, credencial.titulo))
            },
            contenido = { Text(stringResource(R.string.papelera_borrar_aviso)) },
            botonConfirmar = {
                TextButton(onClick = {
                    aBorrar = null
                    modelo.borraDeVerdad(credencial)
                }) {
                    Text(
                        stringResource(R.string.papelera_borrar_ahora),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            botonCancelar = {
                TextButton(onClick = { aBorrar = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }

    if (confirmaVaciado) {
        DialogoSeguro(
            alCerrar = { confirmaVaciado = false },
            titulo = { Text(stringResource(R.string.papelera_vaciar_titulo)) },
            contenido = {
                Text(stringResource(R.string.papelera_vaciar_aviso, entradas.size))
            },
            botonConfirmar = {
                TextButton(onClick = {
                    confirmaVaciado = false
                    modelo.vacia()
                }) {
                    Text(
                        stringResource(R.string.papelera_vaciar),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            botonCancelar = {
                TextButton(onClick = { confirmaVaciado = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun PapeleraVacia(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.papelera_vacia),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.papelera_vacia_ayuda, Papelera.DIAS_RETENCION),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FichaBorrada(
    credencial: Credencial,
    alRestaurar: () -> Unit,
    alBorrar: () -> Unit
) {
    // La cuenta atras se calcula al pintar, contra el reloj de ahora: la fecha
    // guardada es la del borrado, y lo que le interesa a quien mira es cuanto
    // le queda para decidir.
    val eliminadoEn = credencial.eliminadoEn
    val dias = remember(eliminadoEn) {
        eliminadoEn?.let { Papelera.diasRestantes(it, System.currentTimeMillis()) } ?: 0
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                credencial.titulo,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (credencial.usuario.isNotBlank()) {
                Text(
                    credencial.usuario,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (dias > 0) {
                    pluralStringResource(R.plurals.papelera_restante, dias, dias)
                } else {
                    stringResource(R.string.papelera_vence_ya)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = alRestaurar) {
                    Text(stringResource(R.string.papelera_restaurar))
                }
                TextButton(onClick = alBorrar) {
                    Text(
                        stringResource(R.string.papelera_borrar_ahora),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
