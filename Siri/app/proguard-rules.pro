# Reglas para kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.assistant.siri.**$$serializer { *; }
-keepclassmembers class com.assistant.siri.** {
    *** Companion;
}
-keepclasseswithmembers class com.assistant.siri.** {
    kotlinx.serialization.KSerializer serializer(...);
}
