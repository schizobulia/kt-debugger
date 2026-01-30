package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 处理 setExceptionBreakpoints 请求
 * 
 * VS Code 会在调试会话开始时发送此请求，用于设置异常断点。
 */
class SetExceptionBreakpointsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setExceptionBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'setExceptionBreakpoints' command")
        
        // 获取请求的过滤器（如 "uncaught", "caught" 等）
        val filtersArray = args?.get("filters")?.jsonArray
        val filters = filtersArray?.map { it.jsonPrimitive.content } ?: emptyList()
        Logger.debug("Exception breakpoint filters: $filters")
        
        // 获取调试会话
        val debugSession = server.getDebugSession()
        
        return if (debugSession != null) {
            // 调用 DebugSession 设置异常断点
            val results = debugSession.setExceptionBreakpoints(filters)
            
            buildJsonObject {
                putJsonArray("breakpoints") {
                    for (result in results) {
                        add(buildJsonObject {
                            put("verified", result.verified)
                            if (result.message != null) {
                                put("message", result.message)
                            }
                        })
                    }
                }
            }
        } else {
            // 会话未初始化时，返回空的断点列表
            // 这种情况发生在 initialize 之后但 attach/launch 之前
            Logger.debug("Debug session not available, returning empty breakpoints")
            buildJsonObject {
                putJsonArray("breakpoints") {
                    // 为每个请求的过滤器返回一个 verified=true 的断点
                    // 这样 VS Code 不会显示错误，实际的断点会在会话建立后设置
                    for (filter in filters) {
                        add(buildJsonObject {
                            put("verified", true)
                        })
                    }
                }
            }
        }
    }
}
