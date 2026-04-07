package com.optic.tv.utils

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Writes uncaught crash stacks to app storage and lets the next launch show a Telegram contact hint.
 */
object CrashReportHelper {
    private const val CRASH_FILE = "pending_crash_report.txt"
    const val ADMIN_TELEGRAM_HANDLE = "@Opt1c_gh0st"

    fun install(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = File(application.filesDir, CRASH_FILE)
                val body = buildString {
                    appendLine("Report problems to admin on Telegram: $ADMIN_TELEGRAM_HANDLE")
                    appendLine("t.me/Opt1c_gh0st")
                    appendLine()
                    appendLine(throwable.toString())
                    throwable.stackTrace.take(50).forEach { appendLine(it.toString()) }
                }
                f.writeText(body)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun hasPendingReport(ctx: Context): Boolean = File(ctx.filesDir, CRASH_FILE).exists()

    fun readPendingReport(ctx: Context): String? {
        val f = File(ctx.filesDir, CRASH_FILE)
        if (!f.exists()) return null
        return try {
            f.readText()
        } catch (_: Exception) {
            null
        }
    }

    fun clearReport(ctx: Context) {
        try {
            File(ctx.filesDir, CRASH_FILE).delete()
        } catch (_: Exception) {
        }
    }
}
