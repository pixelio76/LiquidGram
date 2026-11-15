# TDLib's JNI side creates TdApi objects by name
-keep class org.drinkless.tdlib.** { *; }

# okio (via a dependency) references JSR-305 annotations that are not on the classpath; they are compile-time only.
-dontwarn javax.annotation.**
