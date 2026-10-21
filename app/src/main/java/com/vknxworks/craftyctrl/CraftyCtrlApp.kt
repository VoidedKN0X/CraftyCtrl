package com.vknxworks.craftyctrl

import android.app.Application

/** Only here so code outside composables can read strings.xml (see [AppStrings]). */
class CraftyCtrlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStrings.context = this
    }
}
