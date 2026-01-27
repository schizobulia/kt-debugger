package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.protocol.Breakpoint
import com.kotlindebugger.dap.protocol.Source
import kotlinx.serialization.json.*
import java.io.File

class SetBreakpointsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val source = args?.get("source")?.jsonObject
        val sourcePath = source?.get("path")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("source.path is required")

        // 提取文件名用于断点匹配
        val fileName = File(sourcePath).name
        
        val breakpoints = args["breakpoints"]?.jsonArray ?: JsonArray(emptyList())

        // 清除该文件的所有断点（按文件名匹配）
        val existingBreakpoints = debugSession.listBreakpoints()
        existingBreakpoints.filterIsInstance<com.kotlindebugger.common.model.Breakpoint.LineBreakpoint>()
            .filter { 
                it.file == sourcePath || 
                it.file == fileName || 
                File(it.file).name == fileName 
            }.forEach {
                debugSession.removeBreakpoint(it.id)
            }

        // 设置新断点（使用文件名以便JDI匹配）
        val result = breakpoints.map { bp ->
            val line = bp.jsonObject["line"]?.jsonPrimitive?.int
                ?: throw IllegalArgumentException("breakpoint.line is required")
            
            // 解析条件表达式（VSCode DAP协议）
            val condition = bp.jsonObject["condition"]?.jsonPrimitive?.contentOrNull

            try {
                // 使用文件名进行JDI断点设置，传递条件表达式
                val breakpoint = debugSession.addBreakpoint(fileName, line, condition)
                Breakpoint(
                    id = breakpoint.id,
                    verified = true,
                    line = line,
                    source = Source(name = fileName, path = sourcePath)
                )
            } catch (e: Exception) {
                Breakpoint(
                    id = -1,
                    verified = false,
                    line = line,
                    source = Source(name = fileName, path = sourcePath),
                    message = e.message
                )
            }
        }

        return buildJsonObject {
            put("breakpoints", Json.encodeToJsonElement(result))
        }
    }
}

class ConfigurationDoneHandler : RequestHandler {
    override val command = "configurationDone"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        return null
    }
}
