package com.kotlindebugger.dap.handler

import com.kotlindebugger.dap.DAPServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * PauseHandler 单元测试
 */
class PauseHandlerTest {

    private lateinit var handler: PauseHandler
    private lateinit var server: DAPServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        // Create a DAPServer for testing
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
        handler = PauseHandler(server)
    }

    @Test
    fun `test handler command is pause`() {
        assertEquals("pause", handler.command)
    }

    @Test
    fun `test handler throws when no debug session`() {
        // PauseHandler requires a debug session
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(null, null)
            }
        }
    }

    @Test
    fun `test handler throws when no debug session with threadId`() {
        // Even with threadId argument, it should throw when no debug session
        val args = json.parseToJsonElement("""
            {
                "threadId": 1
            }
        """.trimIndent()) as kotlinx.serialization.json.JsonObject
        
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
    }

    @Test
    fun `test handler error message is descriptive`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(null, null)
            }
        }
        assertTrue(exception.message?.contains("No debug session") == true)
    }
}
