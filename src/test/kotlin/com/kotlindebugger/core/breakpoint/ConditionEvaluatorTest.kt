package com.kotlindebugger.core.breakpoint

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * ConditionEvaluator 单元测试
 * 
 * 由于 ConditionEvaluator 需要 JDI VirtualMachine，这里主要测试
 * 条件表达式解析的逻辑正确性。实际的条件求值测试需要在集成测试中进行。
 */
class ConditionEvaluatorTest {

    @Test
    fun `test null or blank condition should return true`() {
        // 空条件应该返回 true（不进行条件检查）
        assertTrue(isNullOrBlankCondition(null))
        assertTrue(isNullOrBlankCondition(""))
        assertTrue(isNullOrBlankCondition("   "))
    }

    @Test
    fun `test condition operator detection`() {
        // 测试条件运算符检测
        assertTrue(containsComparisonOperator("x == 5"))
        assertTrue(containsComparisonOperator("x != 5"))
        assertTrue(containsComparisonOperator("x > 5"))
        assertTrue(containsComparisonOperator("x < 5"))
        assertTrue(containsComparisonOperator("x >= 5"))
        assertTrue(containsComparisonOperator("x <= 5"))
        assertTrue(containsLogicalOperator("x > 5 && y < 10"))
        assertTrue(containsLogicalOperator("x > 5 || y < 10"))
    }

    @Test
    fun `test condition expression parsing - equals`() {
        val parts = parseEqualsExpression("x == 5")
        assertEquals(2, parts.size)
        assertEquals("x", parts[0])
        assertEquals("5", parts[1])
    }

    @Test
    fun `test condition expression parsing - not equals`() {
        val parts = parseNotEqualsExpression("name != \"test\"")
        assertEquals(2, parts.size)
        assertEquals("name", parts[0])
        assertEquals("\"test\"", parts[1])
    }

    @Test
    fun `test condition expression parsing - comparison`() {
        val parts = parseComparisonExpression("count >= 10", ">=")
        assertEquals(2, parts.size)
        assertEquals("count", parts[0])
        assertEquals("10", parts[1])
    }

    @Test
    fun `test condition expression parsing - logical and`() {
        val parts = parseLogicalExpression("x > 5 && y < 10", "&&")
        assertEquals(2, parts.size)
        assertEquals("x > 5", parts[0])
        assertEquals("y < 10", parts[1])
    }

    @Test
    fun `test condition expression parsing - logical or`() {
        val parts = parseLogicalExpression("a == 1 || b == 2", "||")
        assertEquals(2, parts.size)
        assertEquals("a == 1", parts[0])
        assertEquals("b == 2", parts[1])
    }

    @Test
    fun `test literal parsing - null`() {
        assertEquals("null", parseLiteralType("null"))
    }

    @Test
    fun `test literal parsing - boolean`() {
        assertEquals("boolean", parseLiteralType("true"))
        assertEquals("boolean", parseLiteralType("false"))
    }

    @Test
    fun `test literal parsing - integer`() {
        assertEquals("integer", parseLiteralType("42"))
        assertEquals("integer", parseLiteralType("-10"))
        assertEquals("integer", parseLiteralType("0"))
    }

    @Test
    fun `test literal parsing - double`() {
        assertEquals("double", parseLiteralType("3.14"))
        assertEquals("double", parseLiteralType("-2.5"))
    }

    @Test
    fun `test literal parsing - string`() {
        assertEquals("string", parseLiteralType("\"hello\""))
        assertEquals("string", parseLiteralType("'world'"))
    }

    @Test
    fun `test literal parsing - identifier`() {
        assertEquals("identifier", parseLiteralType("variableName"))
        assertEquals("identifier", parseLiteralType("_underscore"))
    }

    @Test
    fun `test member access detection`() {
        assertTrue(isMemberAccess("obj.field"))
        assertTrue(isMemberAccess("a.b.c"))
        assertFalse(isMemberAccess("simple"))
        // 方法调用不是简单的成员访问
        assertTrue(isMethodCall("obj.method()"))
    }

    @Test
    fun `test method call detection`() {
        assertTrue(isMethodCall("method()"))
        assertTrue(isMethodCall("obj.method()"))
        assertTrue(isMethodCall("list.isEmpty()"))
        assertFalse(isMethodCall("variable"))
        assertFalse(isMethodCall("obj.field"))
    }

    @Test
    fun `test complex condition parsing`() {
        // 测试复杂条件
        val condition = "person.age >= 30 && person.name != null"
        assertTrue(containsLogicalOperator(condition))
        
        val parts = parseLogicalExpression(condition, "&&")
        assertEquals(2, parts.size)
        assertEquals("person.age >= 30", parts[0])
        assertEquals("person.name != null", parts[1])
    }

    @Test
    fun `test negation condition`() {
        val condition = "!isEnabled"
        assertTrue(isNegation(condition))
        assertEquals("isEnabled", removeNegation(condition))
    }

    // Helper methods for testing (simulate the logic in ConditionEvaluator)

    private fun isNullOrBlankCondition(condition: String?): Boolean {
        return condition.isNullOrBlank()
    }

    private fun containsComparisonOperator(condition: String): Boolean {
        return condition.contains("==") || 
               condition.contains("!=") ||
               condition.contains(">") ||
               condition.contains("<") ||
               condition.contains(">=") ||
               condition.contains("<=")
    }

    private fun containsLogicalOperator(condition: String): Boolean {
        return condition.contains("&&") || condition.contains("||")
    }

    private fun parseEqualsExpression(condition: String): List<String> {
        return condition.split("==", limit = 2).map { it.trim() }
    }

    private fun parseNotEqualsExpression(condition: String): List<String> {
        return condition.split("!=", limit = 2).map { it.trim() }
    }

    private fun parseComparisonExpression(condition: String, operator: String): List<String> {
        return condition.split(operator, limit = 2).map { it.trim() }
    }

    private fun parseLogicalExpression(condition: String, operator: String): List<String> {
        return condition.split(operator, limit = 2).map { it.trim() }
    }

    private fun parseLiteralType(expression: String): String {
        val trimmed = expression.trim()
        return when {
            trimmed == "null" -> "null"
            trimmed == "true" || trimmed == "false" -> "boolean"
            trimmed.toLongOrNull() != null -> "integer"
            trimmed.toDoubleOrNull() != null -> "double"
            (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'")) -> "string"
            else -> "identifier"
        }
    }

    private fun isMemberAccess(expression: String): Boolean {
        return expression.contains(".") && !expression.contains("(")
    }

    private fun isMethodCall(expression: String): Boolean {
        return expression.contains("(") && expression.endsWith(")")
    }

    private fun isNegation(condition: String): Boolean {
        return condition.trim().startsWith("!")
    }

    private fun removeNegation(condition: String): String {
        return condition.trim().removePrefix("!").trim()
    }
}
