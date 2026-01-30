package com.kotlindebugger.kotlin.inline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for InlineTypes data structures
 */
class InlineTypesTest {

    @Test
    fun `SourceLocation creation and access`() {
        val location = SourceLocation(
            sourcePath = "src/main/kotlin",
            sourceName = "Test.kt",
            lineNumber = 42,
            columnNumber = 10
        )

        assertEquals("src/main/kotlin", location.sourcePath)
        assertEquals("Test.kt", location.sourceName)
        assertEquals(42, location.lineNumber)
        assertEquals(10, location.columnNumber)
    }

    @Test
    fun `SourceLocation default column is zero`() {
        val location = SourceLocation(
            sourcePath = "path",
            sourceName = "Test.kt",
            lineNumber = 1
        )

        assertEquals(0, location.columnNumber)
    }

    @Test
    fun `InlineFrame creation with parent`() {
        val parentLocation = SourceLocation(
            sourcePath = "path",
            sourceName = "Parent.kt",
            lineNumber = 10
        )
        val parentFrame = InlineFrame(
            functionName = "parentFunction",
            sourceLocation = parentLocation,
            inlineDepth = 1,
            variableMapping = emptyMap(),
            originalLocation = null,
            parentFrame = null
        )

        val childLocation = SourceLocation(
            sourcePath = "path",
            sourceName = "Child.kt",
            lineNumber = 20
        )
        val childFrame = InlineFrame(
            functionName = "childFunction",
            sourceLocation = childLocation,
            inlineDepth = 2,
            variableMapping = emptyMap(),
            originalLocation = null,
            parentFrame = parentFrame
        )

        assertEquals("childFunction", childFrame.functionName)
        assertEquals(2, childFrame.inlineDepth)
        assertNotNull(childFrame.parentFrame)
        assertEquals("parentFunction", childFrame.parentFrame?.functionName)
    }

    @Test
    fun `InlineContext with empty frames`() {
        val context = InlineContext(
            inlineFrames = emptyList(),
            currentDepth = 0,
            capturedVariables = emptyMap()
        )

        assertTrue(context.inlineFrames.isEmpty())
        assertEquals(0, context.currentDepth)
        assertTrue(context.capturedVariables.isEmpty())
    }

    @Test
    fun `InlineContext with captured variables`() {
        val context = InlineContext(
            inlineFrames = emptyList(),
            currentDepth = 1,
            capturedVariables = mapOf("x" to 42, "y" to "hello")
        )

        assertEquals(2, context.capturedVariables.size)
        assertEquals(42, context.capturedVariables["x"])
        assertEquals("hello", context.capturedVariables["y"])
    }

    @Test
    fun `DebugVariable scopes`() {
        val localVar = DebugVariable(
            name = "localVar",
            type = "Int",
            value = 10,
            scope = VariableScope.LOCAL,
            inlineFrame = null
        )
        assertEquals(VariableScope.LOCAL, localVar.scope)

        val capturedVar = DebugVariable(
            name = "\$capturedVar",
            type = "String",
            value = "captured",
            scope = VariableScope.CAPTURED,
            inlineFrame = null
        )
        assertEquals(VariableScope.CAPTURED, capturedVar.scope)

        val receiverVar = DebugVariable(
            name = "\$this\$function",
            type = "MyClass",
            value = null,
            scope = VariableScope.RECEIVER,
            inlineFrame = null
        )
        assertEquals(VariableScope.RECEIVER, receiverVar.scope)

        val paramVar = DebugVariable(
            name = "param",
            type = "Function1",
            value = null,
            scope = VariableScope.PARAMETER,
            inlineFrame = null
        )
        assertEquals(VariableScope.PARAMETER, paramVar.scope)
    }

    @Test
    fun `LineMapping contains check`() {
        val mapping = LineMapping(
            inputStartLine = 10,
            inputLineCount = 5,
            outputStartLine = 100,
            outputLineCount = 5,
            repeatCount = 1
        )

        assertEquals(10, mapping.inputStartLine)
        assertEquals(5, mapping.inputLineCount)
        assertEquals(100, mapping.outputStartLine)
        assertEquals(5, mapping.outputLineCount)
        assertEquals(1, mapping.repeatCount)
    }

    @Test
    fun `SourceMapInfo creation`() {
        val lineMappings = listOf(
            LineMapping(1, 5, 10, 5),
            LineMapping(10, 3, 20, 3)
        )

        val sourceMapInfo = SourceMapInfo(
            stratum = "Kotlin",
            fileId = "1",
            path = "Test.kt",
            lineMappings = lineMappings
        )

        assertEquals("Kotlin", sourceMapInfo.stratum)
        assertEquals("1", sourceMapInfo.fileId)
        assertEquals("Test.kt", sourceMapInfo.path)
        assertEquals(2, sourceMapInfo.lineMappings.size)
    }

    @Test
    fun `BreakpointLocation with null inline frame`() {
        val sourceLocation = SourceLocation(
            sourcePath = "path",
            sourceName = "Test.kt",
            lineNumber = 42
        )

        // Note: bytecodeLocation requires a real JDI Location which we can't create in unit tests
        // This test verifies the data class structure
        assertNotNull(sourceLocation)
    }

    @Test
    fun `VariableScope enum values`() {
        val values = VariableScope.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(VariableScope.LOCAL))
        assertTrue(values.contains(VariableScope.CAPTURED))
        assertTrue(values.contains(VariableScope.PARAMETER))
        assertTrue(values.contains(VariableScope.RECEIVER))
    }
}
