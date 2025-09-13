# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Keep all classes in our main package to prevent obfuscation issues
-keep class com.example.vkbookandroid.** { *; }
-keep class org.example.pult.** { *; }

# Keep Excel-related classes
-keep class org.apache.poi.** { *; }

# Keep JSON serialization classes
-keep class com.google.gson.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile