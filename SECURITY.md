# Seguridad

Bóveda guarda contraseñas, así que un fallo de seguridad importa más que cualquier otro.

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Usa el reporte privado de GitHub: pestaña *Security* →
*Report a vulnerability* en este repositorio. Solo lo ve el mantenedor.

Ayuda mucho incluir:

- qué app y qué versión (Android o escritorio, y el sistema operativo);
- qué se puede conseguir (leer la bóveda, saltarse el freno, alterar un respaldo sin que se note…);
- los pasos para reproducirlo, o un archivo de respaldo de prueba que lo demuestre
  (**nunca** uno con contraseñas reales).

Recibirás respuesta en un plazo razonable. Una vez corregido, el fallo se describe en el
[CHANGELOG](CHANGELOG.md) junto con la versión que lo arregla.

## Versiones que reciben correcciones

Solo la última versión publicada de cada app. Las correcciones de seguridad no se portan a
versiones anteriores: la solución es actualizar.

## Qué entra y qué no

El modelo de amenazas y sus límites están escritos en los README de cada app, en la sección
**«Lo que esto no cubre»**. Lo que aparece ahí (por ejemplo, un equipo con la bóveda abierta
y un programa malicioso con los permisos del usuario, o la falta de protección contra capturas
en macOS y Linux) es una limitación conocida, no una vulnerabilidad.

Sí interesan especialmente:

- cualquier forma de leer el contenido de la bóveda o de un respaldo sin su contraseña;
- respaldos alterados que se importen sin que el descifrado falle;
- incompatibilidades del formato del respaldo entre Android y escritorio;
- dependencias con vulnerabilidades conocidas que afecten a la app.
