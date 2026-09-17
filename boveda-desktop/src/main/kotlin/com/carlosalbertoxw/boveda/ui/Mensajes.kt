package com.carlosalbertoxw.boveda.ui

import com.carlosalbertoxw.boveda.Textos
import com.carlosalbertoxw.boveda.datos.GestorRespaldo
import com.carlosalbertoxw.boveda.seguridad.Apertura

/**
 * Como se cuenta al usuario lo que devolvieron las capas de abajo.
 *
 * Ninguno de estos textos distingue "contrasena mala" de "archivo manipulado":
 * en AES-GCM las dos cosas fallan igual y esta bien que asi sea.
 */

fun mensajeDe(resultado: Apertura): String? = when (resultado) {
    is Apertura.Ok -> null
    is Apertura.Incorrecta ->
        if (resultado.esperaSegundos > 0) {
            Textos.avisoIncorrectaEspera(resultado.esperaSegundos)
        } else {
            Textos.avisoIncorrecta(resultado.fallos)
        }
    is Apertura.EnEspera -> Textos.avisoEnEspera(resultado.segundos)
    is Apertura.Borrada -> Textos.AVISO_BORRADA
    is Apertura.Falla -> resultado.mensaje
}

fun mensajeDe(resultado: GestorRespaldo.Exportacion): String = when (resultado) {
    is GestorRespaldo.Exportacion.Ok -> Textos.avisoRespaldoCreado(resultado.entradas)
    is GestorRespaldo.Exportacion.MaestraIncorrecta -> Textos.AVISO_RESPALDO_MAESTRA_INCORRECTA
    is GestorRespaldo.Exportacion.BovedaCerrada -> Textos.AVISO_RESPALDO_BOVEDA_CERRADA
    is GestorRespaldo.Exportacion.Falla -> resultado.mensaje
}

fun mensajeDe(resultado: GestorRespaldo.Importacion): String = when (resultado) {
    is GestorRespaldo.Importacion.Ok -> buildString {
        append(Textos.avisoImportadas(resultado.agregadas))
        if (resultado.actualizadas > 0) append(" ").append(Textos.avisoActualizadas(resultado.actualizadas))
        if (resultado.omitidas > 0) append(" ").append(Textos.avisoOmitidas(resultado.omitidas))
    }
    is GestorRespaldo.Importacion.ContrasenaIncorrecta -> Textos.AVISO_IMPORTAR_CLAVE_INCORRECTA
    is GestorRespaldo.Importacion.NoEsUnRespaldo -> Textos.AVISO_IMPORTAR_NO_ES_RESPALDO
    is GestorRespaldo.Importacion.BovedaCerrada -> Textos.AVISO_RESPALDO_BOVEDA_CERRADA
    is GestorRespaldo.Importacion.Falla -> resultado.mensaje
}
