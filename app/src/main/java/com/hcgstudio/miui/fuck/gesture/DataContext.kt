package com.hcgstudio.miui.fuck.gesture

import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData

object DataConst {
    val STRONG_MODE_DATA = PrefsData("strong_mode", true)
    val HIDE_LINE_ON_HOME_SWITCH_DATA = PrefsData("hide_line_on_home_switch", false)
}