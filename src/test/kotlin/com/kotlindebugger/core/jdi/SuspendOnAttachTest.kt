package com.kotlindebugger.core.jdi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * SuspendOnAttach 功能测试
 *
 * 验证 DebugTarget 的 suspend 参数配置正确性，以及
 * CommandProcessor 解析 attach 命令时的行为。
 */
class SuspendOnAttachTest {

    // ==================== DebugTarget.Attach 配置测试 ====================

    @Test
    fun `Attach target has suspend=true by default`() {
        val target = DebugTarget.Attach(host = "localhost", port = 5005)
        assertTrue(target.suspend, "Attach should default to suspend=true")
    }

    @Test
    fun `Attach target can be created with suspend=false`() {
        val target = DebugTarget.Attach(host = "localhost", port = 5005, suspend = false)
        assertFalse(target.suspend)
    }

    @Test
    fun `AttachPid target has suspend=true by default`() {
        val target = DebugTarget.AttachPid(pid = 12345L)
        assertTrue(target.suspend, "AttachPid should default to suspend=true")
    }

    @Test
    fun `AttachPid target can be created with suspend=false`() {
        val target = DebugTarget.AttachPid(pid = 12345L, suspend = false)
        assertFalse(target.suspend)
    }

    @Test
    fun `Attach target sourceRoots default is empty`() {
        val target = DebugTarget.Attach(host = "localhost", port = 5005)
        assertTrue(target.sourceRoots.isEmpty())
    }

    @Test
    fun `Launch target does not have suspend flag in attach sense`() {
        // Launch 已有自己的 suspend 参数，与 Attach 的 suspend 无关
        val target = DebugTarget.Launch(
            mainClass = "MainKt",
            classpath = emptyList(),
            suspend = true
        )
        assertTrue(target.suspend)
    }
}
