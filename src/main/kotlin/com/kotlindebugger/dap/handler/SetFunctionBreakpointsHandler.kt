package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*

/**
 * 处理 setFunctionBreakpoints 请求
 *
 * 函数断点（方法断点）在指定方法入口处暂停执行。
 * 支持格式：
 *   "ClassName.methodName"          - 指定类的指定方法
 *   "com.example.ClassName.method"  - 完整限定名
 *   "methodName"                    - 仅方法名（搜索所有已加载的类）
 */
class SetFunctionBreakpointsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setFunctionBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'setFunctionBreakpoints' command")

        val debugSession = server.getDebugSession()
            ?: return buildJsonObject {
                put("breakpoints", JsonArray(emptyList()))
            }

        // 清除所有已有的方法断点
        debugSession.clearMethodBreakpoints()

        val breakpoints = args?.get("breakpoints")?.jsonArray ?: JsonArray(emptyList())
        Logger.debug("Setting ${breakpoints.size} function breakpoints")

        val result = breakpoints.map { bp ->
            val name = bp.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                ?: return@map buildJsonObject {
                    put("verified", false)
                    put("message", "name is required for function breakpoints")
                }

            val condition = bp.jsonObject["condition"]?.jsonPrimitive?.contentOrNull

            try {
                val (className, methodName) = parseFunctionName(name)
                val breakpoint = debugSession.addMethodBreakpoint(className, methodName, condition)
                Logger.debug("Function breakpoint set: $className.$methodName (id=${breakpoint.id})")
                buildJsonObject {
                    put("id", breakpoint.id)
                    put("verified", true)
                    put("message", "Breakpoint set at $className.$methodName")
                }
            } catch (e: Exception) {
                Logger.error("Failed to set function breakpoint '$name': ${e.message}")
                buildJsonObject {
                    put("verified", false)
                    put("message", "Failed to set function breakpoint: ${e.message}")
                }
            }
        }

        return buildJsonObject {
            put("breakpoints", JsonArray(result))
        }
    }

    /**
     * 解析函数名为 (className, methodName)
     *
     * 支持格式：
     *   "ClassName.methodName"          → ("ClassName", "methodName")
     *   "com.example.ClassName.method"  → ("com.example.ClassName", "method")
     *   "methodName"                    → ("*", "methodName")（搜索所有类）
     */
    private fun parseFunctionName(name: String): Pair<String, String> {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot >= 0) {
            val className = name.substring(0, lastDot)
            val methodName = name.substring(lastDot + 1)
            Pair(className, methodName)
        } else {
            // 没有类名，搜索所有类中的该方法
            Pair("*", name)
        }
    }
}
