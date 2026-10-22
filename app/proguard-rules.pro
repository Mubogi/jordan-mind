# MediaPipe LLM Inference (JNI-backed)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ML Kit text recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# PdfBox-Android
-dontwarn org.apache.pdfbox.**
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Hand-rolled OOXML writers use plain java.util.zip only.
-dontwarn javax.xml.**
-dontwarn java.awt.**
