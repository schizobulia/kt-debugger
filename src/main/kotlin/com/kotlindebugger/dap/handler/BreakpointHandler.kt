package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.protocol.Breakpoint
import com.kotlindebugger.dap.protocol.Source
import kotlinx.serialization.json.*

class SetBreakpointsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val source = args?.get("source")?.jsonObject
        val sourcePath = source?.get("path")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("source.path is required")

        val breakpoints = args["breakpoints"]?.jsonArray ?: JsonArray(emptyList())

        // 清除该文件的所有断点
        val existingBreakpoints = debugSession.listBreakpoints()
        existingBreakpoints.filterIsInstance<com.kotlindebugger.common.model.Breakpoint.LineBreakpoint>()
            .filter { it.file == sourcePath }.forEach {
                debugSession.removeBreakpoint(it.id)
            }

        // 设置新断点
        val result = breakpoints.map { bp ->
            val line = bp.jsonObject["line"]?.jsonPrimitive?.int
                ?: throw IllegalArgumentException("breakpoint.line is required")

            try {
                val breakpoint = debugSession.addBreakpoint(sourcePath, line)
                Breakpoint(
                    id = breakpoint.id,
                    verified = true,
                    line = line,
                    source = Source(path = sourcePath)
                )
            } catch (e: Exception) {
                Breakpoint(
                    id = -1,
                    verified = false,
                    line = line,
                    source = Source(path = sourcePath),
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
