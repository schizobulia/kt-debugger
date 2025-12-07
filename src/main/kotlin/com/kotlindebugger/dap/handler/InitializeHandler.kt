package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.protocol.Capabilities
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json

class InitializeHandler : RequestHandler {
    override val command = "initialize"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val capabilities = Capabilities(
            supportsConfigurationDoneRequest = true,
            supportsFunctionBreakpoints = false,
            supportsConditionalBreakpoints = false,
            supportsEvaluateForHovers = false,
            supportsStepBack = false,
            supportsSetVariable = false,
            supportsRestartFrame = false,
            supportsStepInTargetsRequest = false
        )
        return Json.encodeToJsonElement(capabilities)
    }
}
