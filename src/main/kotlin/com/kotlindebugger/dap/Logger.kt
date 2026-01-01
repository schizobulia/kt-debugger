package com.kotlindebugger.dap

import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 调试器日志系统
 *
 * 用于输出调试信息到stderr和日志文件
 */
object Logger {
    private var debugMode = false
    private val stderr: OutputStream = System.err
    private var logFile: File? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * 设置日志文件路径
     */
    fun setLogFile(path: String) {
        logFile = File(path)
        logFile?.parentFile?.mkdirs()
        info("Logging to file: $path")
    }

    /**
     * 获取日志文件路径
     */
    fun getLogFile(): File? = logFile

    /**
     * 启用调试模式
     */
    fun enableDebugMode() {
        debugMode = true
        info("Debug mode enabled")
    }

    /**
     * 禁用调试模式
     */
    fun disableDebugMode() {
        debugMode = false
    }

    /**
     * 检查是否启用调试模式
     */
    fun isDebugEnabled(): Boolean = debugMode

    /**
     * 输出调试信息
     */
    fun debug(message: String) {
        if (debugMode) {
            log("DEBUG", message)
        }
    }

    /**
     * 输出一般信息
     */
    fun info(message: String) {
        log("INFO", message)
    }

    /**
     * 输出警告信息
     */
    fun warn(message: String) {
        log("WARN", message)
    }

    /**
     * 输出错误信息
     */
    fun error(message: String) {
        log("ERROR", message)
    }

    /**
     * 输出错误信息和堆栈跟踪
     */
    fun error(message: String, throwable: Throwable) {
        log("ERROR", message)
        throwable.printStackTrace(System.err)
        logFile?.appendText("[$timestamp] ERROR $message\n${throwable.stackTraceToString()}\n")
    }

    /**
     * 输出DAP请求
     */
    fun logDAPRequest(command: String, seq: Int, arguments: String?) {
        debug("=== DAP Request ===")
        debug("Command: $command")
        debug("Seq: $seq")
        if (arguments != null) {
            debug("Arguments: $arguments")
        }
    }

    /**
     * 输出DAP响应
     */
    fun logDAPResponse(command: String, seq: Int, requestSeq: Int, success: Boolean, body: String?) {
        debug("=== DAP Response ===")
        debug("Command: $command")
        debug("Seq: $seq, RequestSeq: $requestSeq")
        debug("Success: $success")
        if (body != null) {
            debug("Body: $body")
        }
    }

    /**
     * 输出DAP事件
     */
    fun logDAPEvent(event: String, seq: Int, body: String?) {
        debug("=== DAP Event ===")
        debug("Event: $event")
        debug("Seq: $seq")
        if (body != null) {
            debug("Body: $body")
        }
    }

    /**
     * 格式化并输出日志
     */
    private fun log(level: String, message: String) {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        val logMessage = "[$timestamp] [$level] $message\n"

        // 输出到stderr
        stderr.write(logMessage.toByteArray())
        stderr.flush()

        // 同时输出到文件（如果设置了）
        logFile?.appendText("[$timestamp] [$level] $message\n")
    }

    /**
     * 输出分隔线
     */
    fun separator() {
        debug("========================================")
    }

    private val timestamp: String
        get() = LocalDateTime.now().format(dateFormatter)
}
