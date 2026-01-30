package com.kotlindebugger.core.breakpoint

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * ExceptionBreakpointManager 单元测试
 * 
 * 注意：这些测试只测试基本的逻辑，不涉及实际的 JDI 连接
 */
class ExceptionBreakpointManagerTest {

    @Test
    fun `test ExceptionBreakpointResult data class`() {
        val result = ExceptionBreakpointResult(verified = true, message = null)
        assertTrue(result.verified)
        assertNull(result.message)
    }

    @Test
    fun `test ExceptionBreakpointResult with message`() {
        val result = ExceptionBreakpointResult(
            verified = false, 
            message = "Failed to set exception breakpoint"
        )
        assertFalse(result.verified)
        assertEquals("Failed to set exception breakpoint", result.message)
    }

    @Test
    fun `test ExceptionBreakpointResult equality`() {
        val result1 = ExceptionBreakpointResult(verified = true, message = null)
        val result2 = ExceptionBreakpointResult(verified = true, message = null)
        assertEquals(result1, result2)
    }

    @Test
    fun `test ExceptionBreakpointResult copy`() {
        val result1 = ExceptionBreakpointResult(verified = true, message = null)
        val result2 = result1.copy(verified = false)
        assertFalse(result2.verified)
        assertNull(result2.message)
    }
}
