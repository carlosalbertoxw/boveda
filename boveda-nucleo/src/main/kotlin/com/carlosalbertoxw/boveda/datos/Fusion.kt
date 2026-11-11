package com.carlosalbertoxw.boveda.datos

/**
 * Que entra, que se actualiza y que se omite al fusionar un respaldo con lo que
 * ya hay.
 *
 * Va aparte y sin tocar ningun almacen a proposito: es la unica regla del
 * respaldo con criterio propio —cuando dos entradas son "la misma" y cual de
 * las dos manda— y asi se puede probar sola. Vive en el nucleo porque las dos
 * apps tienen que aplicarla igual: un mismo archivo debe dar el mismo resultado
 * en el telefono y en la computadora.
 */
object Fusion {

    /**
     * [actualizadas] llevan el id de la entrada local que reemplazan, con la
     * credencial ya combinada y su historial completo.
     */
    data class Plan(
        val nuevas: List<Respaldo.Entrada>,
        val actualizadas: List<Respaldo.Entrada>,
        val omitidas: Int
    )

    /**
     * Dos entradas son la misma cuenta si coinciden **titulo y usuario**, sin
     * distinguir mayusculas ni espacios de sobra.
     *
     * No basta el titulo solo: nada impide tener dos "Correo", el personal y el
     * del trabajo, y fusionarlos pisaria la contrasena de una cuenta con la de
     * otra. Y no se exige que coincida la contrasena, que era la regla anterior:
     * cambiarla en el telefono e importar en la computadora dejaba dos "Banco"
     * lado a lado sin forma de saber cual valia.
     *
     * Cuando la misma cuenta llega con datos distintos, [combina] decide. Los
     * duplicados dentro del propio archivo se combinan entre si primero y
     * cuentan como omitidos, asi que todo lo entrante queda contado:
     * `entrantes.size == nuevas + actualizadas + omitidas`.
     */
    fun planifica(existentes: List<Respaldo.Entrada>, entrantes: List<Respaldo.Entrada>): Plan {
        var omitidas = 0
        val agrupadas = LinkedHashMap<Pair<String, String>, Respaldo.Entrada>()
        entrantes.forEach { entrada ->
            val clave = clave(entrada.credencial)
            val previa = agrupadas[clave]
            if (previa == null) {
                agrupadas[clave] = entrada
            } else {
                agrupadas[clave] = combina(previa, entrada) ?: previa
                omitidas++
            }
        }

        val porClave = existentes.groupBy { clave(it.credencial) }
        val nuevas = mutableListOf<Respaldo.Entrada>()
        val actualizadas = mutableListOf<Respaldo.Entrada>()
        agrupadas.forEach { (clave, entrante) ->
            val candidatas = porClave[clave]
            if (candidatas.isNullOrEmpty()) {
                nuevas += entrante
                return@forEach
            }
            val combinada = combina(eligeLocal(candidatas, entrante), entrante)
            if (combinada == null) omitidas++ else actualizadas += combinada
        }
        return Plan(nuevas, actualizadas, omitidas)
    }

    /**
     * La misma cuenta, con lo que traen las dos versiones.
     *
     *  - Titulo, usuario, contrasena, sitio y notas salen de la version con
     *    `actualizadoEn` mas reciente. Si empatan, se queda la local.
     *  - **Ninguna contrasena se pierde:** la de la version que no gana y todo
     *    el historial del archivo que esta boveda no tenga entran al historial,
     *    con la marca de "actual" en la que gano.
     *  - El id y la papelera son siempre los de la local: lo que se borro aqui
     *    sigue borrado, y lo activo no se manda a la papelera porque el otro
     *    equipo lo borrara.
     *
     * Devuelve null si no hay nada que cambiar: mismo contenido y ninguna
     * contrasena nueva. Las fechas solas no cuentan como cambio, o importar dos
     * veces el mismo archivo reescribiria la boveda entera.
     */
    fun combina(local: Respaldo.Entrada, entrante: Respaldo.Entrada): Respaldo.Entrada? {
        val base = local.credencial
        val otra = entrante.credencial
        val ganaLaOtra = otra.actualizadoEn > base.actualizadoEn
        val ganadora = if (ganaLaOtra) otra else base
        val perdedora = if (ganaLaOtra) base else otra

        val conocidas = local.historial.mapTo(HashSet()) { it.contrasena }
        val historial = local.historial.toMutableList()
        entrante.historial.forEach { anotacion ->
            if (conocidas.add(anotacion.contrasena)) {
                historial += anotacion.copy(id = 0, credencialId = base.id, actual = false)
            }
        }
        if (conocidas.add(perdedora.contrasena)) {
            historial += HistorialContrasena(
                credencialId = base.id,
                contrasena = perdedora.contrasena,
                creadoEn = perdedora.actualizadoEn,
                actual = false
            )
        }

        val mismoContenido = base.titulo == ganadora.titulo &&
            base.usuario == ganadora.usuario &&
            base.contrasena == ganadora.contrasena &&
            base.sitio == ganadora.sitio &&
            base.notas == ganadora.notas
        if (mismoContenido && historial.size == local.historial.size) return null

        val credencial = base.copy(
            titulo = ganadora.titulo,
            usuario = ganadora.usuario,
            contrasena = ganadora.contrasena,
            sitio = ganadora.sitio,
            notas = ganadora.notas,
            actualizadoEn = maxOf(base.actualizadoEn, otra.actualizadoEn)
        )
        // Normalizar deja una sola marca, en la contrasena que gano.
        return Respaldo.normaliza(credencial, historial)
    }

    /**
     * Si esta boveda ya tiene la cuenta repetida (de antes de esta regla), se
     * combina con la que mas se le parece y las demas no se tocan.
     */
    private fun eligeLocal(
        candidatas: List<Respaldo.Entrada>,
        entrante: Respaldo.Entrada
    ): Respaldo.Entrada {
        val contrasena = entrante.credencial.contrasena
        return candidatas.firstOrNull { it.credencial.contrasena == contrasena }
            ?: candidatas.firstOrNull { local ->
                local.historial.any { it.contrasena == contrasena }
            }
            ?: candidatas.maxBy { it.credencial.actualizadoEn }
    }

    private fun clave(credencial: Credencial) =
        normaliza(credencial.titulo) to normaliza(credencial.usuario)

    private val ESPACIOS = Regex("\\s+")

    private fun normaliza(texto: String) = texto.trim().replace(ESPACIOS, " ").lowercase()
}
