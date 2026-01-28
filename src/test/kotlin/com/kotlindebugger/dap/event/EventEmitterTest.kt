package com.kotlindebugger.dap.event

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayOutputStream

/**
 * EventEmitter 单元测试
 */
class EventEmitterTest {

    private lateinit var outputStream: ByteArrayOutputStream
    private lateinit var emitter: EventEmitter
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        outputStream = ByteArrayOutputStream()
        emitter = EventEmitter(outputStream)
    }

    private fun getOutput(): String = outputStream.toString()

    private fun parseEventBody(output: String): kotlinx.serialization.json.JsonElement {
        // Extract JSON from Content-Length format
        val jsonStart = output.indexOf('{')
        val jsonEnd = output.lastIndexOf('}') + 1
        val jsonStr = output.substring(jsonStart, jsonEnd)
        return json.parseToJsonElement(jsonStr)
    }

    // ==================== sendInitialized Tests ====================

    @Test
    fun `test sendInitialized sends correct event`() {
        emitter.sendInitialized()
        val output = getOutput()

        assertTrue(output.contains("Content-Length:"))
        assertTrue(output.contains("\"event\":\"initialized\""))
        assertTrue(output.contains("\"type\":\"event\""))
    }

    @Test
    fun `test sendInitialized includes seq`() {
        emitter.sendInitialized()
        val output = getOutput()

        assertTrue(output.contains("\"seq\":1"))
    }

    // ==================== sendStopped Tests ====================

    @Test
    fun `test sendStopped with breakpoint reason`() {
        emitter.sendStopped("breakpoint", 1)
        val output = getOutput()

        assertTrue(output.contains("\"event\":\"stopped\""))
        assertTrue(output.contains("\"reason\":\"breakpoint\""))
        assertTrue(output.contains("\"threadId\":1"))
    }

    @Test
    fun `test sendStopped with step reason`() {
        emitter.sendStopped("step", 1)
        val output = getOutput()

        assertTrue(output.contains("\"reason\":\"step\""))
    }

    @Test
    fun `test sendStopped with allThreadsStopped`() {
        emitter.sendStopped("breakpoint", 1, allThreadsStopped = true)
        val output = getOutput()

        assertTrue(output.contains("\"allThreadsStopped\":true"))
    }

    @Test
    fun `test sendStopped with hitBreakpointIds`() {
        emitter.sendStopped("breakpoint", 1, hitBreakpointIds = listOf(1, 2, 3))
        val output = getOutput()

        assertTrue(output.contains("\"hitBreakpointIds\""))
        assertTrue(output.contains("1"))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("3"))
    }

    @Test
    fun `test sendStopped without hitBreakpointIds`() {
        emitter.sendStopped("step", 1)
        val output = getOutput()

        // hitBreakpointIds should not be present when null
        // The output should NOT contain the hitBreakpointIds key
        assertFalse(output.contains("hitBreakpointIds"))
    }

    @Test
    fun `test sendStopped with empty hitBreakpointIds`() {
        emitter.sendStopped("breakpoint", 1, hitBreakpointIds = emptyList())
        val output = getOutput()

        // Empty list should not add hitBreakpointIds field (implementation skips empty lists)
        assertFalse(output.contains("hitBreakpointIds"))
    }

    // ==================== sendContinued Tests ====================

    @Test
    fun `test sendContinued sends correct event`() {
        emitter.sendContinued(1)
        val output = getOutput()

        assertTrue(output.contains("\"event\":\"continued\""))
        assertTrue(output.contains("\"threadId\":1"))
        assertTrue(output.contains("\"allThreadsContinued\":true"))
    }

    @Test
    fun `test sendContinued with allThreadsContinued false`() {
        emitter.sendContinued(1, allThreadsContinued = false)
        val output = getOutput()

        assertTrue(output.contains("\"allThreadsContinued\":false"))
    }

    // ==================== sendTerminated Tests ====================

    @Test
    fun `test sendTerminated sends correct event`() {
        emitter.sendTerminated()
        val output = getOutput()

        assertTrue(output.contains("\"event\":\"terminated\""))
        assertTrue(output.contains("\"type\":\"event\""))
    }

    @Test
    fun `test sendTerminated has no body`() {
        emitter.sendTerminated()
        val output = getOutput()

        // Terminated event should not have a body with reason etc.
        assertFalse(output.contains("\"reason\""))
    }

    // ==================== sendExited Tests ====================

    @Test
    fun `test sendExited with exit code 0`() {
        emitter.sendExited(0)
        val output = getOutput()

        assertTrue(output.contains("\"event\":\"exited\""))
        assertTrue(output.contains("\"exitCode\":0"))
    }

    @Test
    fun `test sendExited with non-zero exit code`() {
        emitter.sendExited(1)
        val output = getOutput()

        assertTrue(output.contains("\"exitCode\":1"))
    }

    @Test
    fun `test sendExited with negative exit code`() {
        emitter.sendExited(-1)
        val output = getOutput()

        assertTrue(output.contains("\"exitCode\":-1"))
    }

    // ==================== sendOutput Tests ====================

    @Test
    fun `test sendOutput with console category`() {
        emitter.sendOutput("Hello, World!")
        val output = getOutput()

        assertTrue(output.contains("\"event\":\"output\""))
        assertTrue(output.contains("\"output\":\"Hello, World!\""))
        assertTrue(output.contains("\"category\":\"console\""))
    }

    @Test
    fun `test sendOutput with stdout category`() {
        emitter.sendOutput("Stdout message", "stdout")
        val output = getOutput()

        assertTrue(output.contains("\"category\":\"stdout\""))
    }

    @Test
    fun `test sendOutput with stderr category`() {
        emitter.sendOutput("Error message", "stderr")
        val output = getOutput()

        assertTrue(output.contains("\"category\":\"stderr\""))
    }

    @Test
    fun `test sendOutput with special characters`() {
        emitter.sendOutput("Line1\nLine2\tTabbed")
        val output = getOutput()

        assertTrue(output.contains("\"output\":"))
    }

    // ==================== Sequence Number Tests ====================

    @Test
    fun `test sequence numbers increment`() {
        emitter.sendInitialized()
        val output1 = getOutput()
        assertTrue(output1.contains("\"seq\":1"))

        outputStream.reset()
        emitter.sendTerminated()
        val output2 = getOutput()
        assertTrue(output2.contains("\"seq\":2"))
    }

    @Test
    fun `test multiple events have unique sequence numbers`() {
        emitter.sendInitialized()
        emitter.sendStopped("breakpoint", 1)
        emitter.sendContinued(1)
        emitter.sendTerminated()

        val output = getOutput()
        assertTrue(output.contains("\"seq\":1"))
        assertTrue(output.contains("\"seq\":2"))
        assertTrue(output.contains("\"seq\":3"))
        assertTrue(output.contains("\"seq\":4"))
    }

    // ==================== Content-Length Tests ====================

    @Test
    fun `test output includes Content-Length header`() {
        emitter.sendInitialized()
        val output = getOutput()

        assertTrue(output.startsWith("Content-Length:"))
    }

    @Test
    fun `test Content-Length is correct`() {
        emitter.sendInitialized()
        val output = getOutput()

        // Parse Content-Length
        val contentLengthMatch = Regex("Content-Length: (\\d+)").find(output)
        assertNotNull(contentLengthMatch)
        val contentLength = contentLengthMatch!!.groupValues[1].toInt()

        // Find the JSON part (after \r\n\r\n)
        val jsonStart = output.indexOf("\r\n\r\n") + 4
        val jsonContent = output.substring(jsonStart)

        assertEquals(contentLength, jsonContent.length)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `test empty output message`() {
        emitter.sendOutput("")
        val output = getOutput()

        assertTrue(output.contains("\"output\":\"\""))
    }

    @Test
    fun `test very long output message`() {
        val longMessage = "x".repeat(10000)
        emitter.sendOutput(longMessage)
        val output = getOutput()

        assertTrue(output.contains("\"output\":"))
        assertTrue(output.length > 10000)
    }

    @Test
    fun `test sendStopped with different thread IDs`() {
        emitter.sendStopped("breakpoint", 999)
        val output = getOutput()

        assertTrue(output.contains("\"threadId\":999"))
    }
}
