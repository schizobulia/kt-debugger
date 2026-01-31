package com.kotlindebugger.dap.handler

import com.kotlindebugger.dap.DAPServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * SetFunctionBreakpointsHandler 单元测试
 */
class SetFunctionBreakpointsHandlerTest {

    private lateinit var handler: SetFunctionBreakpointsHandler
    private lateinit var server: DAPServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
        handler = SetFunctionBreakpointsHandler(server)
    }

    @Test
    fun `test handler command is setFunctionBreakpoints`() {
        assertEquals("setFunctionBreakpoints", handler.command)
    }

    @Test
    fun `test handler returns empty breakpoints with null args`() = runBlocking {
        val result = handler.handle(null, null)
        assertNotNull(result)
        
        val resultObj = result.jsonObject
        assertTrue(resultObj.containsKey("breakpoints"))
        assertEquals(0, resultObj["breakpoints"]?.jsonArray?.size)
    }

    @Test
    fun `test handler returns empty breakpoints with empty array`() = runBlocking {
        val args = buildJsonObject {
            putJsonArray("breakpoints") {}
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        
        assertEquals(0, resultObj["breakpoints"]?.jsonArray?.size)
    }

    @Test
    fun `test handler returns unverified breakpoints for function breakpoints`() = runBlocking {
        val args = buildJsonObject {
            putJsonArray("breakpoints") {
                add(buildJsonObject {
                    put("name", JsonPrimitive("testFunction"))
                })
                add(buildJsonObject {
                    put("name", JsonPrimitive("anotherFunction"))
                })
            }
        }
        
        val result = handler.handle(args, null)
        val resultObj = result.jsonObject
        val breakpoints = resultObj["breakpoints"]?.jsonArray
        
        assertNotNull(breakpoints)
        assertEquals(2, breakpoints?.size)
        
        // All breakpoints should be unverified
        breakpoints?.forEach { bp ->
            assertEquals(false, bp.jsonObject["verified"]?.jsonPrimitive?.boolean)
            assertTrue(bp.jsonObject["message"]?.jsonPrimitive?.content?.contains("not supported") ?: false)
        }
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
            putJsonArray("breakpoints") {
                add(buildJsonObject {
                    put("name", JsonPrimitive("myFunction"))
                })
            }
        }
        
        val result1 = handler.handle(args, null)
        val result2 = handler.handle(args, null)
        
        assertEquals(result1.toString(), result2.toString())
    }
}
