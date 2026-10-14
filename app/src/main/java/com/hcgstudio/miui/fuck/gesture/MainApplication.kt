package com.hcgstudio.miui.fuck.gesture

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * The modern Xposed API shares module settings through the framework service: hooked
 * processes read them with getRemotePreferences(), so this app has to publish its
 * preferences whenever the framework connects.
 */
class MainApplication : Application() {

    private var localPrefs: SharedPreferences? = null
    private var remotePrefs: SharedPreferences? = null

    // SharedPreferences only holds change listeners weakly, so keep a strong reference.
    private val mirror =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val local = localPrefs
            val remote = remotePrefs
            if (local != null && remote != null && key != null) {
                local.all[key]?.let { value -> remote.edit().also { copy(it, key, value) }.apply() }
            }
        }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)

        val local = PreferenceManager.getDefaultSharedPreferences(this)
        localPrefs = local

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                val remote = service.getRemotePreferences(DataConst.PREFS_NAME)
                remotePrefs = remote
                remote.edit().also { editor ->
                    local.all.forEach { (key, value) -> copy(editor, key, value) }
                }.apply()
                local.registerOnSharedPreferenceChangeListener(mirror)
            }

            override fun onServiceDied(service: XposedService) {
                remotePrefs = null
                local.unregisterOnSharedPreferenceChangeListener(mirror)
            }
        })
    }
}

private fun copy(editor: SharedPreferences.Editor, key: String, value: Any?) {
    when (value) {
        is Boolean -> editor.putBoolean(key, value)
        is String -> editor.putString(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        else -> Unit
    }
}
