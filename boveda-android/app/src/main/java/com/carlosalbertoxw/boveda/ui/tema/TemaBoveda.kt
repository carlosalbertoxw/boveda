package com.carlosalbertoxw.boveda.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Azul = Color(0xFF4C7DF0)
private val AzulClaro = Color(0xFF9CBBFF)
private val Ambar = Color(0xFFF0B44C)
private val Rojo = Color(0xFFE2564E)

private val Oscuro = darkColorScheme(
    primary = AzulClaro,
    onPrimary = Color(0xFF06285C),
    primaryContainer = Color(0xFF1B3A72),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Ambar,
    onSecondary = Color(0xFF3A2500),
    error = Rojo,
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF12131A),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF232532),
    onSurfaceVariant = Color(0xFFC4C6D0)
)

private val Claro = lightColorScheme(
    primary = Azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE2FF),
    onPrimaryContainer = Color(0xFF0B1B47),
    secondary = Color(0xFF8A6100),
    onSecondary = Color.White,
    error = Color(0xFFB3261E),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F)
)

/**
 * Sin color dinamico a proposito. Material You toma la paleta del fondo de
 * pantalla, y en esta app hay estados —contrasena visible, boveda a punto de
 * borrarse— que deben verse siempre igual y con el mismo contraste, sin
 * depender de la foto que el usuario tenga puesta.
 */
@Composable
fun TemaBoveda(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (oscuro) Oscuro else Claro,
        content = contenido
    )
}
