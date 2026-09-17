package com.carlosalbertoxw.boveda.ui

import android.content.Context
import com.carlosalbertoxw.boveda.R
import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import com.carlosalbertoxw.boveda.seguridad.Apertura

/**
 * Como se cuenta al usuario lo que devolvieron las capas de abajo.
 *
 * Estaba metido en los `when` del modelo de vista, mezclado con la
 * orquestacion. Separado se lee de un vistazo si un mensaje es claro, se puede
 * probar sin levantar nada, y los textos salen de strings.xml en vez de estar
 * incrustados a mitad de un cuando.
 *
 * Ninguno de estos textos distingue "contrasena mala" de "archivo manipulado":
 * en AES-GCM las dos cosas fallan igual y esta bien que asi sea.
 */

fun mensajeDe(contexto: Context, resultado: Apertura): String? = when (resultado) {
    is Apertura.Ok -> null
    is Apertura.Incorrecta ->
        if (resultado.esperaSegundos > 0) {
            contexto.getString(R.string.aviso_incorrecta_espera, resultado.esperaSegundos)
        } else {
            contexto.getString(R.string.aviso_incorrecta, resultado.fallos)
        }
    is Apertura.EnEspera -> contexto.getString(R.string.aviso_en_espera, resultado.segundos)
    is Apertura.Borrada -> contexto.getString(R.string.aviso_borrada)
    is Apertura.Falla -> resultado.mensaje
}

fun mensajeDe(contexto: Context, resultado: GestorRespaldo.Exportacion): String = when (resultado) {
    is GestorRespaldo.Exportacion.Ok ->
        contexto.getString(R.string.aviso_respaldo_creado, resultado.entradas)
    is GestorRespaldo.Exportacion.MaestraIncorrecta ->
        contexto.getString(R.string.aviso_respaldo_maestra_incorrecta)
    is GestorRespaldo.Exportacion.BovedaCerrada ->
        contexto.getString(R.string.aviso_respaldo_boveda_cerrada)
    is GestorRespaldo.Exportacion.Falla -> resultado.mensaje
}

fun mensajeDe(contexto: Context, resultado: GestorRespaldo.Importacion): String = when (resultado) {
    is GestorRespaldo.Importacion.Ok -> buildString {
        append(contexto.getString(R.string.aviso_importadas, resultado.agregadas))
        if (resultado.actualizadas > 0) {
            append(" ")
            append(contexto.getString(R.string.aviso_actualizadas, resultado.actualizadas))
        }
        if (resultado.omitidas > 0) {
            append(" ")
            append(contexto.getString(R.string.aviso_omitidas, resultado.omitidas))
        }
    }
    is GestorRespaldo.Importacion.ContrasenaIncorrecta ->
        contexto.getString(R.string.aviso_importar_clave_incorrecta)
    is GestorRespaldo.Importacion.NoEsUnRespaldo ->
        contexto.getString(R.string.aviso_importar_no_es_respaldo)
    is GestorRespaldo.Importacion.BovedaCerrada ->
        contexto.getString(R.string.aviso_respaldo_boveda_cerrada)
    is GestorRespaldo.Importacion.Falla -> resultado.mensaje
}
