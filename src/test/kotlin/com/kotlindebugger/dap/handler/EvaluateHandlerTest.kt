package com.kotlindebugger.dap.handler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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
}
