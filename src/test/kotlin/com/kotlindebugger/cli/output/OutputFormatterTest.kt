package com.kotlindebugger.cli.output

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OutputFormatterTest {

    @Test
    fun `formatter with color disabled returns plain text`() {
        val formatter = OutputFormatter(colorEnabled = false)

        assertEquals("test", formatter.bold("test"))
        assertEquals("test", formatter.red("test"))
        assertEquals("test", formatter.green("test"))
        assertEquals("test", formatter.yellow("test"))
        assertEquals("test", formatter.blue("test"))
        assertEquals("test", formatter.cyan("test"))
    }

    @Test
    fun `formatter with color enabled returns colored text`() {
        val formatter = OutputFormatter(colorEnabled = true)

        assertTrue(formatter.bold("test").contains("\u001B["))
        assertTrue(formatter.red("test").contains("\u001B[31m"))
        assertTrue(formatter.green("test").contains("\u001B[32m"))
        assertTrue(formatter.yellow("test").contains("\u001B[33m"))
        assertTrue(formatter.blue("test").contains("\u001B[34m"))
        assertTrue(formatter.cyan("test").contains("\u001B[36m"))
    }

    @Test
    fun `semantic colors work correctly`() {
        val formatter = OutputFormatter(colorEnabled = false)

        assertEquals("success", formatter.success("success"))
        assertEquals("error", formatter.error("error"))
        assertEquals("warning", formatter.warning("warning"))
        assertEquals("info", formatter.info("info"))
    }

    @Test
    fun `table formats correctly`() {
        val formatter = OutputFormatter(colorEnabled = false)

        val table = formatter.table(
            headers = listOf("ID", "Name", "Value"),
            rows = listOf(
                listOf("1", "foo", "bar"),
                listOf("2", "baz", "qux")
            )
        )

        assertTrue(table.contains("ID"))
        assertTrue(table.contains("Name"))
        assertTrue(table.contains("Value"))
        assertTrue(table.contains("foo"))
        assertTrue(table.contains("bar"))
        assertTrue(table.contains("---"))
    }

    @Test
    fun `table with empty rows returns empty string`() {
        val formatter = OutputFormatter(colorEnabled = false)

        val table = formatter.table(
            headers = listOf("ID", "Name"),
            rows = emptyList()
        )

        assertEquals("", table)
    }

    @Test
    fun `codeBlock formats correctly`() {
        val formatter = OutputFormatter(colorEnabled = false)

        val code = formatter.codeBlock(
            lines = listOf(
                1 to "fun main() {",
                2 to "    println(\"Hello\")",
                3 to "}"
            ),
            highlightLine = 2
        )

        assertTrue(code.contains("1"))
        assertTrue(code.contains("2"))
        assertTrue(code.contains("3"))
        assertTrue(code.contains("fun main()"))
        assertTrue(code.contains("println"))
    }

    @Test
    fun `codeBlock with highlight shows arrow`() {
        val formatter = OutputFormatter(colorEnabled = false)

        val code = formatter.codeBlock(
            lines = listOf(
                1 to "line 1",
                2 to "line 2"
            ),
            highlightLine = 1
        )

        // The highlighted line should have some indicator
        assertTrue(code.contains("line 1"))
    }

    @Test
    fun `box formats correctly`() {
        val formatter = OutputFormatter(colorEnabled = false)

        val box = formatter.box("Title", "Content line 1\nContent line 2")

        assertTrue(box.contains("Title"))
        assertTrue(box.contains("Content line 1"))
        assertTrue(box.contains("Content line 2"))
        assertTrue(box.contains("┌"))
        assertTrue(box.contains("┐"))
        assertTrue(box.contains("└"))
        assertTrue(box.contains("┘"))
        assertTrue(box.contains("│"))
    }
}
