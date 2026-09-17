package com.carlosalbertoxw.boveda.escritorio

import java.nio.file.Path

/**
 * Donde vive la boveda en cada sistema.
 *
 * En Windows se usa la carpeta **local** (`%LOCALAPPDATA%`) y no la itinerante
 * (`%APPDATA%`): en equipos de empresa la itinerante se copia a un servidor al
 * cerrar sesion, y la boveda no debe viajar a ningun sitio por su cuenta. Es el
 * mismo criterio que `allowBackup=false` en Android.
 */
object Rutas {

    /** Con `-Dboveda.datos=<carpeta>` se usa otra carpeta: para probar sin tocar la de verdad. */
    const val PROPIEDAD_DATOS = "boveda.datos"

    fun directorioDatos(): Path {
        System.getProperty(PROPIEDAD_DATOS)?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }

        val sistema = System.getProperty("os.name").orEmpty().lowercase()
        val casa = System.getProperty("user.home")
        return when {
            "win" in sistema -> {
                val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                    ?: Path.of(casa, "AppData", "Local").toString()
                Path.of(local, "Boveda")
            }
            "mac" in sistema -> Path.of(casa, "Library", "Application Support", "Boveda")
            else -> {
                val datos = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?: Path.of(casa, ".local", "share").toString()
                Path.of(datos, "boveda")
            }
        }
    }
}
