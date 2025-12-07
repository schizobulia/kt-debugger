package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ContinueHandler(private val server: DAPServer) : RequestHandler {
    override val command = "continue"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        debugSession.resume()

        return buildJsonObject {
            put("allThreadsContinued", true)
        }
    }
}

class NextHandler(private val server: DAPServer) : RequestHandler {
    override val command = "next"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        debugSession.stepOver()
        return null
    }
}

class StepInHandler(private val server: DAPServer) : RequestHandler {
    override val command = "stepIn"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        debugSession.stepInto()
        return null
    }
}

class StepOutHandler(private val server: DAPServer) : RequestHandler {
    override val command = "stepOut"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        debugSession.stepOut()
        return null
    }
}

class DisconnectHandler(private val server: DAPServer) : RequestHandler {
    override val command = "disconnect"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
        debugSession?.stop()
        return null
    }
}
