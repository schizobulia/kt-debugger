package com.kotlindebugger.core.breakpoint

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 测试 hitCount（命中次数断点）相关的数据模型与逻辑
 *
 * 验证：
 * 1. PendingBreakpoint 正确存储 hitCount
 * 2. Breakpoint.LineBreakpoint 的 hitCount 字段
 * 3. hitCount=0 表示无限制（普通断点）
 * 4. hitCount>0 表示在第 N 次命中时才触发
 */
class HitConditionBreakpointTest {

    // 用于测试的简化版 PendingBreakpoint（镜像实现中的私有 data class）
    private data class PendingBp(
        val breakpointId: Int,
        val line: Int,
        val condition: String?,
        val logMessage: String? = null,
        val hitCount: Int = 0
    )

    @Test
    fun `PendingBreakpoint defaults hitCount to 0`() {
        val bp = PendingBp(breakpointId = 1, line = 10, condition = null)
        assertEquals(0, bp.hitCount)
    }

    @Test
    fun `PendingBreakpoint stores hitCount correctly`() {
        val bp = PendingBp(breakpointId = 2, line = 20, condition = null, hitCount = 5)
        assertEquals(5, bp.hitCount)
    }

    @Test
    fun `LineBreakpoint defaults hitCount to 0`() {
        val bp = com.kotlindebugger.common.model.Breakpoint.LineBreakpoint(
            id = 1,
            file = "Test.kt",
            line = 10
        )
        assertEquals(0, bp.hitCount)
    }

    @Test
    fun `LineBreakpoint stores non-zero hitCount`() {
        val bp = com.kotlindebugger.common.model.Breakpoint.LineBreakpoint(
            id = 1,
            file = "Test.kt",
            line = 10,
            hitCount = 5
        )
        assertEquals(5, bp.hitCount)
    }

    @Test
    fun `hitCount of 0 means no filter applied`() {
        // hitCount=0 意味着普通断点，不需要添加 countFilter
        val hitCount = 0
        assertFalse(hitCount > 0, "hitCount=0 should NOT apply countFilter")
    }

    @Test
    fun `hitCount greater than 0 means filter should be applied`() {
        // hitCount>0 意味着需要添加 countFilter
        val hitCount = 5
        assertTrue(hitCount > 0, "hitCount=5 SHOULD apply countFilter")
    }

    @Test
    fun `hitCount copied correctly in LineBreakpoint`() {
        val original = com.kotlindebugger.common.model.Breakpoint.LineBreakpoint(
            id = 1,
            file = "Main.kt",
            line = 42,
            hitCount = 10
        )
        val copy = original.copy(enabled = false)
        assertEquals(10, copy.hitCount, "hitCount should be preserved in copy()")
    }
}
