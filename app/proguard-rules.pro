# Retrofit interfaces are referenced reflectively.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.radustavila.nasaexplorer.**$$serializer { *; }
-keepclassmembers class com.radustavila.nasaexplorer.** {
    *** Companion;
}
