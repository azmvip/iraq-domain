# Add project specific ProGuard rules here.

# Keep application class names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Obfuscate all BuildConfig fields' string contents at class level
# (the key is embedded at compile time, making it harder to find)
-keep class com.redload.app.BuildConfig { *; }

# Keep activity names so the system can start them
-keep public class com.redload.app.** extends android.app.Activity
-keep public class com.redload.app.** extends androidx.appcompat.app.AppCompatActivity

# JSONObject — used for API responses
-keep class org.json.** { *; }

# Kotlin metadata
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { *; }

# Suppress missing class warnings
-dontwarn java.lang.invoke.StringConcatFactory

# Firebase Analytics (its aar ships its own consumer rules; this is a safety net)
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# jsoup — used by our own extractor library
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# Skraper + Ktor + coroutines — safety net for the full-library integration
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn ru.sokomishalov.skraper.**
-keep class ru.sokomishalov.skraper.** { *; }
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
