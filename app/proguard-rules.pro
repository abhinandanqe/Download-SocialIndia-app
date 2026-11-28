# Social India Release ProGuard / R8 Rules

# Preserve stack trace line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep Firestore / Room / Retrofit / Serialization models
-keep class com.example.model.** { *; }
-keep class com.example.repository.** { *; }
-keep class com.example.api.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ZegoCloud Live Streaming SDK
-keep class im.zego.** { *; }
-keep class com.zegocloud.** { *; }
-dontwarn im.zego.**
-dontwarn com.zegocloud.**

# Firebase / Google Play Services
-keepattributes Exceptions
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
