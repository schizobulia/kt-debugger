package com.kotlindebugger.cli.command

import com.kotlindebugger.cli.output.OutputFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class CommandProcessorTest {

    private lateinit var formatter: OutputFormatter
    private lateinit var processor: CommandProcessor

    @BeforeEach
    fun setup() {
        formatter = OutputFormatter(colorEnabled = false)
        processor = CommandProcessor(formatter)
    }

    @Test
    fun `help command returns help text`() {
        val result = processor.process("help")

        assertTrue(result is CommandResult.Message)
        val message = (result as CommandResult.Message).text
        assertTrue(message.contains("Kotlin Debugger Commands"))
        assertTrue(message.contains("run"))
        assertTrue(message.contains("breakpoint"))
        assertTrue(message.contains("continue"))
    }

    @Test
    fun `h is alias for help`() {
        val result = processor.process("h")

        assertTrue(result is CommandResult.Message)
        val message = (result as CommandResult.Message).text
        assertTrue(message.contains("Kotlin Debugger Commands"))
    }

    @Test
    fun `quit command returns exit`() {
        val result = processor.process("quit")
        assertTrue(result is CommandResult.Exit)
        assertTrue(processor.shouldExit())
    }

    @Test
    fun `q is alias for quit`() {
        val result = processor.process("q")
        assertTrue(result is CommandResult.Exit)
    }

    @Test
    fun `unknown command returns error`() {
        val result = processor.process("unknowncommand")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("Unknown command"))
    }

    @Test
    fun `empty command returns success`() {
        val result = processor.process("")
        assertTrue(result is CommandResult.Success)
    }

    @Test
    fun `whitespace only command returns success`() {
        val result = processor.process("   ")
        assertTrue(result is CommandResult.Success)
    }

    @Test
    fun `run without class returns error`() {
        val result = processor.process("run")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("Usage"))
    }

    @Test
    fun `break without session returns error`() {
        val result = processor.process("break Main.kt:10")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `break with invalid format returns error`() {
        // 先启动一个会话会更好，但这里只测试解析逻辑
        val result = processor.process("break invalid")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `continue without session returns error`() {
        val result = processor.process("continue")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `backtrace without session returns error`() {
        val result = processor.process("backtrace")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `locals without session returns error`() {
        val result = processor.process("locals")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `threads without session returns error`() {
        val result = processor.process("threads")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `status without session returns message`() {
        val result = processor.process("status")

        assertTrue(result is CommandResult.Message)
        val message = (result as CommandResult.Message).text
        assertTrue(message.contains("No active debug session"))
    }

    @Test
    fun `print without variable name returns error`() {
        val result = processor.process("print")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `frame without number returns error`() {
        val result = processor.process("frame")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `thread without id returns error`() {
        val result = processor.process("thread")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `delete without id returns error`() {
        val result = processor.process("delete")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `enable without id returns error`() {
        val result = processor.process("enable")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `disable without id returns error`() {
        val result = processor.process("disable")

        assertTrue(result is CommandResult.Error)
    }

    @Test
    fun `step command requires active session`() {
        val result = processor.process("step")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `next command requires active session`() {
        val result = processor.process("next")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `finish command requires active session`() {
        val result = processor.process("finish")

        assertTrue(result is CommandResult.Error)
        val error = (result as CommandResult.Error).message
        assertTrue(error.contains("No active debug session"))
    }

    @Test
    fun `command aliases work correctly`() {
        // Test various aliases
        val commands = mapOf(
            "b Main.kt:10" to "break",
            "d 1" to "delete",
            "l" to "list",
            "c" to "continue",
            "s" to "step",
            "n" to "next",
            "f" to "finish",
            "bt" to "backtrace",
            "fr 0" to "frame",
            "p x" to "print",
            "t 1" to "thread"
        )

        // 这些命令都应该失败（没有会话或未实现），但不应该返回 "Unknown command"
        commands.forEach { (cmd, _) ->
            val result = processor.process(cmd)
            if (result is CommandResult.Error) {
                assertFalse(result.message.contains("Unknown command"),
                    "Command '$cmd' should be recognized")
            }
        }
    }
}
