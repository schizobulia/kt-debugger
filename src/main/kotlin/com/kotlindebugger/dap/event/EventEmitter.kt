package com.kotlindebugger.dap.event

import com.kotlindebugger.dap.Logger
import com.kotlindebugger.dap.protocol.DAPEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class EventEmitter(private val output: OutputStream) {
    private val seqCounter = AtomicInteger(1)
    private val json = Json { encodeDefaults = true }

    fun sendInitialized() {
        Logger.info("Sending 'initialized' event to VSCode")
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "initialized"
        ))
    }

    fun sendStopped(reason: String, threadId: Int, allThreadsStopped: Boolean = true, hitBreakpointIds: List<Int>? = null, description: String? = null, text: String? = null) {
        Logger.info("Sending 'stopped' event: reason=$reason, threadId=$threadId, hitBreakpointIds=$hitBreakpointIds, description=$description")
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "stopped",
            body = buildJsonObject {
                put("reason", reason)
                put("threadId", threadId)
                put("allThreadsStopped", allThreadsStopped)
                if (hitBreakpointIds != null && hitBreakpointIds.isNotEmpty()) {
                    putJsonArray("hitBreakpointIds") {
                        hitBreakpointIds.forEach { add(JsonPrimitive(it)) }
                    }
                }
                if (description != null) {
                    put("description", description)
                }
                if (text != null) {
                    put("text", text)
                }
            }
        ))
    }

    fun sendContinued(threadId: Int, allThreadsContinued: Boolean = true) {
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "continued",
            body = buildJsonObject {
                put("threadId", threadId)
                put("allThreadsContinued", allThreadsContinued)
            }
        ))
    }

    fun sendTerminated() {
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "terminated"
        ))
    }

    fun sendExited(exitCode: Int) {
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "exited",
            body = buildJsonObject {
                put("exitCode", exitCode)
            }
        ))
    }

    fun sendOutput(output: String, category: String = "console") {
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "output",
            body = buildJsonObject {
                put("output", output)
                put("category", category)
            }
        ))
    }

    private fun send(event: DAPEvent) {
        val jsonString = json.encodeToString(event)
        Logger.debug("=== DAP Event ===")
        Logger.debug("Event: ${event.event}")
        Logger.debug("Seq: ${event.seq}")
        Logger.debug("Body: $jsonString")
        val content = "Content-Length: ${jsonString.length}\r\n\r\n$jsonString"
        synchronized(output) {
            output.write(content.toByteArray())
            output.flush()
        }
    }
}
