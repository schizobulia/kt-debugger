package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.sun.jdi.StackFrame
import kotlinx.serialization.json.*

/**
 * 处理 completions 请求
 * 
 * 提供调试控制台的自动补全功能。
 * 根据当前栈帧上下文，提供变量名、方法名等的补全建议。
 */
class CompletionsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "completions"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'completions' command")

        val text = args?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val column = args?.get("column")?.jsonPrimitive?.intOrNull ?: text.length
        val frameId = args?.get("frameId")?.jsonPrimitive?.intOrNull

        Logger.debug("Completion request: text='$text', column=$column, frameId=$frameId")

        val debugSession = server.getDebugSession()
        if (debugSession == null) {
            Logger.debug("No debug session available for completions")
            return buildJsonObject {
                put("targets", JsonArray(emptyList()))
            }
        }

        // 获取补全前缀（光标位置之前的文本部分）
        val prefix = if (column > 0 && column <= text.length) {
            extractPrefix(text.substring(0, column))
        } else {
            extractPrefix(text)
        }

        Logger.debug("Completion prefix: '$prefix'")

        val targets = mutableListOf<JsonObject>()

        try {
            // 获取当前栈帧
            val frame = getStackFrame(debugSession, frameId)
            
            if (frame != null) {
                // 获取局部变量名
                try {
                    val visibleVariables = frame.visibleVariables()
                    for (variable in visibleVariables) {
                        if (prefix.isEmpty() || variable.name().startsWith(prefix)) {
                            targets.add(buildJsonObject {
                                put("label", variable.name())
                                put("type", "variable")
                                put("text", variable.name())
                            })
                        }
                    }
                } catch (e: Exception) {
                    Logger.debug("Could not get visible variables: ${e.message}")
                }

                // 获取 this 对象的字段
                try {
                    val thisObject = frame.thisObject()
                    if (thisObject != null) {
                        val referenceType = thisObject.referenceType()
                        for (field in referenceType.allFields()) {
                            if (prefix.isEmpty() || field.name().startsWith(prefix)) {
                                targets.add(buildJsonObject {
                                    put("label", field.name())
                                    put("type", "field")
                                    put("text", field.name())
                                })
                            }
                        }
                        
                        // 获取方法名
                        for (method in referenceType.allMethods()) {
                            val methodName = method.name()
                            // 跳过构造器和内部方法
                            if (!methodName.startsWith("<") && 
                                (prefix.isEmpty() || methodName.startsWith(prefix))) {
                                targets.add(buildJsonObject {
                                    put("label", "$methodName()")
                                    put("type", "method")
                                    put("text", methodName)
                                })
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.debug("Could not get this object fields: ${e.message}")
                }
            }

            // 添加常用 Kotlin 关键字
            val keywords = listOf("true", "false", "null", "this", "super", "it")
            for (keyword in keywords) {
                if (prefix.isEmpty() || keyword.startsWith(prefix)) {
                    targets.add(buildJsonObject {
                        put("label", keyword)
                        put("type", "keyword")
                        put("text", keyword)
                    })
                }
            }

        } catch (e: Exception) {
            Logger.error("Error generating completions", e)
        }

        // 去重并限制结果数量
        val uniqueTargets = targets.distinctBy { it["label"]?.jsonPrimitive?.content }
            .take(50)

        return buildJsonObject {
            put("targets", JsonArray(uniqueTargets))
        }
    }

    /**
     * 从文本中提取补全前缀
     */
    private fun extractPrefix(text: String): String {
        if (text.isEmpty()) return ""
        
        // 找到最后一个标识符的开始位置
        var endIndex = text.length
        var startIndex = endIndex
        
        while (startIndex > 0) {
            val char = text[startIndex - 1]
            if (char.isLetterOrDigit() || char == '_') {
                startIndex--
            } else {
                break
            }
        }
        
        return text.substring(startIndex, endIndex)
    }

    /**
     * 获取指定的栈帧
     */
    private fun getStackFrame(debugSession: DebugSession, frameId: Int?): StackFrame? {
        val thread = debugSession.getCurrentThread() ?: return null
        val vm = debugSession.getVirtualMachine()
        
        val jdiThread = vm.allThreads().find { it.uniqueID() == thread.id }
            ?: return null

        if (!jdiThread.isSuspended) {
            return null
        }

        val frames = jdiThread.frames()
        val index = frameId ?: 0
        return if (index in frames.indices) frames[index] else null
    }
}
