package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
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

        // 尝试从当前栈帧获取异常信息
        try {
            val frames = thread.frames()
            if (frames.isEmpty()) {
                return buildEmptyExceptionInfo()
            }

            // 查找异常对象
            // 在异常断点命中时，异常对象通常是当前帧的第一个局部变量或在特殊寄存器中
            var exceptionObject: ObjectReference? = null
            
            // 方法1: 尝试从 EventHandler 缓存获取（如果实现了）
            // 方法2: 尝试从栈帧的局部变量中查找 Throwable 类型的对象
            for (frame in frames) {
                try {
                    val visibleVariables = frame.visibleVariables()
                    for (variable in visibleVariables) {
                        val value = frame.getValue(variable)
                        if (value is ObjectReference) {
                            val typeName = value.referenceType().name()
                            if (isThrowableType(typeName)) {
                                exceptionObject = value
                                break
                            }
                        }
                    }
                    if (exceptionObject != null) break
                } catch (e: Exception) {
                    // 某些帧可能没有调试信息
                    Logger.debug("Could not inspect frame: ${e.message}")
                }
            }

            if (exceptionObject == null) {
                // 尝试从第一个帧的 this 对象查找（如果当前在 catch 块中）
                try {
                    val thisObject = frames[0].thisObject()
                    if (thisObject != null && isThrowableType(thisObject.referenceType().name())) {
                        exceptionObject = thisObject
                    }
                } catch (e: Exception) {
                    Logger.debug("Could not get this object: ${e.message}")
                }
            }

            if (exceptionObject != null) {
                return buildExceptionInfo(exceptionObject)
            }

            // 无法找到异常对象，返回基本信息
            return buildEmptyExceptionInfo()

        } catch (e: Exception) {
            Logger.error("Error getting exception info", e)
            return buildEmptyExceptionInfo()
        }
    }

    /**
     * 检查类型是否是 Throwable 的子类
     */
    private fun isThrowableType(typeName: String): Boolean {
        return typeName == "java.lang.Throwable" ||
               typeName == "java.lang.Exception" ||
               typeName == "java.lang.Error" ||
               typeName == "java.lang.RuntimeException" ||
               typeName.endsWith("Exception") ||
               typeName.endsWith("Error")
    }

    /**
     * 构建异常信息响应
     */
    private fun buildExceptionInfo(exception: ObjectReference): JsonObject {
        val typeName = exception.referenceType().name()
        
        // 获取异常消息
        var message: String? = null
        try {
            val messageField = exception.referenceType().fieldByName("detailMessage")
            if (messageField != null) {
                val messageValue = exception.getValue(messageField)
                if (messageValue is StringReference) {
                    message = messageValue.value()
                }
            }
        } catch (e: Exception) {
            Logger.debug("Could not get exception message: ${e.message}")
        }

        // 获取堆栈跟踪
        var stackTrace: String? = null
        try {
            val toStringMethod = exception.referenceType().methodsByName("toString").firstOrNull()
            if (toStringMethod != null) {
                // 注意：调用方法可能需要 VM 支持
                // 这里简化处理，只使用类型名和消息
                stackTrace = null
            }
        } catch (e: Exception) {
            Logger.debug("Could not get stack trace: ${e.message}")
        }

        val breakMode = "always" // 可以是 "never", "always", "unhandled", "userUnhandled"
        val exceptionId = typeName

        return buildJsonObject {
            put("exceptionId", exceptionId)
            put("description", message ?: typeName)
            put("breakMode", breakMode)
            putJsonObject("details") {
                put("message", message)
                put("typeName", typeName)
                if (stackTrace != null) {
                    put("stackTrace", stackTrace)
                }
                put("fullTypeName", typeName)
            }
        }
    }

    /**
     * 构建空的异常信息响应
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
