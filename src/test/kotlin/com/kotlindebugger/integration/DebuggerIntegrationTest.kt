package com.kotlindebugger.integration

import com.kotlindebugger.common.model.Breakpoint
import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.core.SessionState
import com.kotlindebugger.core.jdi.DebugTarget
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * 调试器功能集成测试
 *
 * 测试断点管理和会话状态等核心功能
 *
 * 注意：断点触发相关的测试需要更复杂的设置（如使用 Attach 模式），
 * 在当前实现中作为 TODO 保留。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DebuggerIntegrationTest {

    private lateinit var testProgramJar: String
    private val kotlincPath = "/Applications/HBuilderX-Dev.app/Contents/HBuilderX/plugins/uniapp-runextension/kotlinc/bin/kotlinc"

    @BeforeAll
    fun setup() {
        compileTestProgram()
    }

    private fun compileTestProgram() {
        val sourceFile = File(javaClass.getResource("/TestProgram.kt")?.toURI()
            ?: throw IllegalStateException("TestProgram.kt not found"))

        val outputDir = File(System.getProperty("java.io.tmpdir"), "kotlin-debugger-test")
        outputDir.mkdirs()

        testProgramJar = File(outputDir, "TestProgram.jar").absolutePath

        val kotlinc = File(kotlincPath)
        val kotlincToUse = if (!kotlinc.exists()) {
            ProcessBuilder("which", "kotlinc")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().readText().trim()
                .takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("kotlinc not found")
        } else {
            kotlincPath
        }

        val process = ProcessBuilder(kotlincToUse, sourceFile.absolutePath, "-include-runtime", "-d", testProgramJar)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.inputStream.bufferedReader().readText()
            throw IllegalStateException("Failed to compile: $error")
        }
    }

    @AfterAll
    fun cleanup() {
        File(testProgramJar).delete()
    }

    // ==================== 断点管理测试 ====================

    @Test
    @Timeout(30)
    fun `test add and remove breakpoint`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(200) // Give time for VM to start

            // 添加断点
            val bp = session.addBreakpoint("TestProgram.kt", 14)
            assertNotNull(bp)
            assertEquals(1, bp.id)
            assertTrue(bp is Breakpoint.LineBreakpoint)
            assertEquals("TestProgram.kt", (bp as Breakpoint.LineBreakpoint).file)
            assertEquals(14, bp.line)

            // 列出断点
            val breakpoints = session.listBreakpoints()
            assertEquals(1, breakpoints.size)

            // 删除断点
            val removed = session.removeBreakpoint(bp.id)
            assertTrue(removed)

            // 确认已删除
            val afterRemove = session.listBreakpoints()
            assertEquals(0, afterRemove.size)

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test enable and disable breakpoint`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            // 添加断点
            val bp = session.addBreakpoint("TestProgram.kt", 12)

            // 禁用断点
            val disabled = session.disableBreakpoint(bp.id)
            assertTrue(disabled)

            val disabledBp = session.listBreakpoints().first()
            assertFalse(disabledBp.enabled)

            // 启用断点
            val enabled = session.enableBreakpoint(bp.id)
            assertTrue(enabled)

            val enabledBp = session.listBreakpoints().first()
            assertTrue(enabledBp.enabled)

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test multiple breakpoints`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            // 添加多个断点
            val bp1 = session.addBreakpoint("TestProgram.kt", 11)
            val bp2 = session.addBreakpoint("TestProgram.kt", 12)
            val bp3 = session.addBreakpoint("TestProgram.kt", 13)

            val breakpoints = session.listBreakpoints()
            assertEquals(3, breakpoints.size)

            // 删除中间的断点
            session.removeBreakpoint(bp2.id)

            val remaining = session.listBreakpoints()
            assertEquals(2, remaining.size)
            assertTrue(remaining.any { it.id == bp1.id })
            assertTrue(remaining.any { it.id == bp3.id })

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test breakpoint id uniqueness`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            val bp1 = session.addBreakpoint("TestProgram.kt", 11)
            val bp2 = session.addBreakpoint("TestProgram.kt", 12)
            val bp3 = session.addBreakpoint("TestProgram.kt", 13)

            // 每个断点应该有唯一的 ID
            val ids = setOf(bp1.id, bp2.id, bp3.id)
            assertEquals(3, ids.size)

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test remove nonexistent breakpoint`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            // 删除不存在的断点应该返回 false
            val removed = session.removeBreakpoint(999)
            assertFalse(removed)

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test enable nonexistent breakpoint`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            // 启用不存在的断点应该返回 false
            val enabled = session.enableBreakpoint(999)
            assertFalse(enabled)

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test disable nonexistent breakpoint`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            // 禁用不存在的断点应该返回 false
            val disabled = session.disableBreakpoint(999)
            assertFalse(disabled)

        } finally {
            session.stop()
        }
    }

    // ==================== 会话状态测试 ====================

    @Test
    @Timeout(30)
    fun `test session initial state`() {
        val session = createSession()
        assertEquals(SessionState.NOT_STARTED, session.getState())
    }

    @Test
    @Timeout(30)
    fun `test session state after start`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)
            assertEquals(SessionState.RUNNING, session.getState())
        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test session state after stop`() {
        val session = createSession()

        session.start()
        Thread.sleep(100)
        session.stop()

        assertEquals(SessionState.TERMINATED, session.getState())
    }

    @Test
    @Timeout(30)
    fun `test session suspend and resume`() {
        val session = createSession()

        try {
            session.start()
            Thread.sleep(100)

            // 暂停
            session.suspend()
            assertEquals(SessionState.SUSPENDED, session.getState())

            // 恢复
            session.resume()
            Thread.sleep(50)
            assertEquals(SessionState.RUNNING, session.getState())

        } finally {
            session.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `test is terminated helper`() {
        val session = createSession()

        assertFalse(session.isTerminated())

        session.start()
        Thread.sleep(100)
        assertFalse(session.isTerminated())

        session.stop()
        assertTrue(session.isTerminated())
    }

    // ==================== 辅助方法 ====================

    private fun createSession(): DebugSession {
        val target = DebugTarget.Launch(
            mainClass = "TestProgramKt",
            classpath = listOf(testProgramJar),
            suspend = false
        )
        return DebugSession(target)
    }
}
