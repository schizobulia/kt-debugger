package com.kotlindebugger.dap

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.converter.SourcePathResolver
import com.kotlindebugger.dap.event.EventEmitter
import com.kotlindebugger.dap.handler.*
import com.kotlindebugger.dap.protocol.DAPRequest
import com.kotlindebugger.dap.protocol.DAPResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class DAPServer(
    private val input: InputStream = System.`in`,
    private val output: OutputStream = System.out
) {
    private val dispatcher = RequestDispatcher()
    val eventEmitter = EventEmitter(output)
    val sourcePathResolver = SourcePathResolver()
    val variableReferenceManager = VariableReferenceManager()
    private var debugSession: DebugSession? = null
    private val seqCounter = AtomicInteger(1)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        registerHandlers()
    }

    private fun registerHandlers() {
        dispatcher.register(InitializeHandler())
        dispatcher.register(LaunchHandler(this))
        dispatcher.register(AttachHandler(this))
        dispatcher.register(SetBreakpointsHandler(this))
        dispatcher.register(SetExceptionBreakpointsHandler(this))
        dispatcher.register(ConfigurationDoneHandler(this))
        dispatcher.register(ThreadsHandler(this))
        dispatcher.register(StackTraceHandler(this))
        dispatcher.register(ScopesHandler(this))
        dispatcher.register(VariablesHandler(this))
        dispatcher.register(ContinueHandler(this))
        dispatcher.register(PauseHandler(this))
        dispatcher.register(NextHandler(this))
        dispatcher.register(StepInHandler(this))
        dispatcher.register(StepOutHandler(this))
        dispatcher.register(EvaluateHandler(this))
        dispatcher.register(SetVariableHandler(this))
        dispatcher.register(DisconnectHandler(this))
        dispatcher.register(RedefineClassesHandler(this))
    }

    fun start() {
        Logger.info("DAP Server starting...")
        Logger.separator()

        try {
            while (true) {
                val message = readMessage() ?: break
                handleMessage(message)
            }
        } catch (e: Exception) {
            Logger.error("DAP Server error", e)
        }

        Logger.info("DAP Server stopped")
    }

    private fun readMessage(): String? {
        val headers = mutableMapOf<String, String>()

        while (true) {
            val line = readLine() ?: return null
            if (line.isEmpty()) break

            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                headers[parts[0].trim()] = parts[1].trim()
            }
        }

        val contentLength = headers["Content-Length"]?.toIntOrNull() ?: return null
        val buffer = ByteArray(contentLength)
        var totalRead = 0

        while (totalRead < contentLength) {
            val read = input.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) return null
            totalRead += read
        }

        return String(buffer)
    }

    private fun readLine(): String? {
        val builder = StringBuilder()
        var prev = 0.toChar()

        while (true) {
            val byte = input.read()
            if (byte == -1) return null

            val char = byte.toChar()
            if (char == '\n' && prev == '\r') {
                return builder.substring(0, builder.length - 1)
            }
            builder.append(char)
            prev = char
        }
    }

    private fun handleMessage(message: String) = runBlocking {
        try {
            Logger.debug("Received message: ${message.take(200)}...")
            val request = json.decodeFromString<DAPRequest>(message)

            Logger.logDAPRequest(request.command, request.seq,
                if (request.arguments != null) json.encodeToString(JsonObject.serializer(), request.arguments) else null)

            val body = try {
                Logger.debug("Dispatching command: ${request.command}")
                dispatcher.dispatch(request.command, request.arguments, debugSession)
            } catch (e: Exception) {
                Logger.error("Error handling command: ${request.command}", e)
                sendErrorResponse(request, e.message ?: "Unknown error")
                return@runBlocking
            }

            Logger.debug("Sending response for: ${request.command}")
            sendResponse(request, body)
        } catch (e: Exception) {
            Logger.error("Failed to handle message", e)
        }
    }

    private fun sendResponse(request: DAPRequest, body: kotlinx.serialization.json.JsonElement?) {
        val response = DAPResponse(
            seq = seqCounter.getAndIncrement(),
            request_seq = request.seq,
            success = true,
            command = request.command,
            body = body
        )

        val bodyStr = if (body != null) json.encodeToString(JsonElement.serializer(), body) else null
        Logger.logDAPResponse(response.command, response.seq, request.seq, true, bodyStr)

        sendMessage(response)
    }

    private fun sendErrorResponse(request: DAPRequest, message: String) {
        val response = DAPResponse(
            seq = seqCounter.getAndIncrement(),
            request_seq = request.seq,
            success = false,
            command = request.command,
            message = message
        )

        Logger.logDAPResponse(response.command, response.seq, request.seq, false, message)
        Logger.error("Error response: $message")

        sendMessage(response)
    }

    private fun sendMessage(response: DAPResponse) {
        val jsonString = json.encodeToString(DAPResponse.serializer(), response)
        val content = "Content-Length: ${jsonString.length}\r\n\r\n$jsonString"
        synchronized(output) {
            output.write(content.toByteArray())
            output.flush()
        }
    }

    fun setDebugSession(session: DebugSession) {
        this.debugSession = session
    }

    fun getDebugSession(): DebugSession? = debugSession
}
