package com.kotlindebugger.dap.handler

import com.kotlindebugger.dap.DAPServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * CompletionsHandler 单元测试
 */
class CompletionsHandlerTest {

    private lateinit var handler: CompletionsHandler
    private lateinit var server: DAPServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
        handler = CompletionsHandler(server)
    }

    @Test
    fun `test handler command is completions`() {
        assertEquals("completions", handler.command)
    }

    @Test
    fun `test handler returns targets array with null args`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
        
        val resultObj = result.jsonObject
        assertTrue(resultObj.containsKey("targets"))
    }

    @Test
    fun `test handler returns empty targets when no session`() = runBlocking {
        val args = buildJsonObject {
            put("text", "test")
            put("column", 4)
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        val targets = resultObj["targets"]?.jsonArray
        
        assertNotNull(targets)
        // Without a session, returns empty targets
        assertTrue(targets?.isEmpty() ?: true)
    }

    @Test
    fun `test handler handles text input without session`() = runBlocking {
        val args = buildJsonObject {
            put("text", "tr")
            put("column", 2)
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        val targets = resultObj["targets"]?.jsonArray
        
        assertNotNull(targets)
        // Without a session, targets array is empty
        assertEquals(0, targets?.size ?: -1)
    }

    @Test
    fun `test handler with empty text`() = runBlocking {
        val args = buildJsonObject {
            put("text", "")
            put("column", 0)
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        val targets = resultObj["targets"]?.jsonArray
        
        assertNotNull(targets)
        // Without a session, targets array is empty
        assertEquals(0, targets?.size ?: -1)
    }

    @Test
    fun `test handler handles null text prefix`() = runBlocking {
        val args = buildJsonObject {
            put("text", "null")
            put("column", 4)
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        val targets = resultObj["targets"]?.jsonArray
        
        assertNotNull(targets)
        // Without a session, targets array is empty
        assertEquals(0, targets?.size ?: -1)
    }

    @Test
    fun `test handler does not require session`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
    }

    @Test
    fun `test result is valid JSON`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        
        assertDoesNotThrow {
            json.parseToJsonElement(resultStr)
        }
    }

    @Test
    fun `test handler is idempotent`() = runBlocking {
        val args = buildJsonObject {
            put("text", "this")
            put("column", 4)
        }
        
        val result1 = handler.handle(args, null)
        val result2 = handler.handle(args, null)
        
        assertEquals(result1.toString(), result2.toString())
    }

    @Test
    fun `test handler with frameId`() = runBlocking {
        val args = buildJsonObject {
            put("text", "test")
            put("column", 4)
            put("frameId", 0)
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
    }
}
