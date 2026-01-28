package com.kotlindebugger.dap.handler

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * SetExceptionBreakpointsHandler 单元测试
 */
class SetExceptionBreakpointsHandlerTest {

    private lateinit var handler: SetExceptionBreakpointsHandler
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        handler = SetExceptionBreakpointsHandler()
    }

    @Test
    fun `test handler command is setExceptionBreakpoints`() {
        assertEquals("setExceptionBreakpoints", handler.command)
    }

    @Test
    fun `test handler returns empty breakpoints list`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
        
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"breakpoints\""))
        assertTrue(resultStr.contains("[]"))
    }

    @Test
    fun `test handler with uncaught filter`() = runBlocking {
        val args = buildJsonObject {
            putJsonArray("filters") {
                add("uncaught")
            }
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
        
        // Handler should still return empty breakpoints list (not yet implemented)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"breakpoints\""))
    }

    @Test
    fun `test handler with caught filter`() = runBlocking {
        val args = buildJsonObject {
            putJsonArray("filters") {
                add("caught")
            }
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
    }

    @Test
    fun `test handler with multiple filters`() = runBlocking {
        val args = buildJsonObject {
            putJsonArray("filters") {
                add("caught")
                add("uncaught")
            }
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
    }

    @Test
    fun `test handler with empty filters array`() = runBlocking {
        val args = buildJsonObject {
            putJsonArray("filters") {}
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
        
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"breakpoints\""))
    }

    @Test
    fun `test handler with no arguments`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
    }

    @Test
    fun `test handler does not require session`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
    }

    @Test
    fun `test handler result is valid JSON`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        
        assertDoesNotThrow {
            json.parseToJsonElement(resultStr)
        }
    }

    @Test
    fun `test handler is idempotent`() = runBlocking {
        val result1 = handler.handle(null, null)
        val result2 = handler.handle(null, null)
        
        assertEquals(result1.toString(), result2.toString())
    }
}
