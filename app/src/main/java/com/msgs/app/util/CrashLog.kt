package com.msgs.app.util

import android.content.Context
import android.util.Log
import com.msgs.app.BuildConfig
import java.io.File
import java.util.Date

/** Writes uncaught crashes to a file the Settings screen can copy. */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "crash.log")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val f = file(app)
                if (f.length() > 256_000) f.writeText("")
                f.appendText("${Date()} build ${BuildConfig.VERSION_CODE} thread ${thread.name}\n${Log.getStackTraceString(error)}\n\n")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()

    fun clear(context: Context) {
        file(context).delete()
    }
}
