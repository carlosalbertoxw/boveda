package com.carlosalbertoxw.boveda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Que el texto de licencias llegue empaquetado y diga lo que tiene que decir.
 * Si el recurso se perdiera en un cambio de build, Ajustes → Licencias se
 * quedaria vacio sin que nada mas fallara.
 */
class LicenciasTest {

    @Test
    fun `el texto de licencias esta empaquetado y nombra lo que se redistribuye`() {
        val texto = Licencias.texto

        assertTrue(texto.contains("MIT License"))
        // SQLCipher pide reproducir su aviso en la distribucion binaria.
        assertTrue(texto.contains("Copyright (c) 2008-2023, ZETETIC LLC"))
        assertTrue(texto.contains("Apache License"))
        assertTrue(texto.contains("Bóveda"))
    }
}
