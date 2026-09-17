# SQLCipher entra por JNI: R8 no ve las llamadas y borraria las clases.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Room genera implementaciones por reflexion en tiempo de construccion.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

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
