package com.kotlindebugger.dap.handler

import com.kotlindebugger.dap.DAPServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * SourceHandler 单元测试
 */
class SourceHandlerTest {

    private lateinit var handler: SourceHandler
    private lateinit var server: DAPServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
        handler = SourceHandler(server)
    }

    @Test
    fun `test handler command is source`() {
        assertEquals("source", handler.command)
    }

    @Test
    fun `test handler returns content with null args`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
        
        val resultObj = result.jsonObject
        assertTrue(resultObj.containsKey("content"))
        assertTrue(resultObj.containsKey("mimeType"))
    }

    @Test
    fun `test handler returns empty content for sourceReference 0`() = runBlocking {
        val args = buildJsonObject {
            put("sourceReference", 0)
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        
        assertEquals("", resultObj["content"]?.jsonPrimitive?.content)
        assertEquals("text/x-kotlin", resultObj["mimeType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test handler returns placeholder for non-zero sourceReference`() = runBlocking {
        val args = buildJsonObject {
            put("sourceReference", 123)
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        
        assertTrue(resultObj["content"]?.jsonPrimitive?.content?.contains("123") ?: false)
        assertEquals("text/x-kotlin", resultObj["mimeType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test handler handles nested source object`() = runBlocking {
        val args = buildJsonObject {
            put("source", buildJsonObject {
                put("sourceReference", 456)
            })
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        
        assertTrue(resultObj["content"]?.jsonPrimitive?.content?.contains("456") ?: false)
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
            put("sourceReference", 100)
        }
        
        val result1 = handler.handle(args, null)
        val result2 = handler.handle(args, null)
        
        assertEquals(result1.toString(), result2.toString())
    }
}
