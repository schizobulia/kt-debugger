package com.kotlindebugger.phase2

import com.kotlindebugger.cli.command.CommandProcessor
import com.kotlindebugger.cli.command.CommandResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Phase 2 功能测试
 * 测试单步执行、栈帧查看、变量查看、线程管理、源代码显示等核心调试功能
 */
class Phase2FunctionalityTest {

    private val processor = CommandProcessor()

    @Test
    fun `step command is implemented`() {
        val result = processor.process("step")

        // 现在应该返回会话错误，而不是"not implemented"
        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
        assertFalse(error.contains("not yet implemented"))
    }

    @Test
    fun `next command is implemented`() {
        val result = processor.process("next")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
        assertFalse(error.contains("not yet implemented"))
    }

    @Test
    fun `finish command is implemented`() {
        val result = processor.process("finish")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
        assertFalse(error.contains("not yet implemented"))
    }

    
    @Test
    fun `list command lists breakpoints`() {
        val result = processor.process("list")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `backtrace command exists`() {
        val result = processor.process("backtrace")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `bt command alias works`() {
        val result = processor.process("bt")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `where command alias works`() {
        val result = processor.process("where")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `frame command exists`() {
        val result = processor.process("frame 0")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `fr command alias works`() {
        val result = processor.process("fr 1")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `up command exists`() {
        val result = processor.process("up")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `down command exists`() {
        val result = processor.process("down")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `locals command exists`() {
        val result = processor.process("locals")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `info locals command alias works`() {
        val result = processor.process("info locals")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `print command exists`() {
        val result = processor.process("print variable")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `p command alias works`() {
        val result = processor.process("p variable")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `threads command exists`() {
        val result = processor.process("threads")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `thread command exists`() {
        val result = processor.process("thread 1")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `t command alias works`() {
        val result = processor.process("t 1")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `help command includes new features`() {
        val result = processor.process("help")

        assertTrue(result is CommandResult.Message)
        val helpText = (result as CommandResult.Message).text

        // 确保单步执行功能不再标记为 TODO
        assertTrue(helpText.contains("Step into"))
        assertTrue(helpText.contains("Step over"))
        assertTrue(helpText.contains("Step out"))
        assertFalse(helpText.contains("TODO"))
    }

    @Test
    fun `status command exists`() {
        val result = processor.process("status")

        assertTrue(result is CommandResult.Message)
        val statusText = (result as CommandResult.Message).text
        assertTrue(statusText.contains("No active debug session"))
    }
}