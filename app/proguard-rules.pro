# Retrofit interfaces are referenced reflectively.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# kotlinx.serialization ships R8 rules for @Serializable classes; these only cover the app's own
# generated serializers and the companions that hand them out.
-keep,includedescriptorclasses class com.radustavila.nasaexplorer.**$$serializer { *; }
-keepclassmembers class com.radustavila.nasaexplorer.** {
    *** Companion;
}
