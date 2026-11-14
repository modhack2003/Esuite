# Serialization and OkHttp ship their own consumer rules. Avoid blanket keeps that
# disable shrinking. Keep source/line metadata for private release crash diagnosis.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX instrumentation shares this library with the tested optimized app.
# Retain its small tracing entry points so inlining/removal in the application
# cannot leave the separately packaged test runner with unresolved classes.
-keep class androidx.tracing.** { *; }
