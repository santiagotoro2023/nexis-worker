# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.** { *; }

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }

# Keep BuildConfig fields
-keep class ch.toroag.nexis.worker.BuildConfig { *; }
