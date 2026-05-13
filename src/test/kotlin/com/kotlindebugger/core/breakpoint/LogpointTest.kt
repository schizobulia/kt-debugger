package com.kotlindebugger.core.breakpoint

import com.kotlindebugger.common.model.Breakpoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Logpoint 相关测试
 * 验证 LineBreakpoint 的 logMessage 字段以及 logMessage 传递
 */
class LogpointTest {

    @Test
    fun `test LineBreakpoint with logMessage`() {
        val bp = Breakpoint.LineBreakpoint(
            id = 1,
            file = "Main.kt",
            line = 10,
            logMessage = "Value of x is {x}"
        )

        assertEquals("Value of x is {x}", bp.logMessage)
        assertNull(bp.condition) // logpoint 通常没有条件
        assertTrue(bp.enabled)
    }

    @Test
    fun `test LineBreakpoint without logMessage is regular breakpoint`() {
        val bp = Breakpoint.LineBreakpoint(
            id = 2,
            file = "Main.kt",
            line = 20
        )

        assertNull(bp.logMessage) // 普通断点没有 logMessage
        assertNull(bp.condition)
    }

    @Test
    fun `test LineBreakpoint toString includes logMessage`() {
        val bp = Breakpoint.LineBreakpoint(
            id = 3,
            file = "Test.kt",
            line = 5,
            logMessage = "Hello {name}"
        )

        val str = bp.toString()
        assertTrue(str.contains("log: Hello {name}"), "toString should include logMessage: $str")
    }

    @Test
    fun `test LineBreakpoint logMessage and condition can coexist`() {
        // logpoint 也可以有条件（只有条件满足时才打印日志）
        val bp = Breakpoint.LineBreakpoint(
            id = 4,
            file = "App.kt",
            line = 100,
            condition = "x > 0",
            logMessage = "x = {x}"
        )

        assertEquals("x > 0", bp.condition)
        assertEquals("x = {x}", bp.logMessage)
    }

    @Test
    fun `test LineBreakpoint copy preserves logMessage`() {
        val original = Breakpoint.LineBreakpoint(
            id = 5,
            file = "Foo.kt",
            line = 42,
            logMessage = "Reached line 42"
        )

        val copy = original.copy(enabled = false)
        assertEquals("Reached line 42", copy.logMessage) // copy 应该保留 logMessage
        assertFalse(copy.enabled)
    }

    @Test
    fun `test BreakpointInfo with logMessage`() {
        // 验证 BreakpointInfo 数据类包含 logMessage 字段
        val info = com.kotlindebugger.core.event.BreakpointInfo(
            id = 1,
            condition = null,
            logMessage = "log: {value}"
        )

        assertEquals(1, info.id)
        assertNull(info.condition)
        assertEquals("log: {value}", info.logMessage)
    }

    @Test
    fun `test BreakpointInfo default logMessage is null`() {
        val info = com.kotlindebugger.core.event.BreakpointInfo(
            id = 2,
            condition = "x == 5"
        )

        assertNull(info.logMessage) // 默认为 null（普通条件断点）
    }
}
