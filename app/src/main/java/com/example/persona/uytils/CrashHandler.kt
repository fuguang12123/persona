package com.example.persona.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlin.system.exitProcess

/**
 * 全局异常捕获器
 * 拦截 App 崩溃，打印日志，并优雅退出
 */
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {

    private var mContext: Context? = null
    private var mDefaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        mContext = context
        // 获取系统默认的异常处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        // 设置该 CrashHandler 为程序的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (!handleException(ex) && mDefaultHandler != null) {
            // 如果用户没有处理则让系统默认的异常处理器来处理
            mDefaultHandler?.uncaughtException(thread, ex)
        } else {
            try {
                Thread.sleep(3000) // 给 Toast 留点显示时间
            } catch (e: InterruptedException) {
                Log.e(TAG, "error : 异常", e)
            }
            // 退出程序
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }

    /**
     * 自定义错误处理，收集错误信息，发送错误报告等操作均在此完成
     * @return true: 如果处理了该异常信息; otherwise false.
     */
    private fun handleException(ex: Throwable?): Boolean {
        if (ex == null) {
            return false
        }

        // 1. 打印日志到 Logcat (红色 Error 级别)
        Log.e(TAG, "🔥 全局异常捕获 🔥", ex)

        // 2. 使用 Toast 显示异常信息
        Handler(Looper.getMainLooper()).post {
            val msg = "程序出现异常: ${ex.message}"
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show()
        }

        return true
    }

    companion object {
        private const val TAG = "CrashHandler"
        val instance: CrashHandler by lazy { CrashHandler() }
    }
}