# Ktor rules
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Coroutines rules
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Room rules
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
