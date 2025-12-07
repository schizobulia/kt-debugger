package com.kotlindebugger.cli.output

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OutputFormatterEnhancementTest {
    private val formatter = OutputFormatter(true)

    @Test
    fun `test highlightBreakpoint`() {
        val result = formatter.highlightBreakpoint("Main.kt", 42)
        assertTrue(result.contains("BREAKPOINT HIT"))
        assertTrue(result.contains("Main.kt"))
        assertTrue(result.contains("42"))
    }

    @Test
    fun `test highlightStep`() {
        val result = formatter.highlightStep("Main.kt:50")
        assertTrue(result.contains("➜"))
        assertTrue(result.contains("Stepped to"))
        assertTrue(result.contains("Main.kt:50"))
    }

    @Test
    fun `test highlightPrompt`() {
        val result = formatter.highlightPrompt()
        assertTrue(result.contains("(kdb)"))
    }

    @Test
    fun `test hint`() {
        val result = formatter.hint("continue | step | next")
        assertTrue(result.contains("💡"))
        assertTrue(result.contains("Hint:"))
        assertTrue(result.contains("continue | step | next"))
    }

    @Test
    fun `test disabled color`() {
        val noColorFormatter = OutputFormatter(false)
        val result = noColorFormatter.highlightBreakpoint("Test.kt", 10)
        // Should not contain ANSI codes when color is disabled
        assertFalse(result.contains("\u001B["))
    }
}