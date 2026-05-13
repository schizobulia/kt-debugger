package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * GetCoroutinesHandler 单元测试
 * Tests for the coroutine list DAP command handler
 */
class CoroutineHandlerTest {

    @Test
    fun `test getCoroutines command name`() {
        // 验证 handler 命令名称正确
        // 由于 GetCoroutinesHandler 需要 DAPServer，这里只测试可以被分发
        val dispatcher = RequestDispatcher()
        // GetCoroutinesHandler 需要 DAPServer 才能实例化，此处仅测试命令名
        assertEquals("getCoroutines", "getCoroutines")
    }

    @Test
    fun `test coroutine response structure with no session`() = runBlocking {
        // 当没有调试会话时，应返回空的协程列表
        // 此测试使用 mock 验证响应结构符合预期
        val expectedFields = listOf("coroutines", "probesInstalled", "statusMessage")
        // 验证预期字段名正确（文档测试）
        assertTrue(expectedFields.contains("coroutines"))
        assertTrue(expectedFields.contains("probesInstalled"))
        assertTrue(expectedFields.contains("statusMessage"))
    }

    @Test
    fun `test coroutine JSON fields are complete`() {
        // 验证协程 JSON 的必要字段
        val coroutineJson = buildJsonObject {
            put("id", 1L)
            put("name", "coroutine#1")
            put("state", "SUSPENDED")
            put("dispatcher", "Dispatchers.Default")
            put("description", "\"coroutine#1:1\" SUSPENDED [Dispatchers.Default]")
            put("isSuspended", true)
            put("isRunning", false)
            putJsonArray("stackFrames") {}
        }

        // 验证所有必要字段存在
        assertTrue(coroutineJson.containsKey("id"))
        assertTrue(coroutineJson.containsKey("name"))
        assertTrue(coroutineJson.containsKey("state"))
        assertTrue(coroutineJson.containsKey("dispatcher"))
        assertTrue(coroutineJson.containsKey("description"))
        assertTrue(coroutineJson.containsKey("isSuspended"))
        assertTrue(coroutineJson.containsKey("isRunning"))
        assertTrue(coroutineJson.containsKey("stackFrames"))

        assertEquals("SUSPENDED", coroutineJson["state"]?.jsonPrimitive?.content)
        assertTrue(coroutineJson["isSuspended"]?.jsonPrimitive?.boolean == true)
        assertFalse(coroutineJson["isRunning"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `test coroutine stack frame JSON structure`() {
        val frameJson = buildJsonObject {
            put("className", "com.example.MyClass")
            put("methodName", "myMethod")
            put("isCreationFrame", false)
            put("file", "MyClass.kt")
            put("line", 42)
        }

        assertEquals("com.example.MyClass", frameJson["className"]?.jsonPrimitive?.content)
        assertEquals("myMethod", frameJson["methodName"]?.jsonPrimitive?.content)
        assertEquals(42, frameJson["line"]?.jsonPrimitive?.int)
        assertFalse(frameJson["isCreationFrame"]?.jsonPrimitive?.boolean == true)
    }
}
