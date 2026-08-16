# Bóveda

Gestor de contraseñas para Android, sin conexión y sin comunicación con otras apps.
Guardas una credencial, y cuando no recuerdas una entras, la ves o la copias.

- **Paquete:** `com.carlosalbertoxw.boveda`
- **minSdk 26 · targetSdk 36 · Kotlin + Jetpack Compose**
- **Estado:** compila en debug y release, 25 pruebas unitarias en verde (criptografía y respaldo).

---

## Cómo se construye

Requiere **JDK 21**. El JBR 25 que trae el Android Studio actual rompe AGP 8.10 con un
error ilegible (`* What went wrong: 25.0.2`). La ruta al JDK vive en la configuración
local de Android Studio (`.idea/`, `.gradle/`), que no se versiona: en un clon nuevo hay
que volver a apuntarla en *Settings → Build Tools → Gradle → Gradle JDK*, o pasar
`JAVA_HOME` en cada invocación como en los ejemplos de abajo.

Desde la terminal:

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:assembleDebug
```

Las pruebas:

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:testDebugUnitTest
```

### Firmar el release

La firma se configura una sola vez y con dos piezas, ninguna de las cuales se versiona.

**1. Crear el keystore** (una vez en la vida de la app: si se pierde, no se puede volver a
publicar una actualización sobre la misma instalación). `keytool` pregunta las contraseñas
de forma interactiva, así que no quedan en el historial de la terminal:

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v -keystore boveda-release.jks -alias boveda -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

**2. Crear `keystore.properties`** en la raíz, copiando `keystore.properties.template` y
rellenando las contraseñas:

```
storeFile=boveda-release.jks
storePassword=...
keyAlias=boveda
keyPassword=...
```

Con eso, `assembleRelease` firma el APK (v2 + v3; v1 no hace falta con `minSdk 26`):

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:assembleRelease
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
| PBKDF2-HMAC-SHA256, 210 000 iteraciones | Cada intento de adivinar cuesta trabajo real | [Kdf.kt](app/src/main/java/com/carlosalbertoxw/boveda/seguridad/Kdf.kt) |
| AES-256-GCM | Cifrado autenticado; la etiqueta es lo que verifica la contraseña | [Sobre.kt](app/src/main/java/com/carlosalbertoxw/boveda/seguridad/Sobre.kt) |
| Keystore con `setUserAuthenticationRequired` | El chip se niega a descifrar sin huella válida | [AlmacenBiometrico.kt](app/src/main/java/com/carlosalbertoxw/boveda/seguridad/AlmacenBiometrico.kt) |
| Freno exponencial + borrado opcional | 3 intentos gratis, luego 15 s → 5 min | [GestorBoveda.kt](app/src/main/java/com/carlosalbertoxw/boveda/seguridad/GestorBoveda.kt) |

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
- La contraseña nunca aparece en el listado. Revelarla se vuelve a tapar sola a los 20 s.
- Cierre automático al pasar a segundo plano y por inactividad (configurable).

### Portapapeles

Es el punto más débil de cualquier gestor: es el único momento en que el secreto sale de la
app, y en Android es un buzón compartido. Se acota marcándolo como sensible (Android 13+ no
lo muestra en la vista previa), borrándolo solo a los 30 s y comprobando antes que siga
siendo nuestro para no pisarle al usuario algo que copió después.

---

## Respaldo

Un archivo `.json` con la cabecera legible y el contenido cifrado en AES-256-GCM, con su
propia contraseña y su propia sal:

```json
{
  "formato": "boveda-respaldo",
  "version": 1,
  "creado": "2026-08-15T18:00:00Z",
  "entradas": 12,
  "kdf": { "algoritmo": "PBKDF2WithHmacSHA256", "iteraciones": 210000, "sal": "..." },
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

**Cómo se usa.** Ajustes → Respaldo. Exportar pide la maestra otra vez (sacar todas las
contraseñas de golpe es la operación más peligrosa de la app) más una contraseña para el
archivo. Importar ofrece fusionar —omitiendo lo que ya está idéntico, para que importar dos
veces no duplique la bóveda— o reemplazar todo.

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
  de iteraciones se guarda junto a la sal, así que se puede subir después sin dejar
  inservibles las bóvedas ya creadas.

---

## Estructura

```
app/src/main/java/com/carlosalbertoxw/boveda/
├── AplicacionBoveda.kt      Estado del candado, colgado del proceso (no de la Activity)
├── MainActivity.kt          FLAG_SECURE, anti-tapjacking, inactividad
├── seguridad/
│   ├── Sobre.kt             AES-256-GCM
│   ├── Kdf.kt               PBKDF2 + medidor de fuerza
│   ├── AlmacenBiometrico.kt Llave del Keystore ligada a la huella
│   ├── Biometria.kt         BiometricPrompt, siempre con CryptoObject
│   ├── GestorBoveda.kt      Abrir, cerrar, cambiar maestra, borrar
│   ├── PreferenciasBoveda.kt Sal, envolturas y ajustes
│   ├── Generador.kt         Contraseñas con SecureRandom
│   └── Portapapeles.kt      Copiado sensible con borrado automático
├── datos/
│   ├── Credencial.kt        Entidad, BovedaDatabase.kt, CredencialDao.kt
│   ├── Respaldo.kt          Formato del archivo: sobre JSON + AES-GCM con AAD
│   └── GestorRespaldo.kt    Exportar/importar por SAF, fusión y deduplicado
└── ui/                      Compose: alta, desbloqueo, listado, detalle, editor, ajustes
```
