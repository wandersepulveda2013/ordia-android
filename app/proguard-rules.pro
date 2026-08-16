# Ordia keeps all persistent data local. Room generates the implementation classes.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# WorkManager instantiates ListenableWorker implementations by class name.
-keep class com.ordia.app.reminders.** extends androidx.work.ListenableWorker { *; }
-keep class com.ordia.app.updates.** extends androidx.work.ListenableWorker { *; }

# Keep enum names serialized by Room converters and backup files.
-keepclassmembers enum com.ordia.app.data.local.** { *; }
