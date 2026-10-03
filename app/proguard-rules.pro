# Line numbers in crash reports (the crash log); names are decoded with mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep kotlinx.serialization metadata for @Serializable classes (navigation routes, backups).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# On-device AI (LiteRT-LM): native code calls back into these classes.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.ai.edge.litertlm.**

# Widgets: Glance creates tap actions from their class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# Background jobs: WorkManager creates workers by class name.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
