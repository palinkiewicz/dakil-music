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

# JAudiotagger (Adonai fork) needs its whole package kept. It loads ID3 frame-body
# classes reflectively by name (Class.forName("...FrameBody" + frameId)), AND
# several classes call getClass().getPackage().getName() — which returns null once
# proguard-android-optimize.txt repackages classes into the root package, throwing
# NPE. Keeping the package preserves both the class names and the package structure.
-keep class org.jaudiotagger.** { *; }

# JCodec ships *inside* the Adonai JAudiotagger jar (package org.jcodec) and backs the
# MP4/M4A read+rewrite path. It instantiates every MP4 box class reflectively by its
# fourcc (constructor(Header).newInstance(...)), so R8 renaming those constructors makes
# editing an M4A fail at runtime with NoSuchMethodException — but only on release builds
# and only for libraries that are actually MP4. Keep the whole package intact.
-keep class org.jcodec.** { *; }
-dontwarn org.jcodec.**

# The library references desktop/Java SE classes (javax.imageio, java.awt, etc.)
# that don't exist on Android; silence the resulting R8 warnings.
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**