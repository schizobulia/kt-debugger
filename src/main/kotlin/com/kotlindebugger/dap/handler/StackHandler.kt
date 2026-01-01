package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.protocol.Source
import com.kotlindebugger.dap.protocol.StackFrame
import kotlinx.serialization.json.*

class StackTraceHandler(private val server: DAPServer) : RequestHandler {
    override val command = "stackTrace"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val stackFrames = debugSession.getStackFrames().mapIndexed { index, frameInfo ->
            // 使用SourcePathResolver解析完整路径
            val resolvedPath = frameInfo.location?.let { loc ->
                server.sourcePathResolver.resolveSourcePath(loc.file)
            }
            
            StackFrame(
                id = index,
                name = "${frameInfo.className}.${frameInfo.methodName}",
                source = frameInfo.location?.let {
                    Source(
                        name = it.file.substringAfterLast('/'),
                        path = resolvedPath
                    )
                },
                line = frameInfo.location?.line ?: 0,
                column = 0,
                presentationHint = if (frameInfo.isInline) "subtle" else "normal"
            )
        }

        return buildJsonObject {
            put("stackFrames", Json.encodeToJsonElement(stackFrames))
            put("totalFrames", stackFrames.size)
        }
    }
}
