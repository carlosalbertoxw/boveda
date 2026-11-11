package com.carlosalbertoxw.boveda

/**
 * Todos los textos que ve el usuario viven aqui, y aqui si se escriben con
 * tildes. Es el `strings.xml` de la version de Android: mismas claves en
 * espiritu, mismos mensajes, con "telefono" cambiado por "equipo" donde toca.
 *
 * El codigo (comentarios e identificadores) va sin acentos por convencion del
 * proyecto; la interfaz no tiene por que heredar esa convencion.
 */
object Textos {
    const val NOMBRE_APP = "Bóveda"

    // --- Comunes ---------------------------------------------------------
    const val MOSTRAR = "Mostrar"
    const val OCULTAR = "Ocultar"
    const val COPIAR = "Copiar"
    fun copiarCampo(campo: String) = "Copiar $campo"
    const val VOLVER = "Volver"
    const val CANCELAR = "Cancelar"
    const val GUARDAR = "Guardar"
    const val ELIMINAR = "Eliminar"
    const val CONTRASENA_MAESTRA = "Contraseña maestra"

    // --- Medidor de fuerza ----------------------------------------------
    const val FUERZA_DEBIL = "Débil: se rompe en horas"
    const val FUERZA_ACEPTABLE = "Aceptable"
    const val FUERZA_FUERTE = "Fuerte"
    const val FUERZA_EXCELENTE = "Excelente"
    fun fuerzaBits(etiqueta: String, bits: Int) = "$etiqueta · ~$bits bits"

    // --- Generador --------------------------------------------------------
    const val GENERADOR_TITULO = "Generar contraseña"
    fun generadorLargo(largo: Int) = "Largo: $largo"
    const val GENERADOR_MAYUSCULAS = "Mayúsculas"
    const val GENERADOR_DIGITOS = "Dígitos"
    const val GENERADOR_SIMBOLOS = "Símbolos"
    const val GENERADOR_OTRA = "Generar otra"
    const val GENERADOR_USAR = "Usar"

    // --- Alta -------------------------------------------------------------
    const val ALTA_TITULO = "Crea tu contraseña maestra"
    const val ALTA_EXPLICACION =
        "Es la única llave de la bóveda. Todo lo que guardes se cifra con ella."
    const val ALTA_REPITE = "Repítela"
    fun altaMuyCorta(minimo: Int) = "Usa al menos $minimo caracteres."
    const val ALTA_NO_COINCIDEN = "Las dos contraseñas no coinciden."
    const val ALTA_SIN_RECUPERACION_TITULO = "No hay forma de recuperarla"
    const val ALTA_SIN_RECUPERACION =
        "Esta app no guarda tu contraseña maestra en ningún lado ni la manda a ningún " +
            "servidor: por eso nadie más puede leer tu bóveda. La otra cara de esa moneda " +
            "es que si la olvidas, los datos se pierden. Apúntala en un lugar físico seguro."
    const val ALTA_CREANDO = "Creando…"
    const val ALTA_CREAR = "Crear bóveda"

    // --- Desbloqueo -------------------------------------------------------
    const val DESBLOQUEO_TITULO = "Bóveda cerrada"
    fun desbloqueoEspera(segundos: Int) = "Espera $segundos s"
    const val DESBLOQUEO_ABRIENDO = "Abriendo…"
    const val DESBLOQUEO_ABRIR = "Abrir"
    const val DESBLOQUEO_EXPLICACION =
        "Descifrar la bóveda tarda un momento a propósito: cada intento cuesta el mismo " +
            "trabajo, y eso es lo que hace inviable probar contraseñas en serie."

    // --- Listado ----------------------------------------------------------
    const val LISTADO_BUSCAR = "Buscar"
    const val LISTADO_CERRAR_BOVEDA = "Cerrar bóveda (Ctrl+L)"
    const val LISTADO_AJUSTES = "Ajustes"
    const val LISTADO_NUEVA_ENTRADA = "Nueva entrada (Ctrl+N)"
    const val LISTADO_VACIO = "La bóveda está vacía"
    const val LISTADO_VACIO_AYUDA = "Pulsa + para guardar tu primera contraseña."
    fun listadoSinResultados(busqueda: String) = "Sin resultados para «$busqueda»"
    const val LISTADO_PAPELERA = "Papelera"

    // --- Papelera ---------------------------------------------------------
    const val PAPELERA_TITULO = "Papelera"
    const val PAPELERA_VACIA = "La papelera está vacía"
    fun papeleraVaciaAyuda(dias: Int) =
        "Lo que borres se queda aquí $dias días antes de desaparecer solo."
    const val PAPELERA_VENCE_YA = "Ya cumplió el plazo: desaparece al volver a abrir la bóveda."
    fun papeleraRestante(dias: Int) =
        if (dias == 1) "Se borra en 1 día" else "Se borra en $dias días"
    const val PAPELERA_RESTAURAR = "Restaurar"
    const val PAPELERA_BORRAR_AHORA = "Borrar ahora"
    const val PAPELERA_VACIAR = "Vaciar papelera"
    const val PAPELERA_VACIAR_TITULO = "¿Vaciar la papelera?"
    fun papeleraVaciarAviso(cuantas: Int) =
        "Se destruyen las $cuantas entradas que hay aquí, con todo su historial de " +
            "contraseñas. No se puede deshacer."
    fun papeleraBorrarTitulo(titulo: String) = "¿Borrar «$titulo» para siempre?"
    const val PAPELERA_BORRAR_AVISO =
        "Se destruye la entrada y todas las contraseñas que tuvo. No se puede deshacer."
    const val PAPELERA_EXPLICACION =
        "Estas entradas ya no salen en el listado, pero siguen dentro de la bóveda y " +
            "siguen cifradas. Se borran solas al cumplir el plazo."

    // --- Historial de contrasenas ----------------------------------------
    const val HISTORIAL_TITULO = "Historial de contraseñas"
    const val HISTORIAL_VACIO = "Esta entrada todavía no tiene historial."
    const val HISTORIAL_ACTUAL = "Actual"
    fun historialDesde(fecha: String) = "Desde el $fecha"
    const val HISTORIAL_USAR = "Usar esta"
    const val HISTORIAL_BORRAR = "Borrar del historial"
    const val HISTORIAL_USAR_TITULO = "¿Volver a esta contraseña?"
    const val HISTORIAL_USAR_AVISO =
        "La entrada pasará a usar esta contraseña y la marca de «actual» se moverá aquí. " +
            "La de ahora no se pierde: se queda en el historial."
    const val HISTORIAL_BORRAR_TITULO = "¿Borrar esta contraseña del historial?"
    const val HISTORIAL_BORRAR_AVISO =
        "Desaparece de la bóveda para siempre. La entrada y la contraseña que usa hoy no se tocan."
    const val HISTORIAL_EXPLICACION =
        "Una contraseña vieja no deja de servir el día que la cambias: sigue abriendo las " +
            "sesiones que quedaron iniciadas y los respaldos hechos con ella. Por eso se " +
            "guardan aquí, dentro de la bóveda, y por eso puedes borrar una por una las que " +
            "ya no te sirvan."

    // --- Detalle ----------------------------------------------------------
    const val DETALLE_USUARIO = "Usuario"
    const val DETALLE_CONTRASENA = "Contraseña"
    const val DETALLE_SITIO = "Sitio o app"
    const val DETALLE_NOTAS = "Notas"
    const val DETALLE_EDITAR = "Editar"
    fun detalleSeOculta(segundos: Int) = "Se ocultará sola en $segundos s."
    fun detalleActualizada(fecha: String) = "Actualizada el $fecha"
    fun detalleEliminarTitulo(titulo: String) = "¿Eliminar «$titulo»?"
    fun detalleEliminarAviso(dias: Int) =
        "Se va a la papelera. Ahí sigue $dias días por si te arrepientes, y después se borra sola."
    const val DETALLE_HISTORIAL = "Historial de contraseñas"

    // --- Editor -----------------------------------------------------------
    const val EDITOR_NUEVA = "Nueva entrada"
    const val EDITOR_EDITAR = "Editar"
    const val EDITOR_TITULO_CAMPO = "Título"
    const val EDITOR_TITULO_EJEMPLO = "Banco, correo, wifi de casa…"
    const val EDITOR_USUARIO = "Usuario o correo"
    const val EDITOR_CONTRASENA = "Contraseña"
    const val EDITOR_GENERAR = "Generar una segura"
    const val EDITOR_SITIO = "Sitio o app (opcional)"
    const val EDITOR_NOTAS = "Notas (opcional)"
    const val EDITOR_NOTA_PIE =
        "Todo lo que escribas aquí se guarda cifrado dentro de la bóveda y no sale del equipo."

    // --- Ajustes ----------------------------------------------------------
    const val AJUSTES_TITULO = "Ajustes"

    const val AJUSTES_CIERRE_TITULO = "Cierre automático"
    const val AJUSTES_CIERRE_EXPLICACION =
        "Cuánto aguanta abierta la bóveda sin que la uses, o fuera de la ventana. " +
            "«Al instante» la cierra en cuanto cambias a otra aplicación."
    const val AJUSTES_CIERRE_INSTANTE = "Al instante"
    const val AJUSTES_CIERRE_1 = "1 min"
    const val AJUSTES_CIERRE_5 = "5 min"
    const val AJUSTES_CIERRE_15 = "15 min"

    const val AJUSTES_PORTAPAPELES_TITULO = "Borrado del portapapeles"
    const val AJUSTES_PORTAPAPELES_EXPLICACION =
        "Tiempo antes de limpiar lo que copias. El portapapeles es compartido con todo el " +
            "sistema: cuanto menos dure ahí una contraseña, mejor."
    const val AJUSTES_PORTAPAPELES_NUNCA = "No borrar"
    const val AJUSTES_PORTAPAPELES_15 = "15 s"
    const val AJUSTES_PORTAPAPELES_30 = "30 s"
    const val AJUSTES_PORTAPAPELES_60 = "60 s"

    const val AJUSTES_BORRADO_TITULO = "Borrar tras intentos fallidos"
    const val AJUSTES_BORRADO_EXPLICACION =
        "Destruye la bóveda al llegar a ese número de contraseñas erróneas seguidas. " +
            "Protege ante un equipo robado; úsalo solo si tienes tus datos anotados en otro lado."
    const val AJUSTES_BORRADO_NUNCA = "Nunca"
    const val AJUSTES_BORRADO_10 = "10"
    const val AJUSTES_BORRADO_20 = "20"

    const val AJUSTES_CAMBIAR_MAESTRA = "Cambiar contraseña maestra"

    const val AJUSTES_RESPALDO_TITULO = "Respaldo"
    const val AJUSTES_RESPALDO_EXPLICACION =
        "Un archivo cifrado con su propia contraseña, para no perder la bóveda si pierdes el " +
            "equipo. Lleva la bóveda entera: lo que está en la papelera y el historial de " +
            "contraseñas de cada entrada. Es el mismo formato que la app de Android, así que " +
            "sirve para pasar la bóveda del teléfono a la computadora y al revés."
    const val AJUSTES_EXPORTAR = "Exportar respaldo cifrado"
    const val AJUSTES_IMPORTAR = "Importar respaldo"

    const val AJUSTES_BORRAR_TITULO = "Borrar la bóveda"
    const val AJUSTES_BORRAR_EXPLICACION =
        "Elimina todas las entradas y la contraseña maestra de este equipo. No hay copia en " +
            "ningún lado, así que no se puede deshacer."
    const val AJUSTES_BORRAR_BOTON = "Borrar todo"
    const val AJUSTES_LICENCIAS_TITULO = "Licencias"
    const val AJUSTES_LICENCIAS_EXPLICACION =
        "Bóveda es software libre con licencia MIT. Usa componentes de otros proyectos, cada " +
            "uno con su licencia."
    const val AJUSTES_LICENCIAS_VER = "Ver licencias"
    const val CERRAR = "Cerrar"

    const val AJUSTES_SIN_INTERNET =
        "Esta app no usa la red. No hay sincronización, ni copia en la nube, ni telemetría: " +
            "lo que guardas vive solo en este equipo."
    fun ajustesCarpetaDatos(ruta: String) = "Los archivos cifrados de la bóveda están en: $ruta"

    // --- Dialogo: cambiar maestra ----------------------------------------
    const val CAMBIO_ACTUAL = "Contraseña actual"
    const val CAMBIO_NUEVA = "Nueva contraseña"
    const val CAMBIO_REPITE = "Repite la nueva"
    const val CAMBIO_EXPLICACION =
        "Tus entradas no se vuelven a cifrar: lo que cambia es la llave que protege la " +
            "bóveda, así que el cambio es inmediato."
    const val CAMBIO_CONFIRMAR = "Cambiar"

    // --- Dialogo: exportar ------------------------------------------------
    const val EXPORTAR_TITULO = "Exportar respaldo"
    const val EXPORTAR_EXPLICACION_MAESTRA =
        "Sacar todas tus contraseñas de la bóveda es la operación más delicada de la app, " +
            "así que se pide la maestra otra vez."
    const val EXPORTAR_EXPLICACION_ARCHIVO =
        "El archivo lleva su propia contraseña. Puede ser la misma maestra, pero si algún día " +
            "la cambias, los respaldos viejos seguirán abriéndose con la de entonces."
    const val EXPORTAR_CLAVE_ARCHIVO = "Contraseña del archivo"
    const val EXPORTAR_REPITE_ARCHIVO = "Repite la del archivo"
    const val EXPORTAR_CONFIRMAR = "Exportar"
    fun exportarDestino(ruta: String) = "Se guardará en: $ruta"

    // --- Dialogo: importar ------------------------------------------------
    const val IMPORTAR_TITULO = "Importar respaldo"
    const val IMPORTAR_CLAVE = "Contraseña del archivo"
    const val IMPORTAR_REEMPLAZAR = "Reemplazar todo lo que tengo"
    const val IMPORTAR_EXPLICACION_REEMPLAZAR =
        "Se borran las entradas actuales —con su historial y su papelera— y quedan solo las del archivo."
    const val IMPORTAR_EXPLICACION_FUSIONAR =
        "Una entrada con el mismo título y usuario se considera la misma cuenta: no se duplica. " +
            "Si trae datos distintos, se queda la versión modificada más recientemente y la otra " +
            "contraseña pasa a su historial. Lo que borraste aquí sigue en la papelera."
    const val IMPORTAR_CONFIRMAR = "Importar"

    // --- Selector de archivos --------------------------------------------
    const val SELECTOR_EXPORTAR = "Guardar respaldo de Bóveda"
    const val SELECTOR_IMPORTAR = "Abrir respaldo de Bóveda"

    // --- Dialogo: borrado total ------------------------------------------
    const val BORRADO_PALABRA = "BORRAR"
    const val BORRADO_INSTRUCCION = "Se perderán todas las entradas. Escribe BORRAR para confirmar."

    // --- Avisos: apertura de la boveda -----------------------------------
    fun avisoIncorrectaEspera(segundos: Int) =
        "Contraseña incorrecta. Espera $segundos s antes de reintentar."
    fun avisoIncorrecta(intento: Int) = "Contraseña incorrecta. Intento $intento."
    fun avisoEnEspera(segundos: Int) = "Demasiados intentos. Espera $segundos s."
    const val AVISO_BORRADA = "Se alcanzó el límite de intentos: la bóveda se borró."
    const val AVISO_GENERICO = "Algo salió mal."
    const val AVISO_NO_SE_PUDO_ABRIR = "No se pudo abrir la bóveda."

    // --- Avisos: papelera e historial ------------------------------------
    fun avisoALaPapelera(dias: Int) = "En la papelera. Se borra sola en $dias días."
    fun avisoRestaurada(titulo: String) = "«$titulo» vuelve a la bóveda."
    fun avisoBorradaParaSiempre(titulo: String) = "«$titulo» se borró para siempre."
    fun avisoPapeleraVaciada(cuantas: Int) = "Papelera vacía: se borraron $cuantas entradas."
    const val AVISO_HISTORIAL_RESTAURADA = "Esa vuelve a ser la contraseña de la entrada."
    const val AVISO_HISTORIAL_BORRADA = "Contraseña borrada del historial."
    const val AVISO_HISTORIAL_ES_LA_ACTUAL =
        "Esa es la contraseña que usas hoy. Cámbiala antes de borrarla del historial."

    // --- Avisos: portapapeles --------------------------------------------
    const val AVISO_COPIADO = "Copiado."
    fun avisoCopiadoConBorrado(segundos: Int) = "Copiado. Se borra del portapapeles en $segundos s."

    // --- Avisos: maestra --------------------------------------------------
    const val AVISO_MAESTRA_ACTUALIZADA = "Contraseña maestra actualizada."
    const val AVISO_MAESTRA_INCORRECTA_ACTUAL = "La contraseña actual no es correcta."

    // --- Avisos: respaldo -------------------------------------------------
    fun avisoRespaldoCreado(entradas: Int) = "Respaldo creado con $entradas entradas."
    const val AVISO_RESPALDO_MAESTRA_INCORRECTA = "La contraseña maestra no es correcta."
    const val AVISO_RESPALDO_BOVEDA_CERRADA = "La bóveda se cerró antes de terminar."
    fun avisoImportadas(agregadas: Int) = "Importadas $agregadas entradas nuevas."
    fun avisoActualizadas(actualizadas: Int) =
        "Se actualizaron $actualizadas con la versión más reciente; las contraseñas anteriores quedan en su historial."
    fun avisoOmitidas(omitidas: Int) = "Se omitieron $omitidas que ya estaban."
    const val AVISO_IMPORTAR_CLAVE_INCORRECTA =
        "La contraseña del archivo no es correcta, o el archivo fue alterado."
    const val AVISO_IMPORTAR_NO_ES_RESPALDO = "Ese archivo no es un respaldo de Bóveda."

    // --- Errores de las capas de abajo -----------------------------------
    const val ERROR_NO_CONFIGURADA = "La bóveda no está configurada."
    const val ERROR_BASE_ILEGIBLE =
        "El archivo de la bóveda no se pudo descifrar: está dañado o no corresponde a esta contraseña."
    const val ERROR_BASE_CERRADA = "La bóveda está cerrada."
    fun errorEsquemaFuturo(esquema: Int, soportado: Int) =
        "La bóveda fue guardada por una versión más nueva de la app (esquema $esquema; esta lee hasta el $soportado)."
    const val ERROR_ESCRIBIR_ARCHIVO = "No se pudo escribir el archivo."
    const val ERROR_SIN_PERMISO_ESCRITURA = "Sin permiso para escribir ahí."
    const val ERROR_LEER_ARCHIVO = "No se pudo leer ese archivo."
    const val ERROR_SIN_PERMISO_LECTURA = "Sin permiso para leer ahí."
    const val ERROR_ARCHIVO_GRANDE = "El archivo es demasiado grande."
    fun errorVersionNoSoportada(version: Int, soportada: Int) =
        "El archivo es de la versión $version y esta app lee la $soportada."
    fun errorRespaldoDanado(detalle: String) = "El respaldo está dañado: $detalle."

    // --- Arranque ---------------------------------------------------------
    const val YA_ABIERTA =
        "Bóveda ya está abierta en otra ventana. Dos copias escribiendo la misma bóveda " +
            "a la vez podrían pisarse los cambios."
    fun errorCarpetaDatos(ruta: String, detalle: String) =
        "No se pudo preparar la carpeta de datos ($ruta): $detalle"
}
