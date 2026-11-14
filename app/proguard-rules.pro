# Serialization and OkHttp ship their own consumer rules. Avoid blanket keeps that
# disable shrinking. Keep source/line metadata for private release crash diagnosis.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
