package com.kotlindebugger.dap.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * DAP 协议类型单元测试
 */
class TypesTest {

    private val json = Json { encodeDefaults = true }

    // ==================== Source Tests ====================

    @Test
    fun `test Source serialization with all fields`() {
        val source = Source(
            name = "Main.kt",
            path = "/path/to/Main.kt",
            sourceReference = 123
        )

        val jsonString = json.encodeToString(source)
        assertTrue(jsonString.contains("\"name\":\"Main.kt\""))
        assertTrue(jsonString.contains("\"path\":\"/path/to/Main.kt\""))
        assertTrue(jsonString.contains("\"sourceReference\":123"))
    }

    @Test
    fun `test Source serialization with defaults`() {
        val source = Source()
        val jsonString = json.encodeToString(source)
        assertTrue(jsonString.contains("\"sourceReference\":0"))
    }

    @Test
    fun `test Source equality`() {
        val source1 = Source("Main.kt", "/path/to/Main.kt", 0)
        val source2 = Source("Main.kt", "/path/to/Main.kt", 0)
        val source3 = Source("Other.kt", "/path/to/Other.kt", 0)

        assertEquals(source1, source2)
        assertNotEquals(source1, source3)
    }

    // ==================== Breakpoint Tests ====================

    @Test
    fun `test Breakpoint verified creation`() {
        val bp = Breakpoint(
            id = 1,
            verified = true,
            line = 42,
            source = Source("Main.kt", "/path/to/Main.kt")
        )

        assertEquals(1, bp.id)
        assertTrue(bp.verified)
        assertEquals(42, bp.line)
        assertNotNull(bp.source)
        assertNull(bp.message)
    }

    @Test
    fun `test Breakpoint unverified with message`() {
        val bp = Breakpoint(
            id = -1,
            verified = false,
            line = 10,
            source = null,
            message = "Cannot set breakpoint"
        )

        assertEquals(-1, bp.id)
        assertFalse(bp.verified)
        assertEquals("Cannot set breakpoint", bp.message)
    }

    @Test
    fun `test Breakpoint serialization`() {
        val bp = Breakpoint(id = 1, verified = true, line = 42)
        val jsonString = json.encodeToString(bp)
        
        assertTrue(jsonString.contains("\"id\":1"))
        assertTrue(jsonString.contains("\"verified\":true"))
        assertTrue(jsonString.contains("\"line\":42"))
    }

    // ==================== SourceBreakpoint Tests ====================

    @Test
    fun `test SourceBreakpoint with line only`() {
        val sbp = SourceBreakpoint(line = 42)
        
        assertEquals(42, sbp.line)
        assertNull(sbp.column)
        assertNull(sbp.condition)
    }

    @Test
    fun `test SourceBreakpoint with condition`() {
        val sbp = SourceBreakpoint(
            line = 42,
            column = 10,
            condition = "x > 5"
        )
        
        assertEquals(42, sbp.line)
        assertEquals(10, sbp.column)
        assertEquals("x > 5", sbp.condition)
    }

    @Test
    fun `test SourceBreakpoint serialization`() {
        val sbp = SourceBreakpoint(line = 42, condition = "x > 5")
        val jsonString = json.encodeToString(sbp)
        
        assertTrue(jsonString.contains("\"line\":42"))
        assertTrue(jsonString.contains("\"condition\":\"x > 5\""))
    }

    // ==================== StackFrame Tests ====================

    @Test
    fun `test StackFrame creation`() {
        val frame = StackFrame(
            id = 0,
            name = "main",
            source = Source("Main.kt", "/path/to/Main.kt"),
            line = 42,
            column = 0,
            presentationHint = "normal"
        )

        assertEquals(0, frame.id)
        assertEquals("main", frame.name)
        assertEquals(42, frame.line)
        assertEquals(0, frame.column)
        assertEquals("normal", frame.presentationHint)
    }

    @Test
    fun `test StackFrame with inline hint`() {
        val frame = StackFrame(
            id = 1,
            name = "inlineFunc",
            line = 10,
            column = 0,
            presentationHint = "subtle"
        )

        assertEquals("subtle", frame.presentationHint)
    }

    @Test
    fun `test StackFrame serialization`() {
        val frame = StackFrame(
            id = 0,
            name = "main",
            line = 42,
            column = 0
        )
        val jsonString = json.encodeToString(frame)
        
        assertTrue(jsonString.contains("\"id\":0"))
        assertTrue(jsonString.contains("\"name\":\"main\""))
        assertTrue(jsonString.contains("\"line\":42"))
    }

    // ==================== Thread Tests ====================

    @Test
    fun `test Thread creation`() {
        val thread = Thread(id = 1, name = "main")
        
        assertEquals(1, thread.id)
        assertEquals("main", thread.name)
    }

    @Test
    fun `test Thread serialization`() {
        val thread = Thread(id = 1, name = "main")
        val jsonString = json.encodeToString(thread)
        
        assertTrue(jsonString.contains("\"id\":1"))
        assertTrue(jsonString.contains("\"name\":\"main\""))
    }

    @Test
    fun `test Thread equality`() {
        val thread1 = Thread(1, "main")
        val thread2 = Thread(1, "main")
        val thread3 = Thread(2, "worker")

        assertEquals(thread1, thread2)
        assertNotEquals(thread1, thread3)
    }

    // ==================== Scope Tests ====================

    @Test
    fun `test Scope creation`() {
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
    fun `test Scope expensive flag`() {
        val scope = Scope(
            name = "Globals",
            variablesReference = 2001,
            expensive = true
        )

        assertTrue(scope.expensive)
    }

    @Test
    fun `test Scope serialization`() {
        val scope = Scope(name = "Locals", variablesReference = 1001)
        val jsonString = json.encodeToString(scope)
        
        assertTrue(jsonString.contains("\"name\":\"Locals\""))
        assertTrue(jsonString.contains("\"variablesReference\":1001"))
    }

    // ==================== Variable Tests ====================

    @Test
    fun `test Variable creation`() {
        val variable = Variable(
            name = "count",
            value = "42",
            type = "int",
            variablesReference = 0
        )

        assertEquals("count", variable.name)
        assertEquals("42", variable.value)
        assertEquals("int", variable.type)
        assertEquals(0, variable.variablesReference)
    }

    @Test
    fun `test Variable with expandable reference`() {
        val variable = Variable(
            name = "list",
            value = "ArrayList@123",
            type = "java.util.ArrayList",
            variablesReference = 1002
        )

        assertEquals(1002, variable.variablesReference)
    }

    @Test
    fun `test Variable serialization`() {
        val variable = Variable(name = "x", value = "10", type = "int")
        val jsonString = json.encodeToString(variable)
        
        assertTrue(jsonString.contains("\"name\":\"x\""))
        assertTrue(jsonString.contains("\"value\":\"10\""))
        assertTrue(jsonString.contains("\"type\":\"int\""))
    }

    // ==================== Capabilities Tests ====================

    @Test
    fun `test Capabilities defaults`() {
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
    fun `test Capabilities with custom values`() {
        val caps = Capabilities(
            supportsConditionalBreakpoints = true,
            supportsFunctionBreakpoints = true
        )
        
        assertTrue(caps.supportsConditionalBreakpoints)
        assertTrue(caps.supportsFunctionBreakpoints)
    }

    @Test
    fun `test Capabilities serialization`() {
        val caps = Capabilities()
        val jsonString = json.encodeToString(caps)
        
        assertTrue(jsonString.contains("\"supportsConfigurationDoneRequest\":true"))
        assertTrue(jsonString.contains("\"supportsEvaluateForHovers\":true"))
    }
}
