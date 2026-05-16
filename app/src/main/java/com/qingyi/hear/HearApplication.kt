package com.qingyi.hear

import android.app.Application

class HearApplication : Application() {
    lateinit var container: HearContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = HearContainer(this)
    }
}
