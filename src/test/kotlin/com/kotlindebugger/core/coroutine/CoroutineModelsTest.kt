package com.kotlindebugger.core.coroutine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * CoroutineModels 单元测试
 * 测试协程数据模型的正确性
 */
class CoroutineModelsTest {

    @Test
    fun `CoroutineState fromString parses RUNNING correctly`() {
        assertEquals(CoroutineState.RUNNING, CoroutineState.fromString("RUNNING"))
        assertEquals(CoroutineState.RUNNING, CoroutineState.fromString("running"))
        assertEquals(CoroutineState.RUNNING, CoroutineState.fromString("Running"))
    }

    @Test
    fun `CoroutineState fromString parses SUSPENDED correctly`() {
        assertEquals(CoroutineState.SUSPENDED, CoroutineState.fromString("SUSPENDED"))
        assertEquals(CoroutineState.SUSPENDED, CoroutineState.fromString("suspended"))
    }

    @Test
    fun `CoroutineState fromString parses CREATED correctly`() {
        assertEquals(CoroutineState.CREATED, CoroutineState.fromString("CREATED"))
        assertEquals(CoroutineState.CREATED, CoroutineState.fromString("created"))
    }

    @Test
    fun `CoroutineState fromString returns UNKNOWN for invalid state`() {
        assertEquals(CoroutineState.UNKNOWN, CoroutineState.fromString("invalid"))
        assertEquals(CoroutineState.UNKNOWN, CoroutineState.fromString(null))
        assertEquals(CoroutineState.UNKNOWN, CoroutineState.fromString(""))
    }

    @Test
    fun `CoroutineInfo isSuspended returns true for SUSPENDED state`() {
        val info = CoroutineInfo(
            id = 1L,
            name = "test",
            state = CoroutineState.SUSPENDED,
            dispatcher = null,
            lastObservedThread = null,
            lastObservedFrame = null
        )
        assertTrue(info.isSuspended)
        assertFalse(info.isRunning)
        assertFalse(info.isCreated)
    }

    @Test
    fun `CoroutineInfo isRunning returns true for RUNNING state`() {
        val info = CoroutineInfo(
            id = 1L,
            name = "test",
            state = CoroutineState.RUNNING,
            dispatcher = null,
            lastObservedThread = null,
            lastObservedFrame = null
        )
        assertTrue(info.isRunning)
        assertFalse(info.isSuspended)
        assertFalse(info.isCreated)
    }

    @Test
    fun `CoroutineInfo isCreated returns true for CREATED state`() {
        val info = CoroutineInfo(
            id = 1L,
            name = "test",
            state = CoroutineState.CREATED,
            dispatcher = null,
            lastObservedThread = null,
            lastObservedFrame = null
        )
        assertTrue(info.isCreated)
        assertFalse(info.isRunning)
        assertFalse(info.isSuspended)
    }

    @Test
    fun `CoroutineInfo getDescription formats correctly`() {
        val info = CoroutineInfo(
            id = 42L,
            name = "myCoroutine",
            state = CoroutineState.SUSPENDED,
            dispatcher = "Dispatchers.Default",
            lastObservedThread = null,
            lastObservedFrame = null
        )
        val description = info.getDescription()
        assertTrue(description.contains("myCoroutine"))
        assertTrue(description.contains("42"))
        assertTrue(description.contains("SUSPENDED"))
        assertTrue(description.contains("Dispatchers.Default"))
    }

    @Test
    fun `CoroutineInfo uses default name when name is default`() {
        val info = CoroutineInfo(
            id = 1L,
            name = CoroutineInfo.DEFAULT_COROUTINE_NAME,
            state = CoroutineState.RUNNING,
            dispatcher = null,
            lastObservedThread = null,
            lastObservedFrame = null
        )
        assertEquals("coroutine", info.name)
    }

    @Test
    fun `CoroutineStackFrameItem toString formats correctly`() {
        val frame = CoroutineStackFrameItem(
            className = "com.example.MyClass",
            methodName = "myMethod",
            location = com.kotlindebugger.common.model.SourcePosition("MyClass.kt", 42),
            isCreationFrame = false
        )
        val str = frame.toString()
        assertTrue(str.contains("com.example.MyClass"))
        assertTrue(str.contains("myMethod"))
        assertTrue(str.contains("MyClass.kt:42"))
    }

    @Test
    fun `CoroutineStackFrameItem with null location uses unknown`() {
        val frame = CoroutineStackFrameItem(
            className = "com.example.MyClass",
            methodName = "myMethod",
            location = null,
            isCreationFrame = false
        )
        val str = frame.toString()
        assertTrue(str.contains("unknown location"))
    }

    @Test
    fun `CreationCoroutineStackFrameItem is marked as creation frame`() {
        val frame = CreationCoroutineStackFrameItem(
            className = "com.example.MyClass",
            methodName = "launch",
            location = null
        )
        assertTrue(frame.isCreationFrame)
    }

    @Test
    fun `SuspendExitMode has all expected values`() {
        val modes = SuspendExitMode.entries
        assertTrue(modes.contains(SuspendExitMode.NONE))
        assertTrue(modes.contains(SuspendExitMode.SUSPEND_LAMBDA))
        assertTrue(modes.contains(SuspendExitMode.SUSPEND_METHOD))
        assertTrue(modes.contains(SuspendExitMode.SUSPEND_METHOD_PARAMETER))
    }
}
