package com.hcgstudio.miui.fuck.gesture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.xposed.proxy.YukiHookXposedInitProxy


private const val HIDE_GESTURE_LINE = "hide_gesture_line"

private val MIUI_HOME_PACKAGES = setOf(
    "com.miui.home",
    "com.android.provision",
    "com.mi.android.globallauncher",
    "com.aura.oobe.kddi",
    "com.google.android.setupwizard"
)

@InjectYukiHookWithXposed(modulePackageName = "com.hcgstudio.miui.fuck.gesture")
class MainHook : YukiHookXposedInitProxy {
    override fun onHook() {
        YukiHookAPI.encase {

            loadApp(name = "com.android.systemui") {
                val hideLineOnHomeSwitch = prefs.get(DataConst.HIDE_LINE_ON_HOME_SWITCH_DATA)

                findClass("com.android.systemui.assist.PhoneStateMonitorController").hook {
                    injectMember {
                        method {
                            name = "onDefaultHomeChanged"
                        }
                        replaceUnit {
                            if (hideLineOnHomeSwitch) {
                                applyHideGestureLine(contextOf(instance), args(0).any() as? ComponentName)
                            }
                        }
                    }
                }

                val strongMode = prefs.get(DataConst.STRONG_MODE_DATA)

                //Strong mode hook
                if (strongMode) {
                    findClass("android.provider.MiuiSettings\$Global").hook {
                        injectMember {
                            method {
                                name = "getBoolean"
                                returnType = BooleanType
                            }
                            afterHook {
                                if (args(1).string() == "force_fsg_nav_bar") {
                                    resultTrue()
                                }
                            }
                        }
                    }
                }
            }

            loadApp(name = "com.miui.home") {
                findClass("androidx.preference.Preference").hook {
                    injectMember {
                        method {
                            name = "setIntent"
                            beforeHook {
                                val old = args(0).any() as Intent
                                if (old.action == "com.miui.home.action.navigation_bar_type_settings") {
                                    val intent = Intent()
                                    intent.setClassName(
                                        "com.hcgstudio.miui.fuck.gesture",
                                        "com.hcgstudio.miui.fuck.gesture.SettingsActivity"
                                    )
                                    args(0).set(intent)
                                }
                            }
                        }
                    }
                }

                findClass("com.miui.home.common.utils.BuildConfigUtils").hook {
                    injectMember {
                        method {
                            name = "isUseMiuiHomeAsDefaultHome"
                            returnType = BooleanType
                        }
                        replaceToTrue()
                    }
                }
            }

            loadApp(name = "com.miui.voiceassist") {
                findClass("android.provider.Settings\$Global").hook {
                    injectMember {
                        method {
                            name = "putInt"
                        }
                        beforeHook {
                            if (args(1).string() == "force_fsg_nav_bar") {
                                args(2).set(1)
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun contextOf(instance: Any?): Context? = try {
    instance?.javaClass
        ?.getDeclaredField("mContext")
        ?.apply { isAccessible = true }
        ?.get(instance) as? Context
} catch (_: Throwable) {
    null
}

private fun applyHideGestureLine(context: Context?, home: ComponentName?) {
    val packageName = home?.packageName
    val contentResolver = context?.contentResolver
    if (packageName == null || contentResolver == null) {
        return
    }
    val hide = if (packageName in MIUI_HOME_PACKAGES) 0 else 1
    Thread {
        try {
            Settings.Global.putInt(contentResolver, HIDE_GESTURE_LINE, hide)
        } catch (_: Throwable) {
        }
    }.start()
}