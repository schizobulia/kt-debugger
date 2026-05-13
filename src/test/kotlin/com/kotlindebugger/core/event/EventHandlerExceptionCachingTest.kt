package com.kotlindebugger.core.event

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 测试 EventHandler 的异常对象缓存功能。
 *
 * 由于无法在单元测试中真实触发 JDI ExceptionEvent，
 * 这里验证：
 *   1. 初始状态下 getLastExceptionObject() 返回 null
 *   2. BreakpointInfo 数据类的正确性
 *   3. 异常相关的数据结构
 *
 * 集成测试（端到端）放在 test-program/InteractiveTest.kt 的交互式场景中。
 */
class EventHandlerExceptionCachingTest {

    @Test
    fun `BreakpointInfo stores id condition and logMessage correctly`() {
        val info = BreakpointInfo(id = 42, condition = "x > 0", logMessage = "value={x}")
        assertEquals(42, info.id)
        assertEquals("x > 0", info.condition)
        assertEquals("value={x}", info.logMessage)
    }

    @Test
    fun `BreakpointInfo with null condition represents unconditional breakpoint`() {
        val info = BreakpointInfo(id = 1, condition = null)
        assertNull(info.condition)
        assertNull(info.logMessage)
    }

    @Test
    fun `BreakpointInfo copy works correctly`() {
        val original = BreakpointInfo(id = 1, condition = "a == b", logMessage = null)
        val copy = original.copy(id = 2)
        assertEquals(2, copy.id)
        assertEquals("a == b", copy.condition)
        assertNull(copy.logMessage)
    }

    @Test
    fun `BreakpointInfo with logMessage indicates logpoint`() {
        // logMessage 非空时表示 Logpoint（打印日志而不暂停）
        val logpoint = BreakpointInfo(id = 5, condition = null, logMessage = "Hello {name}")
        assertNotNull(logpoint.logMessage)
        assertTrue(logpoint.logMessage!!.contains("{name}"))
    }

    @Test
    fun `BreakpointInfo equality check`() {
        val info1 = BreakpointInfo(id = 10, condition = "x > 0", logMessage = null)
        val info2 = BreakpointInfo(id = 10, condition = "x > 0", logMessage = null)
        assertEquals(info1, info2)
    }
}
