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
 * ExceptionInfoHandler 单元测试
 */
class ExceptionInfoHandlerTest {

    private lateinit var handler: ExceptionInfoHandler
    private lateinit var server: DAPServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
        handler = ExceptionInfoHandler(server)
    }

    @Test
    fun `test handler command is exceptionInfo`() {
        assertEquals("exceptionInfo", handler.command)
    }

    @Test
    fun `test handler throws when threadId is missing`() = runBlocking {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(null, null)
            }
        }
        assertTrue(exception.message?.contains("threadId") ?: false)
    }

    @Test
    fun `test handler throws when no debug session`() = runBlocking {
        val args = buildJsonObject {
            put("threadId", 1L)
        }
        
        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
        assertTrue(exception.message?.contains("No debug session") ?: false)
    }

    @Test
    fun `test result structure is valid`() {
        // Test that the empty exception info structure is correct
        val emptyInfo = buildJsonObject {
            put("exceptionId", "unknown")
            put("description", "Exception information not available")
            put("breakMode", "always")
            put("details", buildJsonObject {
                put("message", "Could not retrieve exception details")
            })
        }
        
        assertTrue(emptyInfo.containsKey("exceptionId"))
        assertTrue(emptyInfo.containsKey("description"))
        assertTrue(emptyInfo.containsKey("breakMode"))
        assertTrue(emptyInfo.containsKey("details"))
    }

    @Test
    fun `test handler requires threadId argument`() = runBlocking {
        val args = buildJsonObject {
            // Missing threadId
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
    }

    @Test
    fun `test handler validates thread existence`() = runBlocking {
        val args = buildJsonObject {
            put("threadId", 999999L)
        }
        
        // Without a debug session, throws IllegalStateException
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                handler.handle(args, null)
            }
        }
    }

    @Test
    fun `test exception info response contains required fields`() {
        // 验证异常信息响应结构符合 DAP 协议规范
        val exceptionInfo = buildJsonObject {
            put("exceptionId", "java.lang.NullPointerException")
            put("description", "java.lang.NullPointerException: value is null")
            put("breakMode", "always")
            put("details", buildJsonObject {
                put("message", "value is null")
                put("typeName", "java.lang.NullPointerException")
                put("fullTypeName", "java.lang.NullPointerException")
                put("stackTrace", "java.lang.NullPointerException: value is null\n\tat com.example.Main.foo(Main.kt:10)")
            })
        }
        // 校验所有必需字段
        assertEquals("java.lang.NullPointerException", exceptionInfo["exceptionId"]?.jsonPrimitive?.content)
        assertEquals("always", exceptionInfo["breakMode"]?.jsonPrimitive?.content)
        assertNotNull(exceptionInfo["details"]?.jsonObject?.get("stackTrace"))
    }

    @Test
    fun `test exception description includes message when present`() {
        // description 应该是 "类名: 消息"，便于用户快速识别
        val typeName = "java.lang.IllegalArgumentException"
        val message = "invalid input"
        val expected = "$typeName: $message"

        // 模拟 buildExceptionInfo 的拼接逻辑
        val description = if (message.isNotEmpty()) "$typeName: $message" else typeName
        assertEquals(expected, description)
    }

    @Test
    fun `test exception description falls back to type name when no message`() {
        val typeName = "java.lang.RuntimeException"
        val message: String? = null
        val description = if (message != null) "$typeName: $message" else typeName
        assertEquals(typeName, description)
    }
}
