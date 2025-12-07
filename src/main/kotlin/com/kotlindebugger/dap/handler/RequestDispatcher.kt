package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class RequestDispatcher {
    private val handlers = mutableMapOf<String, RequestHandler>()

    fun register(handler: RequestHandler) {
        handlers[handler.command] = handler
    }

    suspend fun dispatch(command: String, args: JsonObject?, session: DebugSession?): JsonElement? {
        val handler = handlers[command] ?: throw IllegalArgumentException("Unknown command: $command")
        return handler.handle(args, session)
    }

    fun hasHandler(command: String): Boolean = handlers.containsKey(command)
}
