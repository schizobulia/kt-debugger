package com.kotlindebugger.common.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ModelsTest {

    @Test
    fun `SourcePosition toString formats correctly`() {
        val pos = SourcePosition("Main.kt", 42)
        assertEquals("Main.kt:42", pos.toString())
    }

    @Test
    fun `SourcePosition with column`() {
        val pos = SourcePosition("Main.kt", 42, 10)
        assertEquals("Main.kt", pos.file)
        assertEquals(42, pos.line)
        assertEquals(10, pos.column)
    }

    @Test
    fun `LineBreakpoint toString formats correctly`() {
        val bp = Breakpoint.LineBreakpoint(
            id = 1,
            file = "Main.kt",
            line = 42,
            enabled = true,
            condition = null
        )
        assertEquals("Breakpoint #1 at Main.kt:42", bp.toString())
    }

    @Test
    fun `LineBreakpoint disabled shows status`() {
        val bp = Breakpoint.LineBreakpoint(
            id = 1,
            file = "Main.kt",
            line = 42,
            enabled = false
        )
        assertTrue(bp.toString().contains("(disabled)"))
    }

    @Test
    fun `LineBreakpoint with condition shows condition`() {
        val bp = Breakpoint.LineBreakpoint(
            id = 1,
            file = "Main.kt",
            line = 42,
            condition = "x > 10"
        )
        assertTrue(bp.toString().contains("if (x > 10)"))
    }

    @Test
    fun `MethodBreakpoint toString formats correctly`() {
        val bp = Breakpoint.MethodBreakpoint(
            id = 2,
            className = "com.example.Main",
            methodName = "doSomething"
        )
        assertEquals("Breakpoint #2 at com.example.Main.doSomething()", bp.toString())
    }

    @Test
    fun `StackFrameInfo toString formats correctly`() {
        val frame = StackFrameInfo(
            index = 0,
            className = "com.example.Main",
            methodName = "main",
            location = SourcePosition("Main.kt", 10)
        )
        assertEquals("#0  com.example.Main.main(Main.kt:10)", frame.toString())
    }

    @Test
    fun `StackFrameInfo with inline marker`() {
        val frame = StackFrameInfo(
            index = 1,
            className = "com.example.Main",
            methodName = "inlineFunc",
            location = SourcePosition("Main.kt", 20),
            isInline = true
        )
        assertTrue(frame.toString().contains("[inline depth=0]"))
    }

    @Test
    fun `StackFrameInfo with native marker`() {
        val frame = StackFrameInfo(
            index = 2,
            className = "java.lang.Thread",
            methodName = "sleep",
            location = null,
            isNative = true
        )
        assertTrue(frame.toString().contains("[native]"))
        assertTrue(frame.toString().contains("unknown"))
    }

    @Test
    fun `VariableInfo toString formats correctly`() {
        val variable = VariableInfo(
            name = "count",
            typeName = "kotlin.Int",
            value = "42"
        )
        assertEquals("count: kotlin.Int = 42", variable.toString())
    }

    @Test
    fun `ThreadInfo toString formats correctly`() {
        val thread = ThreadInfo(
            id = 1,
            name = "main",
            status = ThreadStatus.RUNNING,
            isSuspended = false
        )
        assertEquals("Thread #1 \"main\" RUNNING", thread.toString())
    }

    @Test
    fun `ThreadInfo suspended shows marker`() {
        val thread = ThreadInfo(
            id = 2,
            name = "worker",
            status = ThreadStatus.WAITING,
            isSuspended = true
        )
        assertTrue(thread.toString().contains("(suspended)"))
    }

    @Test
    fun `DebugEvent BreakpointHit contains correct data`() {
        val bp = Breakpoint.LineBreakpoint(1, "Main.kt", 10)
        val event = DebugEvent.BreakpointHit(
            breakpoint = bp,
            threadId = 1L,
            location = SourcePosition("Main.kt", 10)
        )

        assertEquals(bp, event.breakpoint)
        assertEquals(1L, event.threadId)
        assertNotNull(event.location)
    }

    @Test
    fun `DebugEvent StepCompleted contains correct data`() {
        val event = DebugEvent.StepCompleted(
            threadId = 1L,
            location = SourcePosition("Main.kt", 15)
        )

        assertEquals(1L, event.threadId)
        assertEquals(15, event.location?.line)
    }

    @Test
    fun `DebugEvent ExceptionThrown contains correct data`() {
        val event = DebugEvent.ExceptionThrown(
            exceptionClass = "java.lang.NullPointerException",
            message = "Cannot invoke method on null",
            threadId = 1L,
            location = SourcePosition("Main.kt", 20)
        )

        assertEquals("java.lang.NullPointerException", event.exceptionClass)
        assertEquals("Cannot invoke method on null", event.message)
    }

    @Test
    fun `ThreadStatus enum values`() {
        assertEquals(7, ThreadStatus.values().size)
        assertNotNull(ThreadStatus.valueOf("RUNNING"))
        assertNotNull(ThreadStatus.valueOf("SLEEPING"))
        assertNotNull(ThreadStatus.valueOf("WAITING"))
        assertNotNull(ThreadStatus.valueOf("MONITOR"))
        assertNotNull(ThreadStatus.valueOf("ZOMBIE"))
        assertNotNull(ThreadStatus.valueOf("NOT_STARTED"))
        assertNotNull(ThreadStatus.valueOf("UNKNOWN"))
    }
}
