package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Handler for the DAP 'pause' request.
 * Pauses execution of the debuggee.
 * 
 * This is mapped to VSCode's debug toolbar pause button.
 */
class PauseHandler(private val server: DAPServer) : RequestHandler {
    override val command = "pause"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        Logger.debug("Handling 'pause' command")
        
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        // Get the threadId from args (optional in DAP spec, but VSCode sends it)
        val threadId = args?.get("threadId")?.jsonPrimitive?.long
        Logger.debug("Pause requested for threadId: $threadId")

        // Clear variable references before pausing
        // This ensures clean state when examining variables after pause
        server.variableReferenceManager.clear()
        Logger.debug("Cleared variable references before pause")

        // Suspend the VM (this will pause all threads)
        debugSession.suspend()
        Logger.debug("Debug session suspended")

        // Return empty object on success (as per DAP spec)
        return buildJsonObject { }
    }
}
