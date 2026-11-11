package com.carlosalbertoxw.boveda

/**
 * La licencia de Boveda y las de lo que usa, tal y como se muestran en
 * Ajustes → Licencias.
 *
 * El texto vive en un solo archivo de recursos del nucleo para que las dos apps
 * muestren lo mismo y no haya que mantener dos copias. Mostrarlo dentro de la
 * app es lo que cumple con licencias como la de SQLCipher, que piden reproducir
 * su aviso en los materiales que acompanan a la version compilada.
 */
object Licencias {

    private const val RECURSO = "/com/carlosalbertoxw/boveda/licencias-terceros.txt"

    val texto: String by lazy {
        checkNotNull(Licencias::class.java.getResourceAsStream(RECURSO)) { "falta $RECURSO" }
            .use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
