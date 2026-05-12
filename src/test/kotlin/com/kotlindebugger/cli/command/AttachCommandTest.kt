package com.kotlindebugger.cli.command

import com.kotlindebugger.cli.output.OutputFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 验证 attach 命令解析逻辑（suspend-on-attach 功能）
 */
class AttachCommandTest {

    private lateinit var formatter: OutputFormatter
    private lateinit var processor: CommandProcessor

    @BeforeEach
    fun setup() {
        formatter = OutputFormatter(colorEnabled = false)
        processor = CommandProcessor(formatter)
    }

    @Test
    fun `attach without host and port returns error`() {
        val result = processor.process("attach")
        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `attach with invalid port returns error`() {
        val result = processor.process("attach localhost:notaport")
        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("Invalid port"))
    }

    @Test
    fun `attach without colon separator returns error`() {
        val result = processor.process("attach localhost5005")
        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `help text includes attach with no-suspend option`() {
        val result = processor.process("help")
        assertTrue(result is CommandResult.Message)
        val text = (result as CommandResult.Message).text
        assertTrue(text.contains("attach"), "Help should mention attach command")
        assertTrue(text.contains("--no-suspend"), "Help should mention --no-suspend flag")
    }

    @Test
    fun `continue without session returns error`() {
        val result = processor.process("continue")
        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `attach without active session fails gracefully`() {
        // 无法连接到不存在的 JVM，应返回错误而不是崩溃
        val result = processor.process("attach localhost:19999")
        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("Failed to attach") || error.contains("refused") || error.isNotBlank())
    }
}
