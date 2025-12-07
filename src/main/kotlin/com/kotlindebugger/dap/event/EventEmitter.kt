package com.kotlindebugger.dap.event

import com.kotlindebugger.dap.protocol.DAPEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class EventEmitter(private val output: OutputStream) {
    private val seqCounter = AtomicInteger(1)
    private val json = Json { encodeDefaults = true }

    fun sendInitialized() {
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "initialized"
        ))
    }

    fun sendStopped(reason: String, threadId: Int, allThreadsStopped: Boolean = true) {
        send(DAPEvent(
            seq = seqCounter.getAndIncrement(),
            event = "stopped",
            body = buildJsonObject {
                put("reason", reason)
                put("threadId", threadId)
                put("allThreadsStopped", allThreadsStopped)
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
        val content = "Content-Length: ${jsonString.length}\r\n\r\n$jsonString"
        synchronized(output) {
            output.write(content.toByteArray())
            output.flush()
        }
    }
}
