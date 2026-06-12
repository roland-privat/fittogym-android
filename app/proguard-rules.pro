# ProGuard / R8 keep rules for the release variant.
# Inherits the AGP defaults from proguard-android-optimize.txt.

# ---------- Application entry points ----------
# Keep the application + activity + service + broadcast receivers so
# the manifest can still resolve them after shrinking.
-keep class com.example.runtraining.RunTrainingApp { *; }
-keep class com.example.runtraining.MainActivity { *; }
-keep class com.example.runtraining.service.** { *; }

# ---------- Kotlin metadata ----------
# Needed for reflection used by Compose tooling, kotlinx-coroutines debug
# probes, and any Kotlin .copy() / data-class .equals() reflection.
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.coroutines.jvm.internal.DebugMetadata { *; }

# ---------- kotlinx.coroutines ----------
# These class references are looked up reflectively.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# ---------- AndroidX Room ----------
# Room generates `*_Impl` classes via KSP — keep them and the Dao entry points.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class **_Impl { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ---------- Garmin FIT SDK ----------
# The Java SDK uses reflection internally for message factories. Keeping
# everything under com.garmin.fit is the documented recommendation.
-keep class com.garmin.fit.** { *; }
-dontwarn com.garmin.fit.**

# ---------- AndroidX DataStore (proto + preferences) ----------
-keep class androidx.datastore.preferences.** { *; }

# ---------- Jetpack Compose ----------
# Compose runtime is largely shrink-safe but Compose previews use reflection.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ---------- ViewModels ----------
# We instantiate ViewModels via reflection in viewmodel-compose factories.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ---------- BLE callbacks ----------
# Android calls these via JNI/AIDL; keep the callback methods.
-keepclassmembers class * extends android.bluetooth.BluetoothGattCallback { *; }
-keepclassmembers class * extends android.bluetooth.le.ScanCallback { *; }

# ---------- Crash reports ----------
# Preserve line numbers + source file so a stack trace stays readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
