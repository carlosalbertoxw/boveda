# SQLCipher (las clases que usa por JNI) y Room (el constructor de la base que
# instancia por reflexion) traen sus propias reglas de R8 dentro del AAR, asi
# que aqui no se repiten.

# Borra del binario de release toda llamada a Log: una contrasena que se
# escape a logcat es legible por cualquier app con permiso de lectura de logs
# en versiones viejas, y por adb siempre.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}
