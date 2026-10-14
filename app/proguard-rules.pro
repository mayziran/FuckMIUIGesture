# The libxposed API is compileOnly and provided by the framework at runtime.
-dontwarn io.github.libxposed.**

# The entry class is named in META-INF/xposed/java_init.list, so it must keep its name.
-keep class com.hcgstudio.miui.fuck.gesture.MainHook { *; }
