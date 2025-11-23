package com.example.persona // 替换为你的包名

import android.app.Application
import com.example.persona.utils.CrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    // 目前不需要内容
    override fun onCreate() {
        super.onCreate()

        // 初始化全局异常捕获
        CrashHandler.instance.init(this)
    }
}