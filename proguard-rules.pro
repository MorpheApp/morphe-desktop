-dontobfuscate
-dontoptimize
-dontnote **
-dontwarn **
-ignorewarnings
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable

-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class org.slf4j.nop.** { *; }

# Bouncy Castle
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.crypto.prng.** { *; }
-keep class * extends java.security.Provider
-keep class * extends java.security.KeyStoreSpi

-keep class app.morphe.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class com.android.apksig.** { *; }
-keep class com.android.tools.build.apkzlib.** { *; }

# Picocli (Command line parsing)
-keep class picocli.** { *; }

# Koin (Dependency Injection - uses reflection)
-keep class org.koin.** { *; }

# Ktor (Networking - uses reflection/ServiceLoader)
-keep class io.ktor.** { *; }

# FileKit (Native file dialogs)
-keep class io.github.vinceglb.filekit.** { *; }

# Keep SPI services (ServiceLoader)
-keepnames class * {
    public static <fields>;
    public static <methods>;
}
-keepclassmembers class * {
    @**.KoinInternalApi *;
}
