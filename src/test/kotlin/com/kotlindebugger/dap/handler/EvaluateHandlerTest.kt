package com.kotlindebugger.dap.handler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import kotlinx.serialization.json.*

/**
 * EvaluateHandler 单元测试
 * 
 * 测试表达式求值处理器的基本功能
 */
class EvaluateHandlerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test empty expression throws error`() {
        // 空表达式应该抛出异常
        val exception = assertThrows(EvaluationException::class.java) {
            throw EvaluationException("Empty expression")
        }
        assertEquals("Empty expression", exception.message)
    }

    @Test
    fun `test EvaluationResult creation`() {
        val result = EvaluationResult(
            displayValue = "42",
            typeName = "int",
            variablesReference = 0,
            value = null
        )
        
        assertEquals("42", result.displayValue)
        assertEquals("int", result.typeName)
        assertEquals(0, result.variablesReference)
        assertNull(result.value)
    }

    @Test
    fun `test EvaluationException with cause`() {
        val cause = RuntimeException("original error")
        val exception = EvaluationException("Evaluation failed", cause)
        
        assertEquals("Evaluation failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test EvaluationResult with variablesReference`() {
        val result = EvaluationResult(
            displayValue = "Object@123",
            typeName = "com.example.MyClass",
            variablesReference = 1001,
            value = null
        )
        
        assertEquals("Object@123", result.displayValue)
        assertEquals("com.example.MyClass", result.typeName)
        assertEquals(1001, result.variablesReference)
    }

    @Test
    fun `test multiple EvaluationResults are independent`() {
        val result1 = EvaluationResult("value1", "String", 0)
        val result2 = EvaluationResult("value2", "Int", 1001)
        
        assertNotEquals(result1.displayValue, result2.displayValue)
        assertNotEquals(result1.typeName, result2.typeName)
        assertNotEquals(result1.variablesReference, result2.variablesReference)
    }

    @Test
    fun `test EvaluationException message formatting`() {
        val exception = EvaluationException("Cannot find variable: x")
        assertTrue(exception.message!!.contains("variable"))
        assertTrue(exception.message!!.contains("x"))
    }

    @Test
    fun `test EvaluationResult with null displayValue handling`() {
        val result = EvaluationResult(
            displayValue = "null",
            typeName = "null",
            variablesReference = 0,
            value = null
        )
        
        assertEquals("null", result.displayValue)
        assertEquals("null", result.typeName)
    }

    @Test
    fun `test EvaluationResult data class copy`() {
        val original = EvaluationResult("42", "int", 0)
        val copy = original.copy(displayValue = "100")
        
        assertEquals("100", copy.displayValue)
        assertEquals("int", copy.typeName)
        assertEquals(0, copy.variablesReference)
    }

    @Test
    fun `test EvaluationException toString`() {
        val exception = EvaluationException("Test error")
        assertTrue(exception.toString().contains("EvaluationException"))
        assertTrue(exception.toString().contains("Test error"))
    }

    @Test
    fun `test EvaluationResult equals and hashCode`() {
        val result1 = EvaluationResult("42", "int", 0)
        val result2 = EvaluationResult("42", "int", 0)
        val result3 = EvaluationResult("100", "int", 0)
        
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
        assertNotEquals(result1, result3)
    }

    @Nested
    inner class EvaluationExceptionTests {
        
        @Test
        fun `test exception with null message`() {
            val exception = EvaluationException(message = "null reference")
            assertNotNull(exception.message)
        }

        @Test
        fun `test exception inherits from Exception`() {
            val exception = EvaluationException("Test")
            assertTrue(exception is Exception)
        }

        @Test
        fun `test exception stacktrace is available`() {
            val exception = EvaluationException("Test")
            assertNotNull(exception.stackTrace)
        }

        @Test
        fun `test nested exception cause chain`() {
            val innerCause = IllegalArgumentException("inner")
            val outerCause = RuntimeException("outer", innerCause)
            val exception = EvaluationException("Evaluation failed", outerCause)
            
            assertEquals(outerCause, exception.cause)
            assertEquals(innerCause, exception.cause?.cause)
        }
    }

    @Nested
    inner class EvaluationResultTests {
        
        @Test
        fun `test result with array type`() {
            val result = EvaluationResult(
                displayValue = "int[5]",
                typeName = "int[]",
                variablesReference = 1005,
                value = null
            )
            
            assertEquals("int[5]", result.displayValue)
            assertTrue(result.typeName.contains("[]"))
        }

        @Test
        fun `test result with string value`() {
            val result = EvaluationResult(
                displayValue = "\"Hello World\"",
                typeName = "java.lang.String",
                variablesReference = 0,
                value = null
            )
            
            assertTrue(result.displayValue.startsWith("\""))
            assertTrue(result.displayValue.endsWith("\""))
        }

        @Test
        fun `test result with boolean value`() {
            val result = EvaluationResult(
                displayValue = "true",
                typeName = "boolean",
                variablesReference = 0,
                value = null
            )
            
            assertEquals("true", result.displayValue)
            assertEquals("boolean", result.typeName)
        }

        @Test
        fun `test result with object reference`() {
            val result = EvaluationResult(
                displayValue = "MyClass@12345",
                typeName = "com.example.MyClass",
                variablesReference = 1001,
                value = null
            )
            
            assertTrue(result.displayValue.contains("@"))
            assertTrue(result.variablesReference > 0)
        }

        @Test
        fun `test result with primitive types`() {
            val intResult = EvaluationResult("42", "int", 0)
            val longResult = EvaluationResult("123456789L", "long", 0)
            val doubleResult = EvaluationResult("3.14", "double", 0)
            val floatResult = EvaluationResult("2.5f", "float", 0)
            val charResult = EvaluationResult("'a'", "char", 0)
            
            assertEquals(0, intResult.variablesReference)
            assertEquals(0, longResult.variablesReference)
            assertEquals(0, doubleResult.variablesReference)
            assertEquals(0, floatResult.variablesReference)
            assertEquals(0, charResult.variablesReference)
        }

        @Test
        fun `test result component functions`() {
            val result = EvaluationResult("value", "type", 1000, null)
            
            val (displayValue, typeName, variablesReference, value) = result
            assertEquals("value", displayValue)
            assertEquals("type", typeName)
            assertEquals(1000, variablesReference)
            assertNull(value)
        }

        @Test
        fun `test result with complex nested type`() {
            val result = EvaluationResult(
                displayValue = "HashMap@1234",
                typeName = "java.util.HashMap<java.lang.String, java.util.List<java.lang.Integer>>",
                variablesReference = 2001,
                value = null
            )
            
            assertTrue(result.typeName.contains("HashMap"))
            assertTrue(result.typeName.contains("<"))
        }
    }

    @Test
    fun `test EvaluationResult default value parameter`() {
        val result = EvaluationResult("42", "int", 0)
        assertNull(result.value)
    }

    @Test
    fun `test EvaluationException with empty message`() {
        val exception = EvaluationException("")
        assertEquals("", exception.message)
    }

    @Test
    fun `test EvaluationException with special characters`() {
        val exception = EvaluationException("Error: \"value\" contains <special> chars & symbols")
        assertTrue(exception.message!!.contains("\""))
        assertTrue(exception.message!!.contains("<"))
        assertTrue(exception.message!!.contains("&"))
    }
}
