# R8 rules for the release build, on top of proguard-android-optimize.txt and the rules each
# library brings with it.
#
# Loaded by reflection:
# - Room finds the AppDatabase_Impl it generates by AppDatabase's name. Room's own rules keep
#   both (-keep class * extends androidx.room.RoomDatabase), and the entities and DAOs are only
#   reached through that generated code.
# - kotlinx.serialization: the backup, the catalogue, the sprites, the backdrops and the advice
#   are read through generated serializers that the code calls by name (CatalogueFile.serializer()
#   and so on). The library's own rules keep each @Serializable class's serializer, and JSON keys
#   come from the generated descriptors, so renaming fields can't change the backup format.
# - ViewModels are built by graphViewModel's factory lambdas, not by reflection, and navigation
#   uses string routes.
#
# Java resources: :core:domain reads its JSON with getResourceAsStream and absolute paths such as
# "/catalogue/products.json". R8 passes Java resources through as they are, and absolute paths
# don't depend on class or package names, so renaming classes is safe.
# ./gradlew :app:checkReleaseResources checks every file is in the release APK and bundle.

# Keep file and line numbers, so a stack trace from logcat can be read back with the mapping
# file (app/build/outputs/mapping/release/mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
