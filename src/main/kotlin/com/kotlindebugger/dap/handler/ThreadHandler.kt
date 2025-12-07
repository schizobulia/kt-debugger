package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.protocol.Thread
import kotlinx.serialization.json.*

class ThreadsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "threads"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val threads = debugSession.getThreads().map { threadInfo ->
            Thread(
                id = threadInfo.id.toInt(),
                name = threadInfo.name
            )
        }

        return buildJsonObject {
            put("threads", Json.encodeToJsonElement(threads))
        }
    }
}
