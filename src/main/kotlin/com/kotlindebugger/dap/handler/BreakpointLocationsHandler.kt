package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*
import java.io.File

/**
 * 处理 breakpointLocations 请求
 *
 * 当客户端请求某个源文件某个行范围内的有效断点位置时触发。
 * 返回该范围内所有可设置断点的行号。
 */
class BreakpointLocationsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "breakpointLocations"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'breakpointLocations' command")

        val source = args?.get("source")?.jsonObject
        val sourcePath = source?.get("path")?.jsonPrimitive?.contentOrNull ?: ""
        val fileName = File(sourcePath).name

        val startLine = args?.get("line")?.jsonPrimitive?.int ?: 1
        val endLine = args?.get("endLine")?.jsonPrimitive?.int ?: startLine

        val debugSession = server.getDebugSession()
        val validLines = if (debugSession != null) {
            debugSession.getBreakpointLocationsForFile(fileName, startLine, endLine)
        } else {
            // 如果没有活动的调试会话，返回请求范围内的所有行
            (startLine..endLine).toList()
        }

        val breakpoints = validLines.map { line ->
            buildJsonObject {
                put("line", line)
                put("column", 1)
            }
        }

        return buildJsonObject {
            put("breakpoints", JsonArray(breakpoints))
        }
    }
}
