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
# ------------------------------------------------------------------ #
# 终端（Termux 内核）
# JNI 符号是 Java_com_termux_terminal_JNI_*，类名或 native 方法名一旦被
# 重命名，C 侧就找不到入口，运行时直接 UnsatisfiedLinkError。
# ------------------------------------------------------------------ #
-keep class com.termux.terminal.JNI { *; }
-keepclasseswithmembernames class * { native <methods>; }

# 终端视图在代码里 new，类名保留便于排查栈
-keep class com.termux.view.TerminalView { *; }
