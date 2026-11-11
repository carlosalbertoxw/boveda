# Bóveda

Gestor de contraseñas para Android, sin conexión y sin comunicación con otras apps.
Guardas una credencial, y cuando no recuerdas una entras, la ves o la copias. Tiene una
[versión de escritorio](../boveda-desktop) en el mismo repositorio, con el mismo formato de
respaldo; el código que comparten (criptografía, formato del respaldo, fusión y papelera) vive en
[`boveda-nucleo/`](../boveda-nucleo).

- **Paquete:** `com.carlosalbertoxw.boveda`
- **minSdk 26 · targetSdk 36 · compileSdk 37 · Kotlin + Jetpack Compose**

---

## Cómo se construye

Los comandos se lanzan desde esta carpeta (`boveda-android/`), que es una build de Gradle
independiente de la de escritorio; en Android Studio se abre esta carpeta, no la raíz del
repositorio.

Requiere un **JDK 17 o posterior**; sirve el que trae Android Studio. Desde la terminal:

```bash
./gradlew :app:assembleDebug
```

Las pruebas de la JVM, sin dispositivo (las del núcleo compartido se lanzan aparte, con
`./gradlew :boveda-nucleo:test`):

```bash
./gradlew :app:testDebugUnitTest
```

Las instrumentadas recorren el ciclo completo contra SQLCipher de verdad —incluida la
migración del esquema, que es lo único capaz de perder datos en silencio—, así que piden un
emulador o un teléfono conectado:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Los analizadores: detekt revisa el estilo y la complejidad del Kotlin, y lint de Android lo
propio de la plataforma, como usar una API que no existe en las versiones que la app soporta
(minSdk 26). Los dos corren en el CI y lo hacen fallar:

```bash
./gradlew :app:detekt :app:lintDebug
```

La configuración de detekt es la misma para todo el repositorio: [config/detekt.yml](../config/detekt.yml).

### Firmar el release

La firma se configura una sola vez y con dos piezas, ninguna de las cuales se versiona.

**1. Crear el keystore** (una vez en la vida de la app: si se pierde, no se puede volver a
publicar una actualización sobre la misma instalación). `keytool` pregunta las contraseñas
de forma interactiva, así que no quedan en el historial de la terminal:

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v -keystore boveda-release.jks -alias boveda -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

**2. Crear `keystore.properties`** en `boveda-android/` (no en la raíz del repositorio), copiando `keystore.properties.template` y
rellenando las contraseñas:

```
storeFile=boveda-release.jks
storePassword=...
keyAlias=boveda
keyPassword=...
```

Con eso, `assembleRelease` firma el APK (v2 + v3; v1 no hace falta con `minSdk 26`):

```bash
./gradlew :app:assembleRelease
```

El APK sale en `app/build/outputs/apk/release/app-release.apk`. Sin
`keystore.properties` la build no falla: el release simplemente queda sin firmar
(`app-release-unsigned.apk`), para que clonar y compilar no exija tener el keystore.

Guarda el `.jks` y sus contraseñas fuera del proyecto y con respaldo — pierdes uno de los
dos y pierdes la capacidad de actualizar la app.

---

## Cómo protege lo que guardas

El esquema es de dos niveles, como en los gestores serios:

```
contraseña maestra ──PBKDF2──▶ llave de envoltura ──AES-GCM──▶ frase de la base ──▶ SQLCipher
huella ──Keystore/TEE───────▶ llave de envoltura ──AES-GCM──▶ frase de la base ──▶ SQLCipher
```

La base **no** se cifra con tu contraseña, sino con una frase larga y aleatoria. Tu
contraseña solo desenvuelve esa frase. De ahí salen dos propiedades prácticas: cambiar la
maestra es instantáneo (no hay que recifrar nada), y la huella y la contraseña son dos
caminos independientes a la misma frase.

| Capa | Qué hace | Dónde |
|---|---|---|
| SQLCipher (AES-256) | El archivo `.db` es ruido sin la frase | [BovedaDatabase.kt](app/src/main/java/com/carlosalbertoxw/boveda/datos/BovedaDatabase.kt) |
| PBKDF2-HMAC-SHA256, 600 000 iteraciones | Cada intento de adivinar cuesta trabajo real | [Kdf.kt](../boveda-nucleo/src/main/kotlin/com/carlosalbertoxw/boveda/seguridad/Kdf.kt) |
| AES-256-GCM | Cifrado autenticado; la etiqueta es lo que verifica la contraseña | [Sobre.kt](../boveda-nucleo/src/main/kotlin/com/carlosalbertoxw/boveda/seguridad/Sobre.kt) |
| Keystore con `setUserAuthenticationRequired` | El chip se niega a descifrar sin huella válida | [AlmacenBiometrico.kt](app/src/main/java/com/carlosalbertoxw/boveda/seguridad/AlmacenBiometrico.kt) |
| Freno exponencial + borrado opcional | 3 intentos gratis, luego 15 s → 5 min | [GestorBoveda.kt](app/src/main/java/com/carlosalbertoxw/boveda/seguridad/GestorBoveda.kt) |
| Migraciones explícitas, nunca destructivas | `fallbackToDestructiveMigration` borraría la única copia que existe | [BovedaDatabase.kt](app/src/main/java/com/carlosalbertoxw/boveda/datos/BovedaDatabase.kt) |

**La espera se mide con los dos relojes del sistema y se cruzan al leerla.** El monotónico no
se deja mover cambiando la fecha del teléfono, pero se reinicia al apagarlo; el de pared
sobrevive al apagón, pero se puede mover a mano. Guardando ambos, adelantar la fecha no
saltea el castigo y atrasarla no lo alarga más allá del castigo más largo posible.

**Reiniciar a mitad de una espera la vuelve a empezar.** Tras un reinicio solo queda el reloj
de pared, y no hay forma de distinguir el tiempo que de verdad pasó de un cambio de fecha: si
se confiara en él, reiniciar y adelantar la fecha anularía el freno. Así que el castigo se arma
otra vez entero, como mucho cinco minutos. Todo está probado en
[CandadoTest](app/src/test/java/com/carlosalbertoxw/boveda/CandadoTest.kt).

**Las iteraciones se ponen al día solas.** Se guardan junto a la sal, y al desbloquear con la
maestra —el único momento en que está en memoria— se vuelve a envolver la frase si el número
subió desde que se creó la bóveda. Sin eso, el parámetro se quedaba congelado para siempre.

**La contraseña maestra no se guarda en ningún lado, ni siquiera como hash.** Lo que
verifica que acertaste es que la etiqueta GCM cuadre al desenvolver. Un hash guardado sería
una pista extra para quien se lleve el archivo.

### Aislamiento

- **Sin permiso de INTERNET.** No está declarado, y además se borra explícitamente con
  `tools:node="remove"` por si alguna dependencia futura lo colara en su manifiesto. Sin
  ese permiso, el sistema operativo bloquea cualquier socket: ni un bug ni una librería
  maliciosa pueden sacar nada del teléfono.
- **Un solo componente expuesto:** el icono del lanzador. Se quitaron el servicio de
  invalidación de Room y el receiver de `profileinstaller`, que las librerías declaran por
  su cuenta y este último venía exportado.
- **Sin respaldo:** `allowBackup=false` y reglas de extracción que excluyen todo. La base
  no viaja a Google Drive ni sale por `adb backup`.
- **Sin `intent-filter` de VIEW ni SEND.** Ninguna otra app puede lanzarla con datos.
- No se ofrece abrir el sitio guardado en el navegador: sería justo lo que esta app no
  hace, y filtraría en qué sitios tienes cuenta.

### En pantalla

- `FLAG_SECURE`: bloquea capturas, grabación de pantalla y la miniatura de "apps recientes".
- `filterTouchesWhenObscured`: defensa contra *tapjacking*, que otra app te tape la pantalla
  y te haga pulsar "mostrar contraseña" creyendo que pulsas otra cosa.
- Autofill desactivado en todo el árbol: otro gestor instalado no puede pedir guardar lo que
  escribes aquí.
- **Los diálogos llevan las mismas defensas.** Un diálogo de Compose se pinta en su propia
  ventana y no hereda ni el anti-*tapjacking* ni la exclusión de autofill de la Activity, así
  que se las aplica una por una `DialogoSeguro`. Es donde se teclea la maestra al exportar y
  donde se confirma el borrado, o sea justo donde más falta hacen.
- La contraseña nunca aparece en el listado ni en la papelera. Revelarla se vuelve a tapar
  sola a los 20 s.
- **En el historial solo se puede descubrir una a la vez**: descubrir la siguiente tapa la
  anterior. Es la pantalla con más secretos por centímetro de la app, y verlos todos juntos
  no le sirve a nadie salvo a quien mire de reojo.
- Cierre automático al pasar a segundo plano y por inactividad (configurable).

### Portapapeles

Es el punto más débil de cualquier gestor: es el único momento en que el secreto sale de la
app, y en Android es un buzón compartido. Se acota marcándolo como sensible (Android 13+ no
lo muestra en la vista previa), borrándolo solo a los 30 s y comprobando antes que siga
siendo nuestro para no pisarle al usuario algo que copió después.

De lo copiado la app guarda su SHA-256, no el texto: para responder a "¿esto sigue siendo lo
nuestro?" comparar huellas vale igual, y así no queda un segundo ejemplar de la contraseña
vivo en memoria —que además, al ser un `String`, no se podría poner en ceros—.

---

## Papelera

**Borrar no borra: aparta.** La entrada sale del listado y se queda en la papelera 30 días,
con la cuenta atrás a la vista, hasta que venza el plazo o se destruya a mano con *Borrar
ahora*. También se puede vaciar entera de golpe. Los dos caminos que sí destruyen piden
confirmación.

Es un borrado lógico —una columna `eliminadoEn` en la misma fila— y no una tabla aparte: una
contraseña borrada por error sigue siendo una contraseña, así que merece el mismo cifrado, el
mismo respaldo y el mismo historial que las demás. Moverla de tabla obligaría a duplicar todo
eso justo para el caso en el que más duele equivocarse.

**El plazo se cumple al abrir la bóveda**, que es el único momento en que la app tiene con
qué descifrar. No hay ni puede haber una tarea programada que lo haga de madrugada: sin
frase no hay base que purgar. Si nadie abre la app en meses, lo vencido sobrevive dentro del
mismo archivo cifrado y se destruye en cuanto alguien vuelve a mirar.

La papelera no muestra contraseñas, ni tapadas con puntos, ni deja copiarlas ni abrir el
detalle: sirve para reconocer qué se borró y decidir si vuelve. Quien necesite la contraseña
restaura primero.

## Historial de contraseñas

Cada entrada guarda las contraseñas que tuvo, con una marca en la que usa hoy. Desde el
detalle → *Historial de contraseñas* se puede **mover esa marca a una anterior** (la entrada
vuelve a usarla, sin crear una fila nueva: la marca se desplaza) y **borrar del historial una
por una, para siempre**.

**Por qué un gestor guarda contraseñas viejas.** Porque no se vuelven inofensivas el día que
las cambias: siguen abriendo las sesiones que quedaron iniciadas, los respaldos hechos con
ellas y las cuentas donde el cambio no llegó a aplicarse. Tenerlas dentro del archivo cifrado
es lo que permite recuperar una cuenta cuando el cambio se hizo a medias. Y por eso mismo se
pueden podar: si una ya no le sirve a nadie, lo mejor que se puede hacer con ella es que deje
de existir.

**La marca no puede discrepar de la entrada.** La verdad sobre qué contraseña usa la cuenta
la tiene `Credencial.contrasena`; la marca dice cuál de las filas del historial le
corresponde. Las dos se mueven dentro de la misma transacción del DAO, así que el estado
descuadrado no existe ni un instante, ni aunque la bóveda se cierre a mitad. La marcada como
actual no se puede borrar —la condición vive en el SQL, no en la pantalla—: dejaría a la
entrada sin la fila que le corresponde.

Toda entrada estrena historial al crearse, no al primer cambio. Si empezara al cambiar, la
contraseña original —justo la que uno quiere recuperar— sería la única que nunca estuvo ahí.
La migración de la base hace lo mismo con lo que ya estaba guardado.

---

## Respaldo

Un archivo `.json` con la cabecera legible y el contenido cifrado en AES-256-GCM, con su
propia contraseña y su propia sal:

```json
{
  "formato": "boveda-respaldo",
  "version": 2,
  "creado": "2026-08-15T18:00:00Z",
  "entradas": 12,
  "kdf": { "algoritmo": "PBKDF2WithHmacSHA256", "iteraciones": 600000, "sal": "..." },
  "cifrado": "AES-256-GCM",
  "datos": "base64( iv || texto cifrado || etiqueta )"
}
```

**Por qué no un ZIP con contraseña.** El cifrado clásico de ZIP (ZipCrypto) está roto: se
descifra con un ataque de texto conocido en segundos. El AES real de ZIP necesita una
librería de terceros como zip4j, que ampliaría la superficie de confianza justo en la parte
más delicada, y aun así filtra nombres y tamaños de archivo y no autentica el contenido
(no detecta manipulaciones). Un solo sobre JSON con AES-GCM no añade dependencias, no
filtra nada y sí detecta cualquier alteración.

**Por qué la cabecera va en claro.** Dentro de tres años, abriendo el archivo con un editor
de texto, se sabe qué es y con qué se descifra. No revela títulos ni longitudes de
contraseñas; lo único que expone es cuántas entradas hay. Además va **autenticada como
AAD**: si alguien edita `iteraciones` a 1 para abaratarse un ataque por diccionario, el
descifrado falla. Hay una prueba que lo verifica.

**Qué lleva dentro (versión 2).** La bóveda entera: las entradas activas, las que están en la
papelera con su fecha de borrado, y el historial de contraseñas de cada una. Un respaldo tiene
que devolver la bóveda tal y como estaba, y "estaba" incluye lo que se borró anteayer y
todavía se puede recuperar. Si el archivo no llevara la fecha de borrado, restaurarlo
convertiría la papelera en entradas activas y desharía un borrado que el usuario sí quiso.

**Los archivos de la versión 1 se siguen leyendo.** Son los que ya existen en los teléfonos de
la gente, y un respaldo que deja de abrirse por actualizar la app es exactamente el fallo que
un respaldo no puede tener. Al leerlos, cada entrada estrena historial con la única contraseña
que trae el archivo y entra fuera de la papelera. La versión va dentro del AAD, así que
tampoco se puede hacer pasar un archivo por uno de otra versión para que se lea con reglas
distintas.

**Un archivo que pida más de 10 millones de iteraciones se rechaza** antes de derivar nada: la
derivación ocurre antes de poder comprobar la cabecera, y un archivo hecho a propósito podría
dejar la app calculando durante horas.

**Lo que viene de fuera se normaliza antes de entrar.** Un archivo es texto que alguien pudo
editar a mano: puede traer dos marcas de "actual", ninguna, o la marca puesta en una
contraseña que la credencial ya no usa. `Respaldo.normaliza` deja exactamente una marca y la
hace coincidir con la contraseña de la entrada, sin descartar nada de lo que el archivo traiga.

**Cómo se usa.** Ajustes → Respaldo. Exportar pide la maestra otra vez (sacar todas las
contraseñas de golpe es la operación más peligrosa de la app) más una contraseña para el
archivo. Importar ofrece fusionar o reemplazar todo. Al fusionar se compara contra la bóveda
entera, papelera incluida: si solo contara lo activo, importar dejaría una copia nueva al lado
de la que acabas de borrar y el borrado se desharía solo. Lo que sigue en la papelera se
recupera desde ahí, que es donde lo dejaste.

**Conflictos al fusionar.** Pasar respaldos entre el teléfono y la computadora después de cambiar
una contraseña en uno de los dos es el caso normal. La regla es idéntica en la [app de escritorio](../boveda-desktop/README.md),
porque las dos usan el mismo [Fusion.kt](../boveda-nucleo/src/main/kotlin/com/carlosalbertoxw/boveda/datos/Fusion.kt):

- **La cuenta es título + usuario**, sin distinguir mayúsculas ni espacios de sobra. El título solo
  no basta: dos «Correo» (personal y trabajo) se fusionarían y una contraseña pisaría a la otra.
- **Mismo contenido:** se omite, así importar dos veces no duplica ni reescribe nada. Que solo
  cambien las fechas no cuenta como conflicto.
- **Datos distintos:** título, usuario, contraseña, sitio y notas salen de la versión con
  `actualizadoEn` más reciente (si empatan, gana la local). **Ninguna contraseña se pierde:** la de
  la versión que no gana y las viejas que traiga el archivo entran al historial, con la marca de
  "actual" en la que ganó.
- **Papelera:** lo que borraste aquí sigue borrado aunque el archivo lo traiga activo.
- **Duplicados:** la misma cuenta repetida en el archivo se combina consigo misma primero; si la
  bóveda ya la tenía repetida, se combina con la que coincide en contraseña y las demás no se tocan.

**El acceso al disco va por el selector del sistema (SAF).** La app no pide permisos de
almacenamiento, no ve el disco y no elige dónde escribir: recibe un `Uri` concreto que
señalaste con el dedo y ahí acaba su acceso. El manifiesto final sigue teniendo un solo
componente expuesto y ningún permiso de almacenamiento.

Como abrir el selector manda la app a segundo plano, el cierre automático concede una
excepción acotada a 3 minutos; si abandonas el selector y no vuelves, caduca sola.

---

## Lo que esto **no** cubre

Vale más decirlo que fingir que no existe:

- **Si olvidas la maestra, los datos se pierden.** No hay recuperación, y eso es exactamente
  lo que hace que nadie más pueda leer tu bóveda. Apúntala en un lugar físico seguro.
- **El respaldo, una vez creado, deja de estar bajo control de la app.** Si lo dejas en la
  carpeta de descargas y esa carpeta se sincroniza a la nube, el archivo cifrado sube. Sigue
  siendo ilegible sin su contraseña, pero conviene saberlo y elegir dónde guardarlo.
- **Si olvidas la contraseña del respaldo, ese archivo es irrecuperable**, igual que la
  bóveda con la maestra.
- **Un teléfono con root y la bóveda abierta es vulnerable.** Mientras está desbloqueada, la
  frase vive en memoria. Ninguna app puede evitarlo; por eso el cierre automático es corto.
- **Compose no permite borrar contraseñas de la memoria de los campos de texto**, porque
  `String` es inmutable. Fuera de la interfaz sí se usan `CharArray`/`ByteArray` y se
  sobreescriben con ceros al terminar.
- **PBKDF2, no Argon2id.** Argon2id resistiría mejor a un atacante con GPU, pero exige una
  librería nativa de terceros. Se prefirió no ampliar la superficie de confianza. El número
  de iteraciones se guarda junto a la sal y se sube solo al desbloquear, así que las bóvedas
  viejas se ponen al día sin quedar inservibles.

---

## Estructura

```
app/src/main/java/com/carlosalbertoxw/boveda/
├── AplicacionBoveda.kt      Estado del candado, colgado del proceso (no de la Activity)
├── MainActivity.kt          FLAG_SECURE, anti-tapjacking, inactividad
├── seguridad/
│   ├── Reloj.kt             Los dos relojes, tras una interfaz (y por eso probables)
│   ├── AlmacenBiometrico.kt Llave del Keystore ligada a la huella
│   ├── Biometria.kt         BiometricPrompt, siempre con CryptoObject
│   ├── GestorBoveda.kt      Abrir, cerrar, cambiar maestra, borrar, freno a la fuerza bruta
│   ├── PreferenciasBoveda.kt Sal, envolturas y ajustes
│   └── Portapapeles.kt      Copiado sensible con borrado automático
├── datos/
│   ├── CredencialFila.kt    La tabla `credenciales` tal y como la ve Room
│   ├── HistorialFila.kt     La tabla `historial_contrasenas`
│   ├── BovedaDatabase.kt    SQLCipher + migraciones explícitas, nunca destructivas
│   ├── CredencialDao.kt     Listados y las transacciones que mueven las dos tablas juntas
│   └── GestorRespaldo.kt    Exportar/importar por SAF
└── ui/
    ├── ModeloBoveda.kt      Único camino de la interfaz hacia la bóveda
    ├── ModeloRespaldo.kt    Exportar e importar, con su propio estado
    ├── ModeloPapelera.kt    Restaurar, borrar de verdad y vaciar
    ├── Avisos.kt            Buzón único de mensajes para la barra de abajo
    ├── Mensajes.kt          Cómo se le cuenta al usuario lo que devolvió cada capa
    └── pantallas/           Compose: alta, desbloqueo, listado, detalle, editor,
                             papelera, historial y ajustes
```

El cifrado (`Sobre`, `Kdf`, `Generador`), el modelo (`Credencial`, `HistorialContrasena`), el
formato del respaldo (`Respaldo`), la fusión (`Fusion`) y la papelera (`Papelera`) están en el
[núcleo compartido](../boveda-nucleo), con sus pruebas. Room devuelve directamente los modelos
del núcleo; las clases `*Fila` solo describen las tablas y sirven para insertar y actualizar.

La interfaz no toca preferencias ni el Keystore: todo lo que cruza esa línea pasa por un
método con nombre de `ModeloBoveda`, y los ajustes salen como estado observable.

## Pruebas

Las del núcleo (criptografía, formato del respaldo, fusión, historial, papelera, compatibilidad
con la implementación de referencia) corren con `./gradlew :boveda-nucleo:test`. Las de esta app:

| Suite | Qué cubre |
|---|---|
| `CandadoTest` | El freno a la fuerza bruta con un reloj falso: los tres intentos gratis, el crecimiento del castigo, el tope, que cambiar la fecha no lo altere y que reiniciar lo vuelva a empezar |
| `PreferenciasBovedaTest` | Que sal, iteraciones y sobre viajen siempre juntos, que es lo que separa una bóveda legible de una perdida |
| `RespaldoAndroidTest` | El formato del respaldo con el org.json de Android, incluido el respaldo de referencia escrito con Node |
| `FlujoBovedaTest` *(instrumentada)* | El ciclo completo contra SQLCipher real, incluido que el `.db` no contenga nada legible |
| `FlujoRespaldoTest` *(instrumentada)* | Exportar y recuperar tras vaciar la bóveda, que la papelera y el historial vuelvan enteros, y que exportar exija la maestra |
| `FlujoPapeleraTest` *(instrumentada)* | Borrar, restaurar, purgar por plazo y mover la marca del historial, con transacciones de verdad |
| `MigracionTest` *(instrumentada)* | Que pasar de la versión 1 a la 2 no pierda ninguna entrada y le abra historial a las que ya estaban |
