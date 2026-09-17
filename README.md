# Bóveda

Gestor de contraseñas sin conexión, en dos apps que comparten funciones, interfaz y **formato de
respaldo**: un archivo exportado en el teléfono se importa en la computadora, y al revés.

| Proyecto | Plataforma | Tecnología | Documentación |
|---|---|---|---|
| [`boveda-android/`](boveda-android) | Android 8.0+ (minSdk 26 · targetSdk 36) | Kotlin + Jetpack Compose, Room + SQLCipher | [README](boveda-android/README.md) |
| [`boveda-desktop/`](boveda-desktop) | Windows, macOS y Linux | Kotlin + Compose Multiplatform, archivo AES-256-GCM | [README](boveda-desktop/README.md) |

Son dos builds de Gradle independientes, cada una con su propio `gradlew`: no hay un proyecto raíz
que las agrupe. Los comandos se lanzan desde la carpeta de cada app.

---

## En común

- **Sin red.** Ninguna de las dos habla con internet ni con otras apps.
- **El mismo esquema de cifrado de dos niveles:** la contraseña maestra (PBKDF2-HMAC-SHA256,
  210 000 iteraciones) desenvuelve con AES-256-GCM una frase aleatoria, y esa frase es la que
  cifra la base. Cambiar la maestra no recifra nada.
- **Freno a la fuerza bruta** con tres intentos gratis y castigo exponencial medido con dos relojes.
- **Papelera de 30 días e historial de contraseñas** por entrada.
- **Respaldo** en un sobre JSON versión 2 (lee también la 1) con cabecera autenticada como AAD y su
  propia contraseña. La fusión al importar sigue la misma regla en las dos apps (misma cuenta =
  título + usuario, gana la versión más reciente y ninguna contraseña se pierde), con la misma
  `FusionTest`.

La compatibilidad entre las dos no se da por supuesta: `CompatibilidadAndroidTest`, en escritorio,
la contrasta con una implementación independiente en Node.js
([herramientas/respaldo-referencia.mjs](boveda-desktop/herramientas/respaldo-referencia.mjs)).
Un cambio en `Kdf.kt`, `Sobre.kt`, `Respaldo.kt` o la fusión de `GestorRespaldo.kt` tiene que
hacerse **en los dos proyectos a la vez**.

## En qué se diferencian

| | Android | Escritorio |
|---|---|---|
| Base | SQLCipher + Room, con migraciones | Un solo archivo `boveda.dat` cifrado, con escritura atómica |
| Desbloqueo con huella | Sí (Keystore/TEE) | No: no hay una API común a Windows Hello, Touch ID y Linux |
| Protección contra capturas | `FLAG_SECURE` | Solo en Windows (`SetWindowDisplayAffinity`) |
| Pruebas | 83 en la JVM + 26 instrumentadas | 120 en la JVM, flujos completos incluidos |
| Entregable | APK firmado | `.msi`/`.exe`, `.dmg`, `.deb`/`.rpm` |

## Construir

Las dos usan Gradle 8.14, que no corre sobre JDK 25 o posterior. Android además pide exactamente
**JDK 21** por AGP 8.10. Con el JBR 21 de Android Studio sirve para las dos:

```bash
cd boveda-android && JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew :app:assembleDebug
```

```bash
cd boveda-desktop && JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew run
```

La firma del APK, las pruebas instrumentadas, los instaladores de escritorio y el razonamiento de
seguridad completo están en el README de cada app.
