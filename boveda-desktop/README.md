# Bóveda para escritorio

Gestor de contraseñas para Windows, macOS y Linux, sin conexión. Es la versión de escritorio de
[Bóveda para Android](../boveda-android), que vive en el mismo repositorio: tiene las mismas
funciones, la misma interfaz y **el mismo formato de respaldo**. Un archivo exportado en el teléfono se importa aquí, y
al revés.

- **Kotlin + Compose Multiplatform (Material 3)**: el mismo lenguaje y el mismo modelo de interfaz
  que la app de Android. El cifrado, el formato del respaldo, la fusión y la papelera no se copian:
  las dos apps usan el mismo [núcleo compartido](../boveda-nucleo).

---

## Cómo se construye

Los comandos se lanzan desde esta carpeta (`boveda-desktop/`), que es una build de Gradle
independiente de la de Android.

Gradle corre sobre un **JDK 21**, que busca solo entre los instalados (sirve el JBR 21 de Android
Studio; ver [gradle-daemon-jvm.properties](gradle/gradle-daemon-jvm.properties)). El instalador lleva
su propio runtime, así que quien instala la app no necesita Java.

```bash
./gradlew run
```

Las pruebas de esta app y las del núcleo compartido (sin emulador ni dispositivo: aquí el ciclo
completo y las pantallas corren en la JVM), y el linter:

```bash
./gradlew test :boveda-nucleo:test detekt
```

Para probar sin tocar tu bóveda de verdad, apunta la app a otra carpeta:

```bash
JAVA_TOOL_OPTIONS="-Dboveda.datos=datos-prueba" ./gradlew run
```

### Instaladores

`jpackage` no compila de forma cruzada: cada instalador se genera **en su propio sistema**. Llevan
un runtime de Java recortado dentro, así que quien instala no necesita tener Java.

| Sistema | Comando | Sale en |
|---|---|---|
| Windows | `./gradlew packageMsi` o `packageExe` | `build/compose/binaries/main/msi/` |
| macOS | `./gradlew packageDmg` | `build/compose/binaries/main/dmg/` |
| Linux | `./gradlew packageDeb` o `packageRpm` | `build/compose/binaries/main/deb/` |

Con un tag `vX.Y.Z`, el CI genera los instaladores de los tres sistemas, cada uno en su propio runner, y
los deja como artefactos del run.

La primera vez en Windows se descarga WiX 3 automáticamente. El `upgradeUuid` del `.msi` está fijo
en `build.gradle.kts` y no debe cambiar nunca: es lo que permite que una versión nueva actualice la
instalación anterior en vez de duplicarla.

**Firma.** Igual que el keystore en Android, la firma no se versiona. Sin firmar, Windows muestra
SmartScreen al instalar y macOS exige abrir la app con clic derecho → *Abrir*. Para publicarla hay
que firmar el `.msi` con `signtool` y, en macOS, configurar `macOS { signing { ... } notarization { ... } }`
en `build.gradle.kts`.

Los íconos de los instaladores (`iconos/`) salen de `java herramientas/GeneraIconos.java`, que dibuja
el mismo escudo del ícono de Android.

---

## Cómo protege lo que guardas

El mismo esquema de dos niveles que en Android:

```
contraseña maestra ──PBKDF2──▶ llave de envoltura ──AES-GCM──▶ frase de la base ──▶ boveda.dat (AES-256-GCM)
```

La base **no** se cifra con tu contraseña, sino con una frase aleatoria de 256 bits. Tu contraseña
solo desenvuelve esa frase: cambiar la maestra es instantáneo y no recifra nada.

| Capa | Qué hace | Dónde |
|---|---|---|
| AES-256-GCM sobre el archivo entero | `boveda.dat` es ruido sin la frase; un byte alterado impide abrirlo | [BaseBoveda.kt](src/main/kotlin/com/carlosalbertoxw/boveda/datos/BaseBoveda.kt) |
| PBKDF2-HMAC-SHA256, 600 000 iteraciones | Cada intento de adivinar cuesta trabajo real | [Kdf.kt](../boveda-nucleo/src/main/kotlin/com/carlosalbertoxw/boveda/seguridad/Kdf.kt) |
| AES-256-GCM | Cifrado autenticado; la etiqueta es lo que verifica la contraseña | [Sobre.kt](../boveda-nucleo/src/main/kotlin/com/carlosalbertoxw/boveda/seguridad/Sobre.kt) |
| Freno exponencial + borrado opcional | 3 intentos gratis, luego 15 s → 5 min, cruzando dos relojes; reiniciar el equipo vuelve a empezar la espera | [GestorBoveda.kt](src/main/kotlin/com/carlosalbertoxw/boveda/seguridad/GestorBoveda.kt) |
| Escritura atómica | Temporal privado desde que nace + `fsync` + renombrado: un corte de luz no deja nada a medias | [ArchivoSeguro.kt](src/main/kotlin/com/carlosalbertoxw/boveda/datos/ArchivoSeguro.kt) |
| Una sola instancia | Dos copias abiertas no se pisan los cambios | [InstanciaUnica.kt](src/main/kotlin/com/carlosalbertoxw/boveda/escritorio/InstanciaUnica.kt) |

**La contraseña maestra no se guarda en ningún lado, ni siquiera como hash.** Las iteraciones se
guardan junto a la sal y se suben solas al desbloquear, igual que en Android.

### Dónde viven los datos

| Sistema | Carpeta |
|---|---|
| Windows | `%LOCALAPPDATA%\Boveda` (la local, no la itinerante: no viaja a servidores de dominio) |
| macOS | `~/Library/Application Support/Boveda` |
| Linux | `$XDG_DATA_HOME/boveda` o `~/.local/share/boveda`, con permisos `700`/`600` |

Dentro hay tres archivos: `boveda.dat` (la base cifrada), `boveda_config.properties` (sal,
iteraciones y la frase envuelta; ningún secreto en claro) y `boveda.lock`. La ruta también se muestra
en *Ajustes*.

### Qué cambió respecto a Android, y por qué

- **SQLCipher → un archivo AES-256-GCM.** No hay un SQLCipher para la JVM que funcione igual en los
  tres sistemas sin compilar código nativo. Una bóveda personal pesa kilobytes, así que el archivo
  entero es un solo sobre cifrado y autenticado que se reescribe de forma atómica en cada cambio. El
  DAO tiene la misma API que el de Room, con las mismas transacciones, y las pruebas de flujo que en
  Android eran instrumentadas aquí se portaron sin cambios de fondo.
- **Sin desbloqueo con huella.** En Android la llave vive en el chip seguro y el hardware se niega a
  usarla sin una huella válida. Desde la JVM no hay una API común a Windows Hello, Touch ID y Linux,
  y guardar la frase en el llavero del sistema sin esa atadura al hardware sería un atajo más débil
  que la maestra. Se prefirió no ofrecerlo.
- **`FLAG_SECURE` → `SetWindowDisplayAffinity` en Windows.** La ventana queda excluida de capturas,
  grabaciones, Recortes y compartir pantalla (verificado: afinidad `0x11`). En **macOS y Linux no hay
  protección contra capturas**: ver abajo.
- **"Segundo plano" = la ventana pierde el foco.** Cambiar a otra aplicación o minimizar cuenta como
  salir. Con *Al instante*, la bóveda se cierra en cuanto cambias de ventana; con 1/5/15 min, al
  volver se comprueba cuánto tiempo estuviste fuera. La inactividad cuenta teclas, clics y rueda.
- **El portapapeles no se limpia al perder el foco.** En Android ese gesto no tenía efecto (el sistema
  no deja tocar el portapapeles desde segundo plano); en escritorio sí lo tendría, y borraría la
  contraseña justo al cambiar al navegador para pegarla. Manda el temporizador (15/30/60 s), que sigue
  corriendo con la ventana detrás, y se limpia al bloquear y al salir. En Windows lo copiado se marca
  para quedar fuera del historial `Win+V` y de la sincronización en la nube.
- **SAF → el selector de archivos nativo** (`FileDialog`). La app solo toca el archivo que señalas.
- **Atajos de teclado:** `Ctrl+L` cierra la bóveda, `Ctrl+N` crea una entrada, `Ctrl+F` va al
  buscador, `Esc` vuelve atrás, y `Enter` en el buscador abre la primera coincidencia.
- **Autofill y *tapjacking*** no tienen equivalente en escritorio.

---

## Papelera, historial y respaldo

Funcionan exactamente igual que en Android; el razonamiento completo está en el
[README de esa app](../boveda-android/README.md#papelera).

- **Papelera:** borrar aparta la entrada 30 días, con cuenta atrás. El plazo se cumple al abrir la
  bóveda. La papelera no muestra contraseñas.
- **Historial:** cada entrada guarda las contraseñas que tuvo con una marca en la actual; se puede
  volver a una anterior o borrar las viejas una por una. Solo se descubre una a la vez.
- **Respaldo:** el mismo sobre JSON (versión 2, lee también la 1) con cabecera autenticada como AAD,
  su propia contraseña y su propia sal. Exportar pide la maestra otra vez. Importar ofrece fusionar
  o reemplazar todo.

### Conflictos al fusionar

Pasar respaldos entre el teléfono y la computadora después de cambiar una contraseña en uno de los dos
es el caso normal, no la excepción. La regla ([Fusion.kt](../boveda-nucleo/src/main/kotlin/com/carlosalbertoxw/boveda/datos/Fusion.kt))
es la misma en las dos apps porque las dos usan ese mismo código:

- **La cuenta es título + usuario**, sin distinguir mayúsculas ni espacios de sobra. El título solo no
  basta: dos «Correo» (personal y trabajo) se fusionarían y una contraseña pisaría a la otra.
- **Mismo contenido:** se omite. Que solo cambien las fechas no cuenta como conflicto.
- **Datos distintos:** título, usuario, contraseña, sitio y notas salen de la versión con
  `actualizadoEn` más reciente (si empatan, gana la local). **Ninguna contraseña se pierde:** la de la
  versión que no gana y las viejas que traiga el archivo entran al historial, con la marca de "actual"
  en la que ganó.
- **Papelera:** lo que borraste en este equipo sigue borrado aunque el archivo lo traiga activo.
- **Duplicados:** si el archivo trae la misma cuenta varias veces, se combinan entre sí primero; si la
  bóveda ya la tenía repetida, se combina con la que coincide en contraseña y las demás no se tocan.

El aviso al terminar cuenta las tres cosas: nuevas, actualizadas y omitidas.

### La compatibilidad con Android está probada, no supuesta

Las dos apps leen y escriben con el mismo `Respaldo.kt`, pero eso solo garantiza que coincidan entre
sí, no que sigan la especificación del formato. Por eso
[CompatibilidadFormatoTest](../boveda-nucleo/src/test/kotlin/com/carlosalbertoxw/boveda/CompatibilidadFormatoTest.kt)
compara contra una **implementación independiente en Node.js** escrita desde la especificación
([respaldo-referencia.mjs](../boveda-nucleo/herramientas/respaldo-referencia.mjs)):

- un respaldo escrito por Node, con contraseña con `ñ` y emoji, papelera e historial, se importa
  completo, aquí y en Android con su propio org.json;
- la derivación de llave coincide byte a byte con la de Node;
- y en la dirección inversa se comprobó a mano que Node descifra un respaldo escrito por esta app.

El detalle fino es la contraseña con caracteres fuera de ASCII: `Kdf.deriva` convierte a UTF-8 y
reexpande cada byte a un `char`, y tanto OpenJDK como el BouncyCastle de Android vuelven a codificar
esos `char` en UTF-8. En bytes, UTF-8 aplicado dos veces. Cualquier cambio en esa función rompería los
respaldos que ya existen en los teléfonos, y la prueba lo detecta.

---

## Lo que esto **no** cubre

- **Si olvidas la maestra, los datos se pierden.** No hay recuperación.
- **En macOS y Linux la ventana se puede capturar.** macOS tiene un mecanismo (`NSWindow.sharingType`),
  pero desde la JVM no hay una forma estable de llegar a la ventana nativa; en X11/Wayland no existe.
- **Un equipo con la sesión abierta y la bóveda desbloqueada es vulnerable.** Mientras está abierta, la
  frase vive en memoria del proceso, y cualquier programa con los permisos de tu usuario puede leerla.
  Por eso el cierre automático es corto.
- **La JVM no permite borrar contraseñas de los campos de texto**, porque `String` es inmutable. Fuera
  de la interfaz se usan `CharArray`/`ByteArray` y se sobreescriben con ceros.
- **El respaldo, una vez creado, deja de estar bajo control de la app.** Si lo guardas en una carpeta
  sincronizada, el archivo cifrado sube a la nube.
- **El freno a la fuerza bruta solo detiene a quien está frente al teclado.** Los contadores de
  intentos y la espera viven en `boveda_config.properties`, que cualquier programa con los permisos de
  tu usuario puede editar o copiar. Contra quien se lleva los archivos, la defensa es el coste de
  PBKDF2 por cada intento, y por eso conviene una maestra larga.
- **PBKDF2, no Argon2id**, por la misma razón que en Android y para no romper la compatibilidad.

---

## Estructura

```
src/main/kotlin/com/carlosalbertoxw/boveda/
├── Main.kt                  Ventana, foco, inactividad, protección contra capturas
├── AplicacionBoveda.kt      Estado del candado y reglas de cierre (el Application de Android)
├── Textos.kt                Todos los textos de la interfaz (el strings.xml)
├── seguridad/
│   ├── Reloj.kt             Los dos relojes, tras una interfaz
│   ├── GestorBoveda.kt      Abrir, cerrar, cambiar maestra, borrar, freno a la fuerza bruta
│   ├── PreferenciasBoveda.kt Sal, envoltura y ajustes, con escritura atómica
│   └── Portapapeles.kt      Copiado sensible con borrado automático
├── datos/
│   ├── BaseBoveda.kt        El archivo cifrado (en lugar de SQLCipher + Room)
│   ├── CredencialDao.kt     Misma API y mismas transacciones que el DAO de Room
│   ├── ArchivoSeguro.kt     Escritura atómica y permisos privados
│   └── GestorRespaldo.kt    Exportar e importar
├── escritorio/
│   ├── Rutas.kt             Carpeta de datos de cada sistema
│   ├── InstanciaUnica.kt    Una sola copia abierta por bóveda
│   ├── ProteccionPantalla.kt Exclusión de capturas en Windows (JNA)
│   └── SelectorArchivos.kt  Diálogo nativo para abrir y guardar
└── ui/
    ├── Modelo.kt            Lo que daba ViewModel: alcance, "ocupado" y avisos de error
    ├── ModeloBoveda.kt / ModeloPapelera.kt / ModeloRespaldo.kt
    ├── Avisos.kt / Mensajes.kt
    ├── RaizBoveda.kt        Navegación colgada del candado, atajos de teclado
    └── pantallas/           Alta, desbloqueo, listado, detalle, editor, historial, papelera, ajustes
```

El cifrado (`Sobre`, `Kdf`, `Generador`), el modelo (`Credencial`, `HistorialContrasena`), el
formato del respaldo (`Respaldo`), la fusión (`Fusion`) y la papelera (`Papelera`) están en el
[núcleo compartido](../boveda-nucleo), con sus pruebas.

## Pruebas

Las del núcleo (criptografía, formato del respaldo, fusión, historial, papelera y compatibilidad
con la implementación de referencia) corren con `./gradlew :boveda-nucleo:test`. Las de esta app:

| Suite | Qué cubre |
|---|---|
| `CandadoTest` | Freno a la fuerza bruta con reloj falso, reinicios, cambios de fecha, borrado tras N fallos |
| `PreferenciasBovedaTest` | Sal, iteraciones y sobre siempre juntos; que todo sobreviva a reabrir la app |
| `FlujoBovedaTest` | Ciclo completo con las iteraciones reales: crear, cerrar, reabrir, archivo ilegible, archivo alterado |
| `FlujoPapeleraTest` | Papelera e historial con transacciones reales, verificando lo escrito en disco |
| `FlujoRespaldoTest` | Exportar e importar contra la base real, incluido el respaldo de referencia |
| `PantallasTest` | Las protecciones de la interfaz: crear la bóveda solo con maestras que coinciden, la contraseña tapada que se vuelve a tapar sola, las licencias en Ajustes |
