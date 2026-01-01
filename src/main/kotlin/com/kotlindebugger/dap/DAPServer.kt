package com.kotlindebugger.dap

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.converter.SourcePathResolver
import com.kotlindebugger.dap.event.EventEmitter
import com.kotlindebugger.dap.handler.*
import com.kotlindebugger.dap.protocol.DAPRequest
import com.kotlindebugger.dap.protocol.DAPResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
        dispatcher.register(ConfigurationDoneHandler())
        dispatcher.register(ThreadsHandler(this))
        dispatcher.register(StackTraceHandler(this))
        dispatcher.register(ScopesHandler(this))
        dispatcher.register(VariablesHandler(this))
        dispatcher.register(ContinueHandler(this))
        dispatcher.register(NextHandler(this))
        dispatcher.register(StepInHandler(this))
        dispatcher.register(StepOutHandler(this))
        dispatcher.register(DisconnectHandler(this))
    }

    fun start() {
        try {
            while (true) {
                val message = readMessage() ?: break
                handleMessage(message)
            }
        } catch (e: Exception) {
            System.err.println("DAP Server error: ${e.message}")
            e.printStackTrace()
        }
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
            val request = json.decodeFromString<DAPRequest>(message)

            val body = try {
                dispatcher.dispatch(request.command, request.arguments, debugSession)
            } catch (e: Exception) {
                sendErrorResponse(request, e.message ?: "Unknown error")
                return@runBlocking
            }

            sendResponse(request, body)
        } catch (e: Exception) {
            System.err.println("Failed to handle message: ${e.message}")
            e.printStackTrace()
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
