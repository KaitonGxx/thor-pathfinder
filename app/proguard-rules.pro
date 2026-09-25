# Shizuku.newProcess is private in API 13.1.5; Shell.kt reaches it by reflection.
-keep class rikka.shizuku.Shizuku { *; }

# Shizuku starts the app-profiles helper by its class name (see AppWatcher).
-keep class com.thorpathfinder.app.TaskWatcher { <init>(); }
