package com.kotlindebugger.dap

import com.kotlindebugger.dap.handler.*
import com.kotlindebugger.dap.protocol.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * DAP 协议集成测试
 * 
 * 测试 DAP 服务器和客户端之间的完整交互流程。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DAPIntegrationTest {

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    // ==================== Initialize-Launch Sequence Tests ====================

    @Test
    fun `test initialize handler returns capabilities`() = runBlocking {
        val handler = InitializeHandler()
        val result = handler.handle(null, null)
        
        assertNotNull(result)
        val resultStr = result.toString()
        
        // 验证必要的功能
        assertTrue(resultStr.contains("supportsConfigurationDoneRequest"))
        assertTrue(resultStr.contains("supportsEvaluateForHovers"))
        assertTrue(resultStr.contains("supportsSetVariable"))
    }

    @Test
    fun `test initialize handler with client info`() = runBlocking {
        val handler = InitializeHandler()
        val args = buildJsonObject {
            put("clientID", "vscode")
            put("clientName", "Visual Studio Code")
            put("adapterID", "kotlin-debug")
            put("pathFormat", "path")
            put("linesStartAt1", true)
            put("columnsStartAt1", true)
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
    }

    // ==================== Breakpoint Handler Tests ====================

    @Test
    fun `test setExceptionBreakpoints returns empty breakpoints`() = runBlocking {
        val handler = SetExceptionBreakpointsHandler()
        val args = buildJsonObject {
            putJsonArray("filters") {
                add("uncaught")
            }
        }
        
        val result = handler.handle(args, null)
        assertNotNull(result)
        
        val resultObj = json.parseToJsonElement(result.toString()).jsonObject
        assertTrue(resultObj.containsKey("breakpoints"))
    }

    // ==================== Request Dispatcher Integration Tests ====================

    @Test
    fun `test request dispatcher routes to correct handler`() = runBlocking {
        val dispatcher = RequestDispatcher()
        dispatcher.register(InitializeHandler())
        dispatcher.register(SetExceptionBreakpointsHandler())
        
        // Test initialize
        val initResult = dispatcher.dispatch("initialize", null, null)
        assertNotNull(initResult)
        assertTrue(initResult.toString().contains("supportsConfigurationDoneRequest"))
        
        // Test setExceptionBreakpoints
        val exceptionResult = dispatcher.dispatch("setExceptionBreakpoints", null, null)
        assertNotNull(exceptionResult)
        assertTrue(exceptionResult.toString().contains("breakpoints"))
    }

    @Test
    fun `test request dispatcher throws for unknown command`() {
        val dispatcher = RequestDispatcher()
        dispatcher.register(InitializeHandler())
        
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dispatcher.dispatch("unknownCommand", null, null)
            }
        }
    }

    // ==================== DAP Message Serialization Tests ====================

    @Test
    fun `test DAP request serialization roundtrip`() {
        val request = DAPRequest(
            seq = 1,
            command = "initialize",
            arguments = buildJsonObject {
                put("clientID", "vscode")
            }
        )
        
        val jsonString = json.encodeToString(DAPRequest.serializer(), request)
        val deserialized = json.decodeFromString<DAPRequest>(jsonString)
        
        assertEquals(request.seq, deserialized.seq)
        assertEquals(request.type, deserialized.type)
        assertEquals(request.command, deserialized.command)
    }

    @Test
    fun `test DAP response serialization roundtrip`() {
        val response = DAPResponse(
            seq = 2,
            request_seq = 1,
            success = true,
            command = "initialize",
            body = buildJsonObject {
                put("supportsConfigurationDoneRequest", true)
            }
        )
        
        val jsonString = json.encodeToString(DAPResponse.serializer(), response)
        val deserialized = json.decodeFromString<DAPResponse>(jsonString)
        
        assertEquals(response.seq, deserialized.seq)
        assertEquals(response.request_seq, deserialized.request_seq)
        assertEquals(response.success, deserialized.success)
        assertEquals(response.command, deserialized.command)
    }

    @Test
    fun `test DAP event serialization roundtrip`() {
        val event = DAPEvent(
            seq = 1,
            event = "stopped",
            body = buildJsonObject {
                put("reason", "breakpoint")
                put("threadId", 1)
            }
        )
        
        val jsonString = json.encodeToString(DAPEvent.serializer(), event)
        val deserialized = json.decodeFromString<DAPEvent>(jsonString)
        
        assertEquals(event.seq, deserialized.seq)
        assertEquals(event.type, deserialized.type)
        assertEquals(event.event, deserialized.event)
    }

    // ==================== Event Emitter Integration Tests ====================

    @Test
    fun `test event emitter sends proper DAP format`() {
        val output = ByteArrayOutputStream()
        val emitter = com.kotlindebugger.dap.event.EventEmitter(output)
        
        emitter.sendInitialized()
        
        val result = output.toString()
        
        // Verify Content-Length header
        assertTrue(result.startsWith("Content-Length:"))
        
        // Verify JSON content
        assertTrue(result.contains("\"event\":\"initialized\""))
        assertTrue(result.contains("\"type\":\"event\""))
    }

    @Test
    fun `test event emitter stopped event with breakpoint`() {
        val output = ByteArrayOutputStream()
        val emitter = com.kotlindebugger.dap.event.EventEmitter(output)
        
        emitter.sendStopped("breakpoint", 1, hitBreakpointIds = listOf(1))
        
        val result = output.toString()
        
        assertTrue(result.contains("\"event\":\"stopped\""))
        assertTrue(result.contains("\"reason\":\"breakpoint\""))
        assertTrue(result.contains("\"threadId\":1"))
        assertTrue(result.contains("\"hitBreakpointIds\""))
    }

    // ==================== Source Path Resolver Integration Tests ====================

    @Test
    fun `test source path resolver with multiple paths`() {
        val resolver = com.kotlindebugger.dap.converter.SourcePathResolver()
        
        resolver.setSourcePaths(listOf(
            "/path/to/src/main/kotlin",
            "/path/to/src/test/kotlin"
        ))
        
        assertEquals(2, resolver.getSourcePaths().size)
    }

    // ==================== Variable Reference Manager Integration Tests ====================

    @Test
    fun `test variable reference manager lifecycle`() {
        val manager = VariableReferenceManager()
        
        // Initial state
        assertNull(manager.getReference(1000))
        
        // Clear should work even when empty
        assertDoesNotThrow { manager.clear() }
        
        // Remove non-existent reference
        assertDoesNotThrow { manager.removeReference(9999) }
    }

    // ==================== Full DAP Protocol Flow Tests ====================

    @Test
    fun `test complete DAP initialization sequence`() = runBlocking {
        val dispatcher = RequestDispatcher()
        dispatcher.register(InitializeHandler())
        dispatcher.register(SetExceptionBreakpointsHandler())
        
        // Step 1: Initialize
        val initArgs = buildJsonObject {
            put("clientID", "vscode")
            put("adapterID", "kotlin-debug")
        }
        val initResult = dispatcher.dispatch("initialize", initArgs, null)
        assertNotNull(initResult)
        
        // Verify capabilities in init result
        val resultStr = initResult.toString()
        assertTrue(resultStr.contains("supportsConfigurationDoneRequest"))
        
        // Step 2: Set exception breakpoints (usually sent after initialize)
        val exceptionArgs = buildJsonObject {
            putJsonArray("filters") {}
        }
        val exceptionResult = dispatcher.dispatch("setExceptionBreakpoints", exceptionArgs, null)
        assertNotNull(exceptionResult)
    }

    // ==================== Protocol Types Validation Tests ====================

    @Test
    fun `test Breakpoint type validation`() {
        val bp = Breakpoint(
            id = 1,
            verified = true,
            line = 42,
            source = Source("Main.kt", "/path/to/Main.kt")
        )
        
        assertTrue(bp.verified)
        assertEquals(42, bp.line)
        assertNotNull(bp.source)
    }

    @Test
    fun `test StackFrame type validation`() {
        val frame = StackFrame(
            id = 0,
            name = "main",
            source = Source("Main.kt"),
            line = 10,
            column = 0,
            presentationHint = "normal"
        )
        
        assertEquals(0, frame.id)
        assertEquals("main", frame.name)
        assertEquals(10, frame.line)
    }

    @Test
    fun `test Scope type validation`() {
        val scope = Scope(
            name = "Locals",
            variablesReference = 1001,
            expensive = false
        )
        
        assertEquals("Locals", scope.name)
        assertEquals(1001, scope.variablesReference)
        assertFalse(scope.expensive)
    }

    @Test
    fun `test Variable type validation`() {
        val variable = Variable(
            name = "count",
            value = "42",
            type = "int",
            variablesReference = 0
        )
        
        assertEquals("count", variable.name)
        assertEquals("42", variable.value)
        assertEquals(0, variable.variablesReference)
    }

    @Test
    fun `test Thread type validation`() {
        val thread = Thread(id = 1, name = "main")
        
        assertEquals(1, thread.id)
        assertEquals("main", thread.name)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `test dispatcher handles handler exception gracefully`() {
        val dispatcher = RequestDispatcher()
        
        val failingHandler = object : RequestHandler {
            override val command = "failing"
            override suspend fun handle(args: JsonObject?, session: com.kotlindebugger.core.DebugSession?): JsonElement? {
                throw RuntimeException("Simulated failure")
            }
        }
        
        dispatcher.register(failingHandler)
        
        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking {
                dispatcher.dispatch("failing", null, null)
            }
        }
        assertEquals("Simulated failure", exception.message)
    }

    // ==================== Capabilities Tests ====================

    @Test
    fun `test capabilities has correct default values`() {
        val caps = Capabilities()
        
        assertTrue(caps.supportsConfigurationDoneRequest)
        assertFalse(caps.supportsFunctionBreakpoints)
        assertFalse(caps.supportsConditionalBreakpoints)
        assertTrue(caps.supportsEvaluateForHovers)
        assertFalse(caps.supportsStepBack)
        assertTrue(caps.supportsSetVariable)
        assertFalse(caps.supportsRestartFrame)
        assertFalse(caps.supportsStepInTargetsRequest)
        assertTrue(caps.supportsValueFormattingOptions)
    }

    @Test
    fun `test capabilities serialization includes all fields`() {
        val caps = Capabilities(
            supportsConditionalBreakpoints = true
        )
        
        val jsonString = json.encodeToString(Capabilities.serializer(), caps)
        
        assertTrue(jsonString.contains("supportsConfigurationDoneRequest"))
        assertTrue(jsonString.contains("supportsFunctionBreakpoints"))
        assertTrue(jsonString.contains("supportsConditionalBreakpoints"))
        assertTrue(jsonString.contains("supportsEvaluateForHovers"))
        assertTrue(jsonString.contains("supportsStepBack"))
        assertTrue(jsonString.contains("supportsSetVariable"))
        assertTrue(jsonString.contains("supportsRestartFrame"))
        assertTrue(jsonString.contains("supportsStepInTargetsRequest"))
        assertTrue(jsonString.contains("supportsValueFormattingOptions"))
    }
}
