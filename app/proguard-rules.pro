# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# Keep all the methods and fields of MPVLib since it's used via reflection
# Keep the MPVLib class and all its methods (including native methods)
-keep class cloud.app.csplayer.ui.player.mpv.MPVLib {
    *;
}

# Keep inner static classes for constants
-keep class cloud.app.csplayer.ui.player.mpv.MPVLib$mpvFormat { *; }
-keep class cloud.app.csplayer.ui.player.mpv.MPVLib$mpvEventId { *; }
-keep class cloud.app.csplayer.ui.player.mpv.MPVLib$mpvLogLevel { *; }

# Keep EventObserver and LogObserver interfaces and their methods
-keep interface cloud.app.csplayer.ui.player.mpv.MPVLib$EventObserver { *; }
-keep interface cloud.app.csplayer.ui.player.mpv.MPVLib$LogObserver { *; }

# Avoid removing Log statements
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int w(...);
    public static int e(...);
}

# Keep log messages, if needed
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int w(...);
    public static int e(...);
}

# Add contents of missing_rules.txt here
-keep class com.facebook.infer.annotation.** { *; }
