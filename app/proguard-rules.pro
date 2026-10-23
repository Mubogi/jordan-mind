# Jordan Mind R8 rules.
#
# The app is mostly first-party code, so these rules stay narrow: keep the reflection entry points
# that third-party libraries rely on, and let R8 strip everything else.

# MediaPipe LLM Inference loads its native bridge and task options reflectively.
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ML Kit resolves detector implementations by class name.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# PDFBox reflects over font and COS model classes.
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**

# The hand-rolled OOXML writers use java.util.zip only; these are optional PDFBox dependencies.
-dontwarn javax.xml.**
-dontwarn java.awt.**

# Coroutines.
-dontwarn kotlinx.coroutines.**

# com.jordanmind.agent.* is serialised field by field through org.json, never reflectively, so it
# needs no keep rules and R8 is free to shrink it.

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
