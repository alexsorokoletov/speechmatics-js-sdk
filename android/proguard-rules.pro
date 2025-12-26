# Speechmatics Android SDK ProGuard Rules

# Keep all public API classes
-keep class com.speechmatics.sdk.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.speechmatics.sdk.**$$serializer { *; }
-keepclassmembers class com.speechmatics.sdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.speechmatics.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
