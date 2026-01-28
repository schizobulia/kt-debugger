package com.kotlindebugger.dap.handler

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import com.kotlindebugger.core.DebugSession

/**
 * RequestDispatcher 单元测试
 */
class RequestDispatcherTest {

    private lateinit var dispatcher: RequestDispatcher

    @BeforeEach
    fun setup() {
        dispatcher = RequestDispatcher()
    }

    @Test
    fun `test register and hasHandler`() {
        val mockHandler = object : RequestHandler {
            override val command = "testCommand"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? = null
        }

        assertFalse(dispatcher.hasHandler("testCommand"))
        dispatcher.register(mockHandler)
        assertTrue(dispatcher.hasHandler("testCommand"))
    }

    @Test
    fun `test dispatch to registered handler`() = runBlocking {
        val expectedResult = buildJsonObject { put("result", "success") }
        
        val mockHandler = object : RequestHandler {
            override val command = "testCommand"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
                return expectedResult
            }
        }

        dispatcher.register(mockHandler)
        val result = dispatcher.dispatch("testCommand", null, null)
        
        assertEquals(expectedResult, result)
    }

    @Test
    fun `test dispatch unknown command throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dispatcher.dispatch("unknownCommand", null, null)
            }
        }
        assertTrue(exception.message!!.contains("Unknown command"))
        assertTrue(exception.message!!.contains("unknownCommand"))
    }

    @Test
    fun `test dispatch with arguments`() = runBlocking {
        val mockHandler = object : RequestHandler {
            override val command = "testWithArgs"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
                assertNotNull(args)
                assertEquals("value", args!!["key"]?.toString()?.replace("\"", ""))
                return null
            }
        }

        dispatcher.register(mockHandler)
        val args = buildJsonObject { put("key", "value") }
        dispatcher.dispatch("testWithArgs", args, null)
    }

    @Test
    fun `test multiple handlers registered`() {
        val handler1 = object : RequestHandler {
            override val command = "command1"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? = null
        }
        val handler2 = object : RequestHandler {
            override val command = "command2"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? = null
        }

        dispatcher.register(handler1)
        dispatcher.register(handler2)

        assertTrue(dispatcher.hasHandler("command1"))
        assertTrue(dispatcher.hasHandler("command2"))
        assertFalse(dispatcher.hasHandler("command3"))
    }

    @Test
    fun `test handler replacement`() = runBlocking {
        val result1 = buildJsonObject { put("version", 1) }
        val result2 = buildJsonObject { put("version", 2) }

        val handler1 = object : RequestHandler {
            override val command = "testCommand"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement = result1
        }
        val handler2 = object : RequestHandler {
            override val command = "testCommand"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement = result2
        }

        dispatcher.register(handler1)
        assertEquals(result1, dispatcher.dispatch("testCommand", null, null))

        dispatcher.register(handler2)
        assertEquals(result2, dispatcher.dispatch("testCommand", null, null))
    }

    @Test
    fun `test hasHandler for non-existent command`() {
        assertFalse(dispatcher.hasHandler("nonExistentCommand"))
    }

    @Test
    fun `test dispatch handler that throws exception`() {
        val mockHandler = object : RequestHandler {
            override val command = "throwingHandler"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
                throw RuntimeException("Handler error")
            }
        }

        dispatcher.register(mockHandler)

        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking {
                dispatcher.dispatch("throwingHandler", null, null)
            }
        }
        assertEquals("Handler error", exception.message)
    }

    @Test
    fun `test dispatch handler returning null`() = runBlocking {
        val mockHandler = object : RequestHandler {
            override val command = "nullHandler"
            override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? = null
        }

        dispatcher.register(mockHandler)
        val result = dispatcher.dispatch("nullHandler", null, null)
        
        assertNull(result)
    }
}
