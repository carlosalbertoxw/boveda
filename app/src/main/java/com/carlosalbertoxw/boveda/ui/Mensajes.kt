package com.carlosalbertoxw.boveda.ui

import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import com.carlosalbertoxw.boveda.seguridad.Apertura

/**
 * Como se cuenta al usuario lo que devolvieron las capas de abajo.
 *
 * Estaba metido en los `when` del modelo de vista, mezclado con la
 * orquestacion. Separado se lee de un vistazo si un mensaje es claro, se puede
 * probar sin levantar nada, y el dia que los textos se muevan a strings.xml hay
 * un solo archivo que tocar.
 *
 * Ninguno de estos textos distingue "contrasena mala" de "archivo manipulado":
 * en AES-GCM las dos cosas fallan igual y esta bien que asi sea.
 */

fun mensajeDe(resultado: Apertura): String? = when (resultado) {
    is Apertura.Ok -> null
    is Apertura.Incorrecta ->
        if (resultado.esperaSegundos > 0) {
            "Contrasena incorrecta. Espera ${resultado.esperaSegundos} s antes de reintentar."
        } else {
            "Contrasena incorrecta. Intento ${resultado.fallos}."
        }
    is Apertura.EnEspera -> "Demasiados intentos. Espera ${resultado.segundos} s."
    is Apertura.Borrada -> "Se alcanzo el limite de intentos: la boveda se borro."
    is Apertura.Falla -> resultado.mensaje
}

fun mensajeDe(resultado: GestorRespaldo.Exportacion): String = when (resultado) {
    is GestorRespaldo.Exportacion.Ok -> "Respaldo creado con ${resultado.entradas} entradas."
    is GestorRespaldo.Exportacion.MaestraIncorrecta -> "La contrasena maestra no es correcta."
    is GestorRespaldo.Exportacion.BovedaCerrada -> "La boveda se cerro antes de terminar."
    is GestorRespaldo.Exportacion.Falla -> resultado.mensaje
}

fun mensajeDe(resultado: GestorRespaldo.Importacion): String = when (resultado) {
    is GestorRespaldo.Importacion.Ok ->
        if (resultado.omitidas > 0) {
            "Importadas ${resultado.agregadas}. Se omitieron ${resultado.omitidas} que ya estaban."
        } else {
            "Importadas ${resultado.agregadas} entradas."
        }
    is GestorRespaldo.Importacion.ContrasenaIncorrecta ->
        "La contrasena del archivo no es correcta, o el archivo fue alterado."
    is GestorRespaldo.Importacion.NoEsUnRespaldo -> "Ese archivo no es un respaldo de Boveda."
    is GestorRespaldo.Importacion.BovedaCerrada -> "La boveda se cerro antes de terminar."
    is GestorRespaldo.Importacion.Falla -> resultado.mensaje
}
