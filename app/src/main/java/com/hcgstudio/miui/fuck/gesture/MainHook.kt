package com.hcgstudio.miui.fuck.gesture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method


private const val TAG = "FuckMiuiGesture"
private const val HIDE_GESTURE_LINE = "hide_gesture_line"
private const val NAVIGATION_BAR_SETTINGS = "com.miui.home.action.navigation_bar_type_settings"

private const val PACKAGE_SYSTEM_UI = "com.android.systemui"
private const val PACKAGE_MIUI_HOME = "com.miui.home"
private const val PACKAGE_VOICE_ASSIST = "com.miui.voiceassist"

private val MIUI_HOME_PACKAGES = setOf(
    "com.miui.home",
    "com.android.provision",
    "com.mi.android.globallauncher",
    "com.aura.oobe.kddi",
    "com.google.android.setupwizard"
)

/**
 * Entry point of the modern Xposed API. The class is named in
 * META-INF/xposed/java_init.list and instantiated by the framework, which then
 * calls [onPackageLoaded] for every package listed in META-INF/xposed/scope.list.
 */
class MainHook : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        when (param.packageName) {
            PACKAGE_SYSTEM_UI -> hookSystemUi(classLoader)
            PACKAGE_MIUI_HOME -> hookMiuiHome(classLoader)
            PACKAGE_VOICE_ASSIST -> hookVoiceAssist(classLoader)
        }
    }

    private fun hookSystemUi(classLoader: ClassLoader) {
        val prefs = getRemotePreferences(DataConst.PREFS_NAME)
        val strongMode = prefs.getBoolean(DataConst.STRONG_MODE, DataConst.STRONG_MODE_DEFAULT)
        val hideLineOnHomeSwitch =
            prefs.getBoolean(DataConst.HIDE_LINE_ON_HOME_SWITCH, DataConst.HIDE_LINE_ON_HOME_SWITCH_DEFAULT)

        // Stop the framework from turning gesture navigation off when the default launcher
        // changes, and keep the gesture hint line in sync with the launcher.
        onMethods(classLoader, "com.android.systemui.assist.PhoneStateMonitorController", "onDefaultHomeChanged") { method ->
            hook(method).intercept { chain ->
                guarded(chain) {
                    if (hideLineOnHomeSwitch) {
                        val home = chain.getArgs().firstOrNull { it is ComponentName } as? ComponentName
                        applyHideGestureLine(contextOf(chain.getThisObject()), home)
                    }
                    null
                }
            }
        }

        // Strong mode: always report force_fsg_nav_bar as enabled.
        if (strongMode) {
            onMethods(
                classLoader,
                "android.provider.MiuiSettings\$Global",
                "getBoolean",
                Boolean::class.javaObjectType
            ) { method ->
                hook(method).intercept { chain ->
                    guarded(chain) {
                        if (chain.getArgs().getOrNull(1) == "force_fsg_nav_bar") true else chain.proceed()
                    }
                }
            }
        }
    }

    private fun hookMiuiHome(classLoader: ClassLoader) {
        // Send MIUI home's navigation settings entry to this module's settings.
        onMethods(classLoader, "androidx.preference.Preference", "setIntent") { method ->
            hook(method).intercept { chain ->
                guarded(chain) {
                    val old = chain.getArgs().getOrNull(0) as? Intent
                    if (old?.action == NAVIGATION_BAR_SETTINGS) {
                        val replacement = Intent()
                        replacement.setClassName(
                            "com.hcgstudio.miui.fuck.gesture",
                            "com.hcgstudio.miui.fuck.gesture.SettingsActivity"
                        )
                        val args = chain.getArgs().toMutableList()
                        args[0] = replacement
                        chain.proceed(args.toTypedArray())
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        // Keep MIUI home believing it is the default home.
        onMethods(classLoader, "com.miui.home.common.utils.BuildConfigUtils", "isUseMiuiHomeAsDefaultHome") { method ->
            hook(method).intercept { _ -> true }
        }
    }

    private fun hookVoiceAssist(classLoader: ClassLoader) {
        // Voice assistant: force force_fsg_nav_bar to 1 whenever it writes it.
        onMethods(classLoader, "android.provider.Settings\$Global", "putInt") { method ->
            hook(method).intercept { chain ->
                guarded(chain) {
                    if (chain.getArgs().getOrNull(1) == "force_fsg_nav_bar") {
                        val args = chain.getArgs().toMutableList()
                        args[2] = 1
                        chain.proceed(args.toTypedArray())
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
    }

    /**
     * Hooks every method of [className] named [methodName] - searching the superclass
     * chain as well - optionally restricted to [returnType].
     */
    private fun onMethods(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        returnType: Class<*>? = null,
        install: (Method) -> Unit
    ) {
        val methods = try {
            methodsNamed(classLoader.loadClass(className), methodName, returnType)
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "$className not found")
            return
        }
        if (methods.isEmpty()) {
            log(Log.WARN, TAG, "$className.$methodName not found")
            return
        }
        methods.forEach(install)
    }

    /** Runs a hook body and never lets an exception escape into the hooked process. */
    private fun guarded(chain: XposedInterface.Chain, body: () -> Any?): Any? = try {
        body()
    } catch (t: Throwable) {
        log(Log.WARN, TAG, "hook failed, falling back: ${t.message}")
        try {
            chain.proceed()
        } catch (_: Throwable) {
            null
        }
    }
}

private fun methodsNamed(clazz: Class<*>, methodName: String, returnType: Class<*>?): List<Method> {
    val found = LinkedHashMap<String, Method>()
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        for (method in current.declaredMethods) {
            if (method.name != methodName) continue
            if (returnType != null && method.returnType != returnType) continue
            found.putIfAbsent(method.name + method.parameterTypes.joinToString(",") { it.name }, method)
        }
        current = current.superclass
    }
    return found.values.toList()
}

private fun contextOf(instance: Any?): Context? {
    try {
        val fromInstance = instance?.javaClass
            ?.getDeclaredField("mContext")
            ?.apply { isAccessible = true }
            ?.get(instance)
        if (fromInstance is Context) return fromInstance
    } catch (_: Throwable) {
    }
    return try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (_: Throwable) {
        null
    }
}

private fun applyHideGestureLine(context: Context?, home: ComponentName?) {
    val packageName = home?.packageName ?: return
    val contentResolver = context?.contentResolver ?: return
    val hidden = if (packageName in MIUI_HOME_PACKAGES) 0 else 1
    Thread {
        try {
            Settings.Global.putInt(contentResolver, HIDE_GESTURE_LINE, hidden)
        } catch (t: Throwable) {
            Log.w(TAG, "cannot write $HIDE_GESTURE_LINE: ${t.message}")
        }
    }.start()
}
