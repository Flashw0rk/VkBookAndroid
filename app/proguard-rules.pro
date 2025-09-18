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
# Сохраняем классы Apache POI, OOXML и XMLBeans, чтобы избежать NoClassDefFoundError в релизе
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# Подавляем предупреждения о XMLBeans/OOXML
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**

# --- Apache POI on Android: suppress optional desktop/JSR deps not present on Android ---
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.events.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.pdfbox.**
-dontwarn de.rototor.pdfbox.graphics2d.**
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.xmlbeans.**

# Missing classes suggested by AGP (from mapping/missing_rules.txt)
-dontwarn org.apache.jcp.xml.dsig.internal.dom.ApacheNodeSetData
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMKeyInfo
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMReference
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMSignedInfo
-dontwarn org.apache.jcp.xml.dsig.internal.dom.DOMSubTreeData
-dontwarn org.apache.xml.security.Init
-dontwarn org.apache.xml.security.c14n.Canonicalizer
-dontwarn org.apache.xml.security.signature.XMLSignatureInput
-dontwarn org.apache.xml.security.utils.XMLUtils
-dontwarn org.w3c.dom.svg.SVGDocument
-dontwarn org.w3c.dom.svg.SVGSVGElement
-dontwarn org.w3c.dom.traversal.DocumentTraversal
-dontwarn org.w3c.dom.traversal.NodeFilter
-dontwarn org.w3c.dom.traversal.NodeIterator

# Keep Retrofit interfaces and annotations
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature

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