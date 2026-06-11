# keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.korvus.nomnom.**$$serializer { *; }
-keepclassmembers class com.korvus.nomnom.** { *** Companion; }
-keepclasseswithmembers class com.korvus.nomnom.** { kotlinx.serialization.KSerializer serializer(...); }
