# Bóveda

Gestor de contraseñas sin conexión, en dos apps que comparten funciones, interfaz y **formato de
respaldo**: un archivo exportado en el teléfono se importa en la computadora, y al revés.

| Proyecto | Qué es | Tecnología | Documentación |
|---|---|---|---|
| [`boveda-android/`](boveda-android) | App para Android 8.0+ (minSdk 26 · targetSdk 36) | Kotlin + Jetpack Compose, Room + SQLCipher | [README](boveda-android/README.md) |
| [`boveda-desktop/`](boveda-desktop) | App para Windows, macOS y Linux | Kotlin + Compose Multiplatform, archivo AES-256-GCM | [README](boveda-desktop/README.md) |
| [`boveda-nucleo/`](boveda-nucleo) | Código que comparten las dos | Kotlin/JVM puro, sin dependencias en tiempo de ejecución | abajo |

Las dos apps son builds de Gradle independientes, cada una con su propio `gradlew`, y las dos
incluyen el núcleo con `includeBuild`: Gradle lo compila como parte de cada una, sin publicar nada.
Los comandos se lanzan desde la carpeta de cada app.

---

## En común

- **Sin red.** Ninguna de las dos habla con internet ni con otras apps.
- **El mismo esquema de cifrado de dos niveles:** la contraseña maestra (PBKDF2-HMAC-SHA256,
  600 000 iteraciones) desenvuelve con AES-256-GCM una frase aleatoria, y esa frase es la que
  cifra la base. Cambiar la maestra no recifra nada.
- **Freno a la fuerza bruta** con tres intentos gratis y castigo exponencial medido con dos relojes.
- **Papelera de 30 días e historial de contraseñas** por entrada.
- **Respaldo** en un sobre JSON versión 2 (lee también la 1) con cabecera autenticada como AAD y su
  propia contraseña. Al importar, la misma cuenta es título + usuario, gana la versión más reciente
  y ninguna contraseña se pierde.

### El núcleo compartido

Lo que tiene que ser idéntico en las dos apps vive una sola vez, en `boveda-nucleo/`:

| | |
|---|---|
| `seguridad/Sobre.kt` | AES-256-GCM |
| `seguridad/Kdf.kt` | PBKDF2, iteraciones y medidor de fuerza |
| `seguridad/Generador.kt` | Contraseñas con `SecureRandom` |
| `datos/Credencial.kt`, `datos/HistorialContrasena.kt` | El modelo, sin nada de cómo lo guarda cada app |
| `datos/Respaldo.kt` | El formato del archivo de respaldo |
| `datos/Fusion.kt` | Qué entra, qué se actualiza y qué se omite al importar |
| `datos/Papelera.kt` | El plazo de 30 días |
| `Licencias.kt` | El texto de Ajustes → Licencias |

Sus pruebas (criptografía, formato, fusión, historial, papelera y licencias) corren desde
cualquiera de las dos apps:

```bash
cd boveda-desktop && ./gradlew :boveda-nucleo:test
```

Que las dos apps usen el mismo código no demuestra que ese código siga la especificación del
formato. Por eso `CompatibilidadFormatoTest` lo contrasta con un respaldo escrito por una
implementación independiente en Node.js
([herramientas/respaldo-referencia.mjs](boveda-nucleo/herramientas/respaldo-referencia.mjs)),
y la app de Android repite la lectura con el org.json de Android (`RespaldoAndroidTest`).

## En qué se diferencian

| | Android | Escritorio |
|---|---|---|
| Base | SQLCipher + Room, con migraciones | Un solo archivo `boveda.dat` cifrado, con escritura atómica |
| Desbloqueo con huella | Sí (Keystore/TEE) | No: no hay una API común a Windows Hello, Touch ID y Linux |
| Protección contra capturas | `FLAG_SECURE` | Solo en Windows (`SetWindowDisplayAffinity`) |
| Pruebas propias | JVM (Robolectric) + instrumentadas contra SQLCipher | JVM, con flujos completos y pantallas |
| Entregable | APK firmado | `.msi`/`.exe`, `.dmg`, `.deb`/`.rpm` |

## Construir

Gradle corre sobre un **JDK 21** —lo fija `gradle/gradle-daemon-jvm.properties` de cada app,
porque detekt 1.23 no arranca sobre los JDK más nuevos (falla con el 26)— y lo busca solo entre los JDK instalados: sirve
el JBR 21 que trae Android Studio. El `java` del sistema puede ser otro, y no hace falta `JAVA_HOME`.

```bash
cd boveda-android && ./gradlew :app:assembleDebug
```

```bash
cd boveda-desktop && ./gradlew run
```

La firma del APK, las pruebas instrumentadas, los instaladores de escritorio y el razonamiento de
seguridad completo están en el README de cada app.

## Versiones y publicación

- La versión de las dos apps vive en [version.properties](version.properties). Los cambios se
  anotan en el [CHANGELOG](CHANGELOG.md).
- Para publicar: subir la versión, anotar el CHANGELOG, crear el tag `vX.Y.Z` y subirlo. El CI
  genera el APK sin firmar y los instaladores de los tres sistemas como artefactos del run.

## Integración continua

En cada push y pull request, [GitHub Actions](.github/workflows/ci.yml) corre las pruebas y detekt
del núcleo y de escritorio en Windows, macOS y Linux, y las pruebas, detekt, lint y el release de
Android. [CodeQL](.github/workflows/codeql.yml) analiza el código cada semana y en cada cambio.
[Dependabot](.github/dependabot.yml) propone actualizaciones de dependencias cada semana.

### Dependencias verificadas

Gradle comprueba la suma SHA-256 de su propia distribución (`distributionSha256Sum` en
`gradle-wrapper.properties`) y la de cada dependencia contra `gradle/verification-metadata.xml`
de cada app. Una dependencia nueva o actualizada, por ejemplo en un PR de Dependabot, hace fallar
la build hasta anotar su suma:

```bash
cd boveda-desktop && ./gradlew --write-verification-metadata sha256 help
```

```bash
cd boveda-android && ./gradlew --write-verification-metadata sha256 help :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

Si se quita una dependencia, sus entradas se quedan en el archivo. Para limpiarlo, se borra y se
vuelve a generar con los mismos comandos; en Android hay que conservar el bloque
`<trusted-artifacts>` de `aapt2`, que Gradle no escribe solo.

Antes de subir el archivo regenerado, conviene revisar en el diff que solo cambien las
dependencias que se querían cambiar.

## Licencia

[MIT](LICENSE). Las licencias de los componentes de terceros están en Ajustes → Licencias de
cada app. Para reportar un fallo de seguridad, ver [SECURITY.md](SECURITY.md).
