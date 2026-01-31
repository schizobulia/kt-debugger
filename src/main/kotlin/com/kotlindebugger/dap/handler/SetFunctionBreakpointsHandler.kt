package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*

/**
 * 处理 setFunctionBreakpoints 请求
 * 
 * 由于当前调试器不支持函数断点（supportsFunctionBreakpoints = false），
 * 此处理器返回空的断点列表。
 * 
 * 某些调试客户端可能仍会发送此请求，因此需要正确处理。
 */
class SetFunctionBreakpointsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setFunctionBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'setFunctionBreakpoints' command")

        // 获取请求的函数断点
        val breakpoints = args?.get("breakpoints")?.jsonArray ?: JsonArray(emptyList())
        
        Logger.debug("Function breakpoints requested: ${breakpoints.size} (not supported)")

        // 返回所有断点都未验证，因为不支持函数断点
        val result = breakpoints.map {
            buildJsonObject {
                put("verified", false)
                put("message", "Function breakpoints are not supported")
            }
        }

        return buildJsonObject {
            put("breakpoints", JsonArray(result))
        }
    }
}
