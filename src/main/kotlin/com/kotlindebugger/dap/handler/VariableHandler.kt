package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.protocol.Scope
import com.kotlindebugger.dap.protocol.Variable
import kotlinx.serialization.json.*

class ScopesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "scopes"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val frameId = args?.get("frameId")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("frameId is required")

        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        debugSession.selectFrame(frameId)

        val scopes = listOf(
            Scope(
                name = "Locals",
                variablesReference = frameId + 1000,
                expensive = false
            )
        )

        return buildJsonObject {
            put("scopes", Json.encodeToJsonElement(scopes))
        }
    }
}

class VariablesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "variables"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val variablesReference = args?.get("variablesReference")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("variablesReference is required")

        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val frameId = variablesReference - 1000
        debugSession.selectFrame(frameId)

        val variables = debugSession.getLocalVariables().map { varInfo ->
            Variable(
                name = varInfo.name,
                value = varInfo.value,
                type = varInfo.typeName,
                variablesReference = 0
            )
        }

        return buildJsonObject {
            put("variables", Json.encodeToJsonElement(variables))
        }
    }
}
