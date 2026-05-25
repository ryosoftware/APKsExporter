package com.ryosoftware.utilities

import android.annotation.SuppressLint
import android.util.Log
import java.io.BufferedWriter
import java.io.FileWriter

object LogUtilities {

    @SuppressLint("SdCardPath")
    private val LOG_FILE: String? = null

    const val DEBUG_NONE = 0
    const val DEBUG_ERRORS = 1
    const val DEBUG_INFO = 2
    const val DEBUG_ALL = 99

    var tag: String = ""

    var logMode: Int = DEBUG_ALL
        set(value) {
            if (LOG_FILE == null) field = value
        }

    fun initialize(logMode: Int, uncaughtExceptionsHandler: Thread.UncaughtExceptionHandler?) {
        this.logMode = logMode
        if (LOG_FILE != null && uncaughtExceptionsHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionsHandler)
        }
    }

    private fun minimizeClassName(className: String): String {
        val index = className.lastIndexOf('.')
        return if (index < 0) className else className.substring(index + 1)
    }

    private fun logToFile(title: String?, messages: Array<String>?) {
        try {
            BufferedWriter(FileWriter(LOG_FILE, true)).use { writer ->
                if (title != null) writer.write("$title\n")
                messages?.forEach { writer.write("$it\n") }
            }
        } catch (_: Exception) {}
    }

    private fun logToFile(title: String?, e: Throwable) {
        val messages = mutableListOf<String?>()
        var cause: Throwable? = e
        messages.add(cause?.message)
        while (cause != null) {
            cause.stackTrace?.forEach {
                messages.add("  at ${it.className}.${it.methodName} (${it.fileName}:${it.lineNumber})")
            }
            cause = cause.cause
            if (cause != null) messages.add(" Caused by ${cause.message}")
        }
        logToFile(title, messages.filterNotNull().toTypedArray())
    }

    fun show(caller: Any, explanation: String?, e: Throwable) {
        if (logMode >= DEBUG_ERRORS) {
            val callerTag = minimizeClassName(caller.javaClass.name)
            if (explanation != null) Log.e(tag, "$callerTag: $explanation")
            Log.e(tag, "$callerTag: ${e.message}")
            e.printStackTrace()
            if (LOG_FILE != null) logToFile("$callerTag: $explanation", e)
        }
    }

    fun show(caller: Class<*>, explanation: String?, e: Throwable) {
        if (logMode >= DEBUG_ERRORS) {
            val callerTag = minimizeClassName(caller.name)
            if (explanation != null) Log.e(tag, "$callerTag: $explanation")
            Log.e(tag, "$callerTag: ${e.message}")
            e.printStackTrace()
            if (LOG_FILE != null) logToFile("$callerTag: $explanation", e)
        }
    }

    fun show(caller: Any, e: Throwable) = show(caller, null, e)
    fun show(caller: Class<*>, e: Throwable) = show(caller, null, e)

    fun show(caller: Any, description: String) {
        if (logMode >= DEBUG_INFO) {
            Log.d(tag, "${minimizeClassName(caller.javaClass.name)}: $description")
            if (LOG_FILE != null) logToFile(description, null)
        }
    }

    fun show(caller: Class<*>, description: String) {
        if (logMode >= DEBUG_INFO) {
            Log.d(tag, "${minimizeClassName(caller.name)}: $description")
            if (LOG_FILE != null) logToFile(description, null)
        }
    }
}
