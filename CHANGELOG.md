# Registro de cambios

Las dos apps comparten número de versión ([version.properties](version.properties)) y se
publican juntas con un tag `vX.Y.Z`, que genera el APK y los instaladores en el CI. La
numeración sigue [versionado semántico](https://semver.org/lang/es/): sube la mayor cuando un
cambio rompe algo que ya existía, por ejemplo un respaldo que deja de abrirse.

**Cualquier cambio en el formato del respaldo se anota aquí**, con qué versiones pueden leer
los archivos nuevos y cuáles los viejos.

## [1.0.0] — sin publicar

Primera versión publicada de las dos apps.

### Android y escritorio

- Gestor de contraseñas sin conexión: cifrado en dos niveles (la maestra desenvuelve una frase
  aleatoria con AES-256-GCM y esa frase cifra la base), freno exponencial a la fuerza bruta y
  borrado opcional tras N fallos.
- Papelera de 30 días e historial de contraseñas por entrada.
- Respaldo en un sobre JSON **versión 2** (lee también la 1), con la cabecera autenticada como
  AAD y su propia contraseña. Un respaldo exportado en una app se importa en la otra.
- Fusión al importar por título + usuario: gana la versión más reciente y ninguna contraseña
  se pierde.
- Ajustes → Licencias: la licencia MIT de Bóveda y las de los componentes de terceros.

### Seguridad

- PBKDF2-HMAC-SHA256 con **600 000 iteraciones**, lo que pide OWASP para SHA-256. Las bóvedas
  creadas con las 210 000 de las versiones de desarrollo se ponen al día solas al desbloquear,
  y los respaldos con 210 000 se siguen abriendo.
- Un respaldo que pida más de 10 millones de iteraciones se rechaza sin derivar nada: antes
  podía dejar la app calculando durante horas.
- Reiniciar el equipo a mitad de una espera la vuelve a empezar entera. Antes, reiniciar y
  cambiar la fecha anulaba el freno.
- Escritorio: el archivo temporal de la escritura atómica nace con permisos `600` en macOS y
  Linux, y el renombrado se fuerza al disco.

### Correcciones

- Android: importar un respaldo hacía caer la app en Android 8 a 12. La lectura del archivo usaba
  una función que solo existe desde Android 13.
- Android: crear la llave de la huella ya no depende de una clase que solo existe desde Android 9
  (lint lo marcaba como error con minSdk 26).

### Formato del respaldo

- Versión 2. La leen todas las versiones 1.x. Los archivos nuevos llevan 600 000 iteraciones en
  la cabecera; cualquier lector de la versión 2 los abre, porque toma las iteraciones de ahí.
