# ===== Gson 必备（保留所有数据类不被混淆） =====
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
-keep class com.yutaca.record.data.entity.** { *; }  # 保留 Room Entity 数据类不被混淆（Gson 序列化用）
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== Notebook 导入/导出数据类（Gson 反序列化需要保留字段名） =====
-keep class com.yutaca.record.data.export.** { *; }

# ===== Room 必备（保留生成的代码） =====
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# ===== Kotlin 协程必备 =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler