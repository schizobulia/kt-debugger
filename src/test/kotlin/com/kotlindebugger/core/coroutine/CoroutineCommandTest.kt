package com.kotlindebugger.core.coroutine

import com.kotlindebugger.cli.command.CommandProcessor
import com.kotlindebugger.cli.command.CommandResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 协程命令行命令测试
 * 测试 coroutines 命令是否正确实现
 */
class CoroutineCommandTest {

    private val processor = CommandProcessor()

    @Test
    fun `coroutines command exists`() {
        val result = processor.process("coroutines")

        // 由于没有活跃的调试会话，应该返回错误
        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `help command includes coroutines`() {
        val result = processor.process("help")

        assertTrue(result is CommandResult.Message)
        val helpText = (result as CommandResult.Message).text
        
        // 确保帮助文本包含协程命令
        assertTrue(helpText.contains("Coroutines:"))
        assertTrue(helpText.contains("coroutines"))
        assertTrue(helpText.contains("List all coroutines"))
    }
}
