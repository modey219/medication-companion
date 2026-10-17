# ProGuard / R8 rules for the Medication Companion app.
# R8 is enabled for release builds in T11 (release preparation).

# Keep line numbers for readable crash reports while hiding source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
