# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# The Xposed API is compileOnly and provided at runtime by the hooking
# framework, so R8 must not fail on the missing classes.
-dontwarn de.robv.android.xposed.**

# Entry classes are referenced by their fully qualified name from
# assets/xposed_init and assets/yukihookapi_init, so their names must be
# preserved. YukiHookAPI itself needs no keep rules under R8.
-keep class com.hcgstudio.miui.fuck.gesture.MainHook { *; }
-keep class com.hcgstudio.miui.fuck.gesture.MainHook_YukiHookXposedInit { *; }
