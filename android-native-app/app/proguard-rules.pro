# ProGuard / R8 rules for Likkho Native Android App
-keep class com.likkho.nativeapp.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn java.lang.invoke.**
