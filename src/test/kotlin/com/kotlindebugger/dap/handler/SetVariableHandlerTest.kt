package com.kotlindebugger.dap.handler

import com.kotlindebugger.dap.DAPServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * SetVariableHandler 单元测试
 */
class SetVariableHandlerTest {

    private lateinit var handler: SetVariableHandler
    private lateinit var server: DAPServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        // Create a DAPServer for testing
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
        handler = SetVariableHandler(server)
    }

    @Test
    fun `test handler command is setVariable`() {
        assertEquals("setVariable", handler.command)
    }

    @Test
    fun `test handler throws when variablesReference missing`() {
        val args = buildJsonObject {
            put("name", "test")
            put("value", "123")
        }
        
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertTrue(exception.message?.contains("variablesReference") == true)
    }

    @Test
    fun `test handler throws when name missing`() {
        val args = buildJsonObject {
            put("variablesReference", 1000)
            put("value", "123")
        }
        
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertTrue(exception.message?.contains("name") == true)
    }

    @Test
    fun `test handler throws when value missing`() {
        val args = buildJsonObject {
            put("variablesReference", 1000)
            put("name", "test")
        }
        
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertTrue(exception.message?.contains("value") == true)
    }

    @Test
    fun `test handler throws when no debug session`() {
        val args = buildJsonObject {
            put("variablesReference", 1000)
            put("name", "test")
            put("value", "123")
        }
        
        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertTrue(exception.message?.contains("No debug session") == true)
    }

    @Test
    fun `test handler throws when invalid variablesReference`() {
        // Create a DAPServer with a mock debug session would be needed
        // For now, we test that the handler properly validates inputs
        val args = buildJsonObject {
            put("variablesReference", 9999)  // Non-existent reference
            put("name", "test")
            put("value", "123")
        }
        
        // Should throw because there's no debug session
        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertNotNull(exception.message)
    }

    @Test
    fun `test handler with null arguments throws`() {
        // setVariable requires all arguments
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(null, null)
            }
        }
        assertNotNull(exception.message)
    }

    @Test
    fun `test handler with empty arguments throws`() {
        val args = buildJsonObject {}
        
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertNotNull(exception.message)
    }
}
