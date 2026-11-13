# Compatibilidad entre versiones: lo persistido (JSON de kotlinx.serialization, nombres de enum en DataStore,
# entidades de Room) debe conservar sus nombres tras R8.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.usos.launcher.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.usos.launcher.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum com.usos.launcher.** { <fields>; }
-keep class com.usos.launcher.data.feeds.Feed { *; }
-keep class com.usos.launcher.data.feeds.Article { *; }
