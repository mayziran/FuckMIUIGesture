package com.hcgstudio.miui.fuck.gesture

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val restart = findPreference<Preference>("restart")
            restart?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Runtime.getRuntime().exec("su -c pkill systemui -9")
                Runtime.getRuntime().exec("su -c am force-stop com.miui.home")
                true
            }

            val settings = findPreference<Preference>("settings")
            settings?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent()
                intent.setClassName(
                    "com.miui.home",
                    "com.miui.home.recents.settings.NavigationBarTypeActivity"
                )
                startActivity(intent)
                true
            }
        }
    }
}
