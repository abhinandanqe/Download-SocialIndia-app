package com.example.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val MAX_STACK_TRACE_CHARS = 48_000

    const val EXTRA_EXCEPTION_NAME = "extra_exception_name"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_FILE_NAME = "extra_file_name"
    const val EXTRA_LINE_NUMBER = "extra_line_number"
    const val EXTRA_METHOD_LOCATION = "extra_method_location"
    const val EXTRA_THREAD_NAME = "extra_thread_name"
    const val EXTRA_STACK_TRACE = "extra_stack_trace"

    private val handlingCrash = AtomicBoolean(false)

    data class CrashInfo(
        val exceptionName: String,
        val message: String,
        val fileName: String,
        val lineNumber: Int,
        val methodLocation: String,
        val threadName: String,
        val stackTrace: String
    ) {
        fun fileAndLine(): String = if (lineNumber > 0) "$fileName:$lineNumber" else fileName

        fun toCopyableText(): String = buildString {
            appendLine("=== APP CRASH REPORT ===")
            appendLine("Exception: $exceptionName")
            appendLine("Message: $message")
            appendLine("Location: ${fileAndLine()}")
            if (methodLocation.isNotBlank()) {
                appendLine("Method: $methodLocation")
            }
            appendLine("Thread: $threadName")
            appendLine()
            appendLine("--- Full Stack Trace ---")
            append(stackTrace)
        }
    }

    fun isCrashProcess(context: Context): Boolean {
        val processName = getCurrentProcessName(context)
        return processName.endsWith(":crash")
    }

    private fun getCurrentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()?.let { return it }
        }
        return try {
            File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n', '\r')
        } catch (_: Throwable) {
            context.packageName
        }
    }

    fun extractCrashInfo(thread: Thread, throwable: Throwable): CrashInfo {
        val rootCause = findRootCause(throwable)
        val exceptionName = if (rootCause !== throwable) {
            "${rootCause.javaClass.name} (wrapped in ${throwable.javaClass.name})"
        } else {
            throwable.javaClass.name
        }
        val message = rootCause.localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?: rootCause.message?.takeIf { it.isNotBlank() }
            ?: throwable.localizedMessage?.takeIf { it.isNotBlank() }
            ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: "(No error message)"

        val primaryFrame = findPrimaryStackFrame(rootCause, throwable)
        val fileName = primaryFrame?.fileName?.takeIf { it.isNotBlank() } ?: "Unknown file"
        val lineNumber = primaryFrame?.lineNumber ?: -1
        val methodLocation = primaryFrame?.let { "${it.className}.${it.methodName}" } ?: ""

        val rawStackTrace = try {
            StringWriter().also { sw ->
                PrintWriter(sw).use { pw -> throwable.printStackTrace(pw) }
            }.toString()
        } catch (_: Throwable) {
            Log.getStackTraceString(throwable)
        }
        val stackTrace = if (rawStackTrace.length > MAX_STACK_TRACE_CHARS) {
            rawStackTrace.take(MAX_STACK_TRACE_CHARS) + "\n... [truncated]"
        } else {
            rawStackTrace
        }

        return CrashInfo(
            exceptionName = exceptionName,
            message = message,
            fileName = fileName,
            lineNumber = lineNumber,
            methodLocation = methodLocation,
            threadName = thread.name,
            stackTrace = stackTrace
        )
    }

    private fun findRootCause(throwable: Throwable): Throwable {
        var current = throwable
        val visited = HashSet<Throwable>()
        while (current.cause != null && current.cause !== current && visited.add(current)) {
            current = current.cause!!
        }
        return current
    }

    private fun findPrimaryStackFrame(rootCause: Throwable, outer: Throwable): StackTraceElement? {
        val allFrames = buildList {
            addAll(rootCause.stackTrace)
            if (outer !== rootCause) {
                addAll(outer.stackTrace)
            }
        }
        return allFrames.firstOrNull { frame ->
            (frame.className.startsWith("com.example.") || frame.className.startsWith("com.socialindia.")) &&
                frame.lineNumber > 0
        } ?: allFrames.firstOrNull { it.lineNumber > 0 }
            ?: allFrames.firstOrNull()
    }

    /**
     * Launches [CrashActivity] in the isolated `:crash` process with full crash details,
     * then terminates the crashed process cleanly. Returns true if handled.
     */
    fun launchCrashScreen(context: Context, thread: Thread, throwable: Throwable): Boolean {
        if ("robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) {
            return false
        }
        if (!handlingCrash.compareAndSet(false, true)) {
            return false
        }
        return try {
            val info = extractCrashInfo(thread, throwable)
            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(EXTRA_EXCEPTION_NAME, info.exceptionName)
                putExtra(EXTRA_MESSAGE, info.message)
                putExtra(EXTRA_FILE_NAME, info.fileName)
                putExtra(EXTRA_LINE_NUMBER, info.lineNumber)
                putExtra(EXTRA_METHOD_LOCATION, info.methodLocation)
                putExtra(EXTRA_THREAD_NAME, info.threadName)
                putExtra(EXTRA_STACK_TRACE, info.stackTrace)
            }
            context.startActivity(intent)
            Process.killProcess(Process.myPid())
            exitProcess(10)
            @Suppress("UNREACHABLE_CODE")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch CrashActivity", t)
            false
        }
    }
}
