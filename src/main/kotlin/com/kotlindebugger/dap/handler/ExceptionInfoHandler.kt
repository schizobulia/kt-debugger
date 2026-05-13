package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.sun.jdi.ArrayReference
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import kotlinx.serialization.json.*

/**
 * 处理 exceptionInfo 请求
 * 
 * 获取异常的详细信息，当调试器在异常处暂停时，
 * 客户端可以使用此请求获取异常的类型、消息和堆栈跟踪。
 */
class ExceptionInfoHandler(private val server: DAPServer) : RequestHandler {
    override val command = "exceptionInfo"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'exceptionInfo' command")

        val threadId = args?.get("threadId")?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("threadId is required")

        Logger.debug("Exception info request for thread: $threadId")

        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val vm = debugSession.getVirtualMachine()
        val thread = vm.allThreads().find { it.uniqueID() == threadId }
            ?: throw IllegalArgumentException("Thread not found: $threadId")

        if (!thread.isSuspended) {
            throw IllegalStateException("Thread is not suspended")
        }

        return try {
            // 优先使用 EventHandler 缓存的异常对象（最可靠，无需扫描局部变量）
            val cachedException = debugSession.getLastExceptionObject()
            if (cachedException != null) {
                buildExceptionInfo(cachedException, thread)
            } else {
                // 回退：扫描栈帧局部变量查找 Throwable
                val fallback = findExceptionInThread(thread)
                if (fallback != null) buildExceptionInfo(fallback, thread)
                else buildEmptyExceptionInfo()
            }
        } catch (e: Exception) {
            Logger.error("Error getting exception info", e)
            buildEmptyExceptionInfo()
        }
    }

    /**
     * 在线程栈帧的局部变量中查找 Throwable 对象（回退策略）
     */
    private fun findExceptionInThread(thread: ThreadReference): ObjectReference? {
        val frames = try { thread.frames() } catch (e: Exception) { return null }
        for (frame in frames) {
            try {
                for (variable in frame.visibleVariables()) {
                    val value = frame.getValue(variable)
                    if (value is ObjectReference && isThrowableType(value.referenceType().name())) {
                        return value
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 检查类型名是否属于 Throwable 体系
     */
    private fun isThrowableType(typeName: String): Boolean =
        typeName == "java.lang.Throwable" ||
        typeName == "java.lang.Exception" ||
        typeName == "java.lang.Error" ||
        typeName == "java.lang.RuntimeException" ||
        typeName.endsWith("Exception") ||
        typeName.endsWith("Error")

    /**
     * 构建完整的异常信息响应
     * @param exception 异常对象引用
     * @param thread 当前暂停的线程（用于获取栈帧作为回退）
     */
    private fun buildExceptionInfo(exception: ObjectReference, thread: ThreadReference): JsonObject {
        val typeName = exception.referenceType().name()
        val message = getExceptionMessage(exception)
        val stackTraceStr = buildStackTrace(exception, thread, typeName, message)
        val causeStr = buildCauseChain(exception)
        val description = if (message != null) "$typeName: $message" else typeName

        return buildJsonObject {
            put("exceptionId", typeName)
            put("description", description)
            put("breakMode", "always")
            putJsonObject("details") {
                put("message", message ?: "")
                put("typeName", typeName)
                put("fullTypeName", typeName)
                put("stackTrace", stackTraceStr)
                if (causeStr != null) {
                    put("innerException", JsonArray(listOf(buildJsonObject {
                        put("message", causeStr)
                    })))
                }
            }
        }
    }

    /**
     * 读取异常的 detailMessage 字段（纯字段读取，不调用方法）
     */
    private fun getExceptionMessage(exception: ObjectReference): String? {
        return try {
            val field = exception.referenceType().fieldByName("detailMessage") ?: return null
            (exception.getValue(field) as? StringReference)?.value()
        } catch (e: Exception) {
            Logger.debug("Could not get exception message: ${e.message}")
            null
        }
    }

    /**
     * 构建格式化的堆栈跟踪字符串。
     * 优先读取异常对象的 stackTrace 字段（StackTraceElement[]），
     * 若无法读取则回退到线程当前的 JDI 栈帧。
     */
    private fun buildStackTrace(
        exception: ObjectReference,
        thread: ThreadReference,
        typeName: String,
        message: String?
    ): String {
        val sb = StringBuilder()
        sb.append(typeName)
        if (message != null) sb.append(": ").append(message)
        sb.append("\n")

        // 先尝试读取 Throwable.stackTrace 字段（StackTraceElement[]）
        val frames = readStackTraceField(exception)
        if (frames != null && frames.isNotEmpty()) {
            frames.forEach { sb.append("\tat ").append(it).append("\n") }
        } else {
            // 回退：使用线程当前 JDI 栈帧
            try {
                for (jdiFrame in thread.frames()) {
                    val loc = jdiFrame.location()
                    val clazz = loc.declaringType().name()
                    val method = loc.method().name()
                    val file = try { loc.sourceName() } catch (_: Exception) { "Unknown" }
                    val line = try { loc.lineNumber() } catch (_: Exception) { -1 }
                    val lineStr = if (line >= 0) ":$line" else ""
                    sb.append("\tat $clazz.$method($file$lineStr)\n")
                }
            } catch (e: Exception) {
                Logger.debug("Could not read thread frames: ${e.message}")
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * 读取 Throwable.stackTrace 字段（StackTraceElement[]）并格式化为字符串列表。
     * 全部通过 JDI 字段访问实现，不调用任何 JVM 方法，避免死锁风险。
     */
    private fun readStackTraceField(exception: ObjectReference): List<String>? {
        return try {
            val stackTraceField = exception.referenceType().fieldByName("stackTrace") ?: return null
            val arrayRef = exception.getValue(stackTraceField) as? ArrayReference ?: return null

            arrayRef.values.mapNotNull { elem ->
                if (elem !is ObjectReference) return@mapNotNull null
                val steType = elem.referenceType()

                fun str(name: String): String? = try {
                    (elem.getValue(steType.fieldByName(name)) as? StringReference)?.value()
                } catch (_: Exception) { null }

                fun int(name: String): Int = try {
                    (elem.getValue(steType.fieldByName(name)) as? IntegerValue)?.value() ?: -1
                } catch (_: Exception) { -1 }

                val clazz = str("declaringClass") ?: return@mapNotNull null
                val method = str("methodName") ?: "<unknown>"
                val file = str("fileName") ?: "Unknown Source"
                val line = int("lineNumber")
                val lineStr = if (line >= 0) ":$line" else ""
                "$clazz.$method($file$lineStr)"
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Logger.debug("Could not read stackTrace field: ${e.message}")
            null
        }
    }

    /**
     * 构建 cause 链描述（只取第一层，避免递归过深）
     */
    private fun buildCauseChain(exception: ObjectReference): String? {
        return try {
            val causeField = exception.referenceType().fieldByName("cause") ?: return null
            val cause = exception.getValue(causeField) as? ObjectReference ?: return null
            // cause == this 表示未设置 cause，跳过
            if (cause.uniqueID() == exception.uniqueID()) return null
            val typeName = cause.referenceType().name()
            val message = getExceptionMessage(cause)
            if (message != null) "$typeName: $message" else typeName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 构建空的异常信息响应（无法获取异常对象时的兜底）
     */
    private fun buildEmptyExceptionInfo(): JsonObject {
        return buildJsonObject {
            put("exceptionId", "unknown")
            put("description", "Exception information not available")
            put("breakMode", "always")
            putJsonObject("details") {
                put("message", "Could not retrieve exception details")
            }
        }
    }
}
