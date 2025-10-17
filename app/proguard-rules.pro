# Keep TensorFlow Lite classes (for face embedding model)
-keep class org.tensorflow.** { *; }

# Keep ML Kit classes (for face detection)
-keep class com.google.mlkit.** { *; }

# Optional: keep Firebase model classes (safe for Firestore/Storage)
-keep class com.google.firebase.** { *; }

# Avoid warnings from Kotlin coroutines
-dontwarn kotlinx.coroutines.**
