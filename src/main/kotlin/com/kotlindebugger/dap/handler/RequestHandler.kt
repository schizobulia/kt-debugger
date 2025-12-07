package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface RequestHandler {
    val command: String
    suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement?
}
