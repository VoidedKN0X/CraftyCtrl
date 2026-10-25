package com.vknxworks.craftyctrl

import android.app.Application

/** Only here so code outside composables can read strings.xml (see [AppStrings]). */
class CraftyCtrlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStrings.context = this
        // Registers with ntfy again (same address) when push from Crafty is on.
        com.vknxworks.craftyctrl.push.Push.start(this)
    }
}
