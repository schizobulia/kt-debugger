package com.kotlindebugger.dap.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * DAP 消息类型单元测试
 */
class MessagesTest {

    private val json = Json { encodeDefaults = true }

    // ==================== DAPRequest Tests ====================

    @Test
    fun `test DAPRequest creation`() {
        val request = DAPRequest(
            seq = 1,
            command = "initialize",
            arguments = buildJsonObject {
                put("clientID", "vscode")
            }
        )

        assertEquals(1, request.seq)
        assertEquals("request", request.type)
        assertEquals("initialize", request.command)
        assertNotNull(request.arguments)
    }

    @Test
    fun `test DAPRequest without arguments`() {
        val request = DAPRequest(
            seq = 2,
            command = "threads"
        )

        assertEquals(2, request.seq)
        assertEquals("threads", request.command)
        assertNull(request.arguments)
    }

    @Test
    fun `test DAPRequest serialization`() {
        val request = DAPRequest(
            seq = 1,
            command = "initialize",
            arguments = buildJsonObject {
                put("clientID", "vscode")
            }
        )
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"seq\":1"))
        assertTrue(jsonString.contains("\"type\":\"request\""))
        assertTrue(jsonString.contains("\"command\":\"initialize\""))
        assertTrue(jsonString.contains("\"clientID\":\"vscode\""))
    }

    @Test
    fun `test DAPRequest deserialization`() {
        val jsonString = """
            {
                "seq": 1,
                "type": "request",
                "command": "initialize",
                "arguments": {"clientID": "vscode"}
            }
        """.trimIndent()
        
        val request = json.decodeFromString<DAPRequest>(jsonString)
        
        assertEquals(1, request.seq)
        assertEquals("request", request.type)
        assertEquals("initialize", request.command)
        assertNotNull(request.arguments)
    }

    // ==================== DAPResponse Tests ====================

    @Test
    fun `test DAPResponse success`() {
        val response = DAPResponse(
            seq = 2,
            request_seq = 1,
            success = true,
            command = "initialize",
            body = buildJsonObject {
                put("supportsConfigurationDoneRequest", true)
            }
        )

        assertEquals(2, response.seq)
        assertEquals("response", response.type)
        assertEquals(1, response.request_seq)
        assertTrue(response.success)
        assertEquals("initialize", response.command)
        assertNull(response.message)
        assertNotNull(response.body)
    }

    @Test
    fun `test DAPResponse error`() {
        val response = DAPResponse(
            seq = 3,
            request_seq = 2,
            success = false,
            command = "launch",
            message = "mainClass is required"
        )

        assertFalse(response.success)
        assertEquals("mainClass is required", response.message)
        assertNull(response.body)
    }

    @Test
    fun `test DAPResponse serialization`() {
        val response = DAPResponse(
            seq = 2,
            request_seq = 1,
            success = true,
            command = "initialize"
        )
        val jsonString = json.encodeToString(response)

        assertTrue(jsonString.contains("\"seq\":2"))
        assertTrue(jsonString.contains("\"type\":\"response\""))
        assertTrue(jsonString.contains("\"request_seq\":1"))
        assertTrue(jsonString.contains("\"success\":true"))
        assertTrue(jsonString.contains("\"command\":\"initialize\""))
    }

    @Test
    fun `test DAPResponse deserialization`() {
        val jsonString = """
            {
                "seq": 2,
                "type": "response",
                "request_seq": 1,
                "success": true,
                "command": "initialize",
                "body": {"supportsConfigurationDoneRequest": true}
            }
        """.trimIndent()
        
        val response = json.decodeFromString<DAPResponse>(jsonString)
        
        assertEquals(2, response.seq)
        assertEquals("response", response.type)
        assertEquals(1, response.request_seq)
        assertTrue(response.success)
    }

    // ==================== DAPEvent Tests ====================

    @Test
    fun `test DAPEvent creation`() {
        val event = DAPEvent(
            seq = 1,
            event = "initialized"
        )

        assertEquals(1, event.seq)
        assertEquals("event", event.type)
        assertEquals("initialized", event.event)
        assertNull(event.body)
    }

    @Test
    fun `test DAPEvent with body`() {
        val event = DAPEvent(
            seq = 2,
            event = "stopped",
            body = buildJsonObject {
                put("reason", "breakpoint")
                put("threadId", 1)
            }
        )

        assertEquals("stopped", event.event)
        assertNotNull(event.body)
    }

    @Test
    fun `test DAPEvent serialization`() {
        val event = DAPEvent(
            seq = 1,
            event = "initialized"
        )
        val jsonString = json.encodeToString(event)

        assertTrue(jsonString.contains("\"seq\":1"))
        assertTrue(jsonString.contains("\"type\":\"event\""))
        assertTrue(jsonString.contains("\"event\":\"initialized\""))
    }

    @Test
    fun `test DAPEvent stopped serialization`() {
        val event = DAPEvent(
            seq = 2,
            event = "stopped",
            body = buildJsonObject {
                put("reason", "breakpoint")
                put("threadId", 1)
                put("allThreadsStopped", true)
            }
        )
        val jsonString = json.encodeToString(event)

        assertTrue(jsonString.contains("\"event\":\"stopped\""))
        assertTrue(jsonString.contains("\"reason\":\"breakpoint\""))
        assertTrue(jsonString.contains("\"threadId\":1"))
    }

    @Test
    fun `test DAPEvent terminated`() {
        val event = DAPEvent(
            seq = 3,
            event = "terminated"
        )

        assertEquals("terminated", event.event)
    }

    @Test
    fun `test DAPEvent exited`() {
        val event = DAPEvent(
            seq = 4,
            event = "exited",
            body = buildJsonObject {
                put("exitCode", 0)
            }
        )

        assertEquals("exited", event.event)
        assertNotNull(event.body)
    }

    // ==================== Equality and HashCode Tests ====================

    @Test
    fun `test DAPRequest equality`() {
        val request1 = DAPRequest(seq = 1, command = "threads")
        val request2 = DAPRequest(seq = 1, command = "threads")
        val request3 = DAPRequest(seq = 2, command = "threads")

        assertEquals(request1, request2)
        assertNotEquals(request1, request3)
        assertEquals(request1.hashCode(), request2.hashCode())
    }

    @Test
    fun `test DAPResponse equality`() {
        val response1 = DAPResponse(seq = 1, request_seq = 1, success = true, command = "init")
        val response2 = DAPResponse(seq = 1, request_seq = 1, success = true, command = "init")
        val response3 = DAPResponse(seq = 2, request_seq = 1, success = true, command = "init")

        assertEquals(response1, response2)
        assertNotEquals(response1, response3)
    }

    @Test
    fun `test DAPEvent equality`() {
        val event1 = DAPEvent(seq = 1, event = "initialized")
        val event2 = DAPEvent(seq = 1, event = "initialized")
        val event3 = DAPEvent(seq = 2, event = "stopped")

        assertEquals(event1, event2)
        assertNotEquals(event1, event3)
    }
}
