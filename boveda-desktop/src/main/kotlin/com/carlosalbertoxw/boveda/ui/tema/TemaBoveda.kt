package com.carlosalbertoxw.boveda.ui.tema

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

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
 * La misma paleta que la app de Android, sin colores dinamicos: hay estados
 * —contrasena visible, boveda a punto de borrarse— que deben verse siempre
 * igual y con el mismo contraste.
 */
@Composable
fun TemaBoveda(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit
) {
    val colores = if (oscuro) Oscuro else Claro
    MaterialTheme(colorScheme = colores) {
        // La barra de desplazamiento por omision es negra y translucida:
        // invisible sobre el fondo oscuro, que es justo donde una pantalla
        // larga parecia cortada.
        CompositionLocalProvider(
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight = 32.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = colores.onSurface.copy(alpha = 0.30f),
                hoverColor = colores.onSurface.copy(alpha = 0.60f)
            ),
            content = contenido
        )
    }
}

/** El escudo del icono del lanzador de Android, trazado con los mismos caminos. */
val IconoBoveda: ImageVector = ImageVector.Builder(
    name = "IconoBoveda",
    defaultWidth = 108.dp,
    defaultHeight = 108.dp,
    viewportWidth = 108f,
    viewportHeight = 108f
).apply {
    addPath(
        pathData = addPathNodes("M18,6H90A12,12 0,0 1,102,18V90A12,12 0,0 1,90,102H18A12,12 0,0 1,6,90V18A12,12 0,0 1,18,6Z"),
        fill = SolidColor(Color(0xFF1B2A4A))
    )
    addPath(
        pathData = addPathNodes("M54,20L78,29.6V51.2C78,65.6 68.4,77 54,82.4C39.6,77 30,65.6 30,51.2V29.6L54,20Z"),
        fill = SolidColor(Color(0xFF7FB3FF))
    )
    addPath(
        pathData = addPathNodes("M54,42.8m-7.2,0a7.2,7.2 0,1 1,14.4 0a7.2,7.2 0,1 1,-14.4 0"),
        fill = SolidColor(Color(0xFF12131A))
    )
    addPath(
        pathData = addPathNodes("M50.4,47.6H57.6V64.4H50.4Z"),
        fill = SolidColor(Color(0xFF12131A))
    )
}.build()
