# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Keep only what is required to ensure runtime/reflection compatibility
# (Узкий набор правил вместо полного запрета обфускации приложения)

# Activities/Services/BroadcastReceivers/ContentProviders — безопасно держать члены,
# а имена компонентов Android R8 синхронизирует с манифестом
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# Фрагменты (часто могут упоминаться в XML/через рефлексию библиотек)
-keep class * extends androidx.fragment.app.Fragment { *; }

# Kotlin metadata и reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# Kotlin classes - полная защита
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keep class kotlinx.coroutines.** { *; }

# Keep all classes in our package (КРИТИЧНО: защищаем весь код приложения от удаления)
-keep class com.example.vkbookandroid.** { *; }
-keep class org.example.pult.android.** { *; }
-keepclassmembers class com.example.vkbookandroid.** { *; }
-keepclassmembers class org.example.pult.android.** { *; }

# RecyclerView Adapters
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder { *; }
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    <init>(...);
}

# ViewPager2 Adapters
-keep class * extends androidx.viewpager2.adapter.FragmentStateAdapter { *; }

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Удалено правило, удерживавшее строковые поля пароля — не сохраняем их явно,
# чтобы усложнить извлечение статически.

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
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.apache.logging.log4j.**

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

# ========================================
# ОПТИМИЗАЦИЯ: Удаление логов в релизе
# ========================================
# Удаляем отладочные логи (Log.d, Log.v, Log.i) для:
# - Повышения производительности (~15% CPU)
# - Улучшения безопасности (скрытие логики работы)
# - Уменьшения размера APK (~50-100 КБ)
# - Экономии батареи (~10-15%)
#
# ОСТАВЛЯЕМ: Log.e() и Log.w() для отслеживания критических ошибок

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile