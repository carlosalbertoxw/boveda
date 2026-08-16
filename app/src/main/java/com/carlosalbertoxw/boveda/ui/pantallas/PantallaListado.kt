package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaListado(
    modelo: ModeloBoveda,
    alAbrir: (Long) -> Unit,
    alCrear: () -> Unit,
    alAjustes: () -> Unit
) {
    val credenciales by modelo.credenciales.collectAsStateWithLifecycle()
    val busqueda by modelo.busqueda.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nombre_app)) },
                actions = {
                    IconButton(onClick = { modelo.bloquea() }) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.listado_cerrar_boveda)
                        )
                    }
                    IconButton(onClick = alAjustes) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.listado_ajustes)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = alCrear) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.listado_nueva_entrada)
                )
            }
        }
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = modelo::busca,
                label = { Text(stringResource(R.string.listado_buscar)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (credenciales.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (busqueda.isBlank()) stringResource(R.string.listado_vacio)
                        else stringResource(R.string.listado_sin_resultados, busqueda),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (busqueda.isBlank()) {
                        Text(
                            stringResource(R.string.listado_vacio_ayuda),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(credenciales, key = { it.id }) { credencial ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alAbrir(credencial.id) }
                        ) {
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
                                // La contrasena nunca aparece en el listado, ni
                                // tapada con puntos: hay que abrir la entrada y
                                // pedirlo. Menos secretos en pantalla, menos
                                // ocasiones de que alguien mire de reojo.
                            }
                        }
                    }
                }
            }
        }
    }
}
