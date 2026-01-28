package com.kotlindebugger.dap.handler

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * InitializeHandler 单元测试
 */
class InitializeHandlerTest {

    private lateinit var handler: InitializeHandler
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        handler = InitializeHandler()
    }

    @Test
    fun `test handler command is initialize`() {
        assertEquals("initialize", handler.command)
    }

    @Test
    fun `test initialize returns capabilities`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
        assertTrue(result is JsonObject || result.toString().contains("supportsConfigurationDoneRequest"))
    }

    @Test
    fun `test capabilities includes supportsConfigurationDoneRequest`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsConfigurationDoneRequest"))
        assertTrue(resultStr.contains("true"))
    }

    @Test
    fun `test capabilities includes supportsEvaluateForHovers`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsEvaluateForHovers"))
    }

    @Test
    fun `test capabilities includes supportsSetVariable`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsSetVariable"))
    }

    @Test
    fun `test capabilities includes supportsConditionalBreakpoints`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsConditionalBreakpoints"))
    }

    @Test
    fun `test capabilities includes supportsValueFormattingOptions`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsValueFormattingOptions"))
    }

    @Test
    fun `test handler does not require session`() = runBlocking {
        // Initialize handler should work without a debug session
        val result = handler.handle(null, null)
        assertNotNull(result)
    }

    @Test
    fun `test handler ignores arguments`() = runBlocking {
        // Initialize handler should work even with arguments
        val args = json.parseToJsonElement("""
            {
                "clientID": "vscode",
                "adapterID": "kotlin-debug"
            }
        """.trimIndent()).jsonObject
        
        val result = handler.handle(args, null)
        assertNotNull(result)
    }

    @Test
    fun `test capabilities includes supportsFunctionBreakpoints`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsFunctionBreakpoints"))
    }

    @Test
    fun `test capabilities includes supportsStepBack`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsStepBack"))
    }

    @Test
    fun `test capabilities includes supportsRestartFrame`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsRestartFrame"))
    }

    @Test
    fun `test capabilities includes supportsStepInTargetsRequest`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("supportsStepInTargetsRequest"))
    }

    @Test
    fun `test result is valid JSON`() = runBlocking {
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        
        // Verify it can be parsed as JSON
        assertDoesNotThrow {
            json.parseToJsonElement(resultStr)
        }
    }

    @Test
    fun `test handler is idempotent`() = runBlocking {
        val result1 = handler.handle(null, null)
        val result2 = handler.handle(null, null)
        
        // Both calls should return equivalent results
        assertEquals(result1.toString(), result2.toString())
    }
}
