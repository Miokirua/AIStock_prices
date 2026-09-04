package com.example.aistock_prices

import android.app.Application

class KRApplication : Application() {

    init {
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        // 主题引擎初始化：读取持久化的主题模式并应用（AUTO 时跟随系统深色）
        ThemeController.init(this)
    }

    companion object {
        lateinit var application: Application
    }
}