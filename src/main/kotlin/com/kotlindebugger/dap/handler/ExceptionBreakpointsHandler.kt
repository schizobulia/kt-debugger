package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * 处理 setExceptionBreakpoints 请求
 * 
 * VS Code 会在调试会话开始时发送此请求，用于设置异常断点。
 * 目前我们返回一个空的断点列表，表示不支持异常断点。
 */
class SetExceptionBreakpointsHandler : RequestHandler {
    override val command = "setExceptionBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'setExceptionBreakpoints' command")
        
        // 获取请求的过滤器（如 "uncaught", "caught" 等）
        val filters = args?.get("filters")
        Logger.debug("Exception breakpoint filters: $filters")
        
        // 目前返回空的断点列表，表示暂不支持异常断点
        // TODO: 未来可以实现异常断点功能
        return buildJsonObject {
            putJsonArray("breakpoints") {
                // 空列表 - 暂不支持异常断点
            }
        }
    }
}
