package com.carlosalbertoxw.boveda.ui.pantallas

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.ui.ModeloBoveda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaListado(
    modelo: ModeloBoveda,
    focoBusqueda: FocusRequester,
    alAbrir: (Long) -> Unit,
    alCrear: () -> Unit,
    alPapelera: () -> Unit,
    alAjustes: () -> Unit
) {
    val credenciales by modelo.credenciales.collectAsState()
    val busqueda by modelo.busqueda.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Textos.NOMBRE_APP) },
                actions = {
                    IconButton(onClick = { modelo.bloquea() }) {
                        Icon(Icons.Filled.Lock, contentDescription = Textos.LISTADO_CERRAR_BOVEDA)
                    }
                    IconButton(onClick = alPapelera) {
                        Icon(Icons.Filled.Delete, contentDescription = Textos.LISTADO_PAPELERA)
                    }
                    IconButton(onClick = alAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = Textos.LISTADO_AJUSTES)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = alCrear) {
                Icon(Icons.Filled.Add, contentDescription = Textos.LISTADO_NUEVA_ENTRADA)
            }
        }
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = modelo::busca,
                label = { Text(Textos.LISTADO_BUSCAR) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focoBusqueda)
                    // Enter en el buscador abre la primera coincidencia: con el
                    // teclado se llega a una contrasena sin tocar el raton.
                    .alPulsarEnter { credenciales.firstOrNull()?.let { alAbrir(it.id) } }
            )

            if (credenciales.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (busqueda.isBlank()) Textos.LISTADO_VACIO
                        else Textos.listadoSinResultados(busqueda),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (busqueda.isBlank()) {
                        Text(
                            Textos.LISTADO_VACIO_AYUDA,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val estadoLista = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = estadoLista,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
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
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(estadoLista),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
        }
    }
}
