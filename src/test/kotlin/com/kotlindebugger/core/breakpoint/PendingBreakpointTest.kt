package com.kotlindebugger.core.breakpoint

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 待处理断点（PendingBreakpoint）逻辑的单元测试。
 *
 * 背景：当一个 Kotlin 源文件（如 App.kt）包含多个 JVM 类（AppKt、TaskPlanner、Task 等）时，
 * 断点所在行可能属于其中任意一个类。之前的 Bug 是：只要第一个类加载（即使断点安装失败），
 * 就把所有 pending 断点清掉，导致后续类加载时无法安装。
 *
 * 修复后：只移除已成功安装的 pending 断点，未安装的继续保留。
 */
class PendingBreakpointTest {

    /**
     * 用纯 Kotlin 数据结构模拟 pending 断点的管理逻辑，
     * 验证修复前后的行为差异，无需 JDI 依赖。
     */
    private data class PendingBp(val id: Int, val line: Int)

    /**
     * 模拟修复前的 buggy 行为：第一个类加载后无论是否安装成功，直接清空 pending 列表
     */
    private fun onClassPreparedBuggy(
        pending: MutableList<PendingBp>,
        classCanInstallLines: Set<Int>
    ) {
        pending.forEach { /* 尝试安装，失败则忽略 */ classCanInstallLines.contains(it.line) }
        pending.clear() // Bug: 无条件清空
    }

    /**
     * 模拟修复后的正确行为：只移除成功安装的断点
     */
    private fun onClassPreparedFixed(
        pending: MutableList<PendingBp>,
        classCanInstallLines: Set<Int>
    ) {
        val installedIds = pending.filter { classCanInstallLines.contains(it.line) }
            .map { it.id }.toSet()
        if (installedIds.isNotEmpty()) {
            pending.removeIf { it.id in installedIds }
        }
    }

    // ========== Bug 复现测试：修复前行为 ==========

    @Test
    fun `buggy behavior - first class clears all pending even if not installed`() {
        val pending = mutableListOf(PendingBp(id = 1, line = 48))

        // AppKt 加载，第 48 行不在其中 -> 安装失败
        onClassPreparedBuggy(pending, classCanInstallLines = emptySet())

        // Bug: pending 已被清空，TaskPlanner 加载时找不到待装断点
        assertTrue(pending.isEmpty(), "Buggy: pending was cleared even though nothing was installed")
    }

    // ========== 修复后行为测试 ==========

    @Test
    fun `fixed - pending breakpoint remains when first class cannot install it`() {
        val pending = mutableListOf(PendingBp(id = 1, line = 48))

        // AppKt 加载，第 48 行不在其中 -> 安装失败
        onClassPreparedFixed(pending, classCanInstallLines = emptySet())

        // 修复后：pending 不被清空，等待后续类加载
        assertFalse(pending.isEmpty(), "Pending breakpoint should remain for later class loading")
        assertEquals(1, pending.size)
        assertEquals(48, pending[0].line)
    }

    @Test
    fun `fixed - pending breakpoint is removed after being successfully installed`() {
        val pending = mutableListOf(PendingBp(id = 1, line = 48))

        // TaskPlanner 加载，第 48 行在其中 -> 安装成功
        onClassPreparedFixed(pending, classCanInstallLines = setOf(48))

        // 安装成功后清理
        assertTrue(pending.isEmpty(), "Pending breakpoint should be removed after installation")
    }

    @Test
    fun `fixed - multiple classes from same file install different breakpoints`() {
        // App.kt 有两个断点：第 10 行（在 AppKt）和第 48 行（在 TaskPlanner）
        val pending = mutableListOf(
            PendingBp(id = 1, line = 10),
            PendingBp(id = 2, line = 48)
        )

        // AppKt 加载：能安装第 10 行，不能安装第 48 行
        onClassPreparedFixed(pending, classCanInstallLines = setOf(10))
        assertEquals(1, pending.size, "Only line-48 breakpoint should remain pending")
        assertEquals(48, pending[0].line)

        // TaskPlanner 加载：能安装第 48 行
        onClassPreparedFixed(pending, classCanInstallLines = setOf(48))
        assertTrue(pending.isEmpty(), "All breakpoints should now be installed")
    }

    @Test
    fun `fixed - breakpoint at line only in second class still gets installed`() {
        val pending = mutableListOf(PendingBp(id = 1, line = 48))

        // 第一个类（AppKt）不含第 48 行
        onClassPreparedFixed(pending, classCanInstallLines = emptySet())
        assertEquals(1, pending.size, "Should still have pending breakpoint")

        // 第二个类（TaskPlanner）含第 48 行，应成功安装
        onClassPreparedFixed(pending, classCanInstallLines = setOf(48))
        assertTrue(pending.isEmpty(), "Breakpoint should be installed in second class")
    }

    @Test
    fun `fixed - breakpoint uninstallable in all classes stays pending without error`() {
        // 若断点行不在任何类中（例如空行），pending 不被清空，不报错
        val pending = mutableListOf(PendingBp(id = 1, line = 99))

        onClassPreparedFixed(pending, classCanInstallLines = emptySet())
        onClassPreparedFixed(pending, classCanInstallLines = emptySet())

        assertEquals(1, pending.size, "Uninstallable breakpoint stays pending")
    }

    @Test
    fun `fixed - multiple breakpoints some installed some not`() {
        val pending = mutableListOf(
            PendingBp(id = 1, line = 10),
            PendingBp(id = 2, line = 20),
            PendingBp(id = 3, line = 30)
        )

        // 第一个类只能安装 line=10 和 line=30
        onClassPreparedFixed(pending, classCanInstallLines = setOf(10, 30))

        assertEquals(1, pending.size)
        assertEquals(20, pending[0].line, "Only line-20 breakpoint should remain")
    }
}
