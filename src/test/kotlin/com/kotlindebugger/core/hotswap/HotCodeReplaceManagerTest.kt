package com.kotlindebugger.core.hotswap

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * HotCodeReplaceManager 单元测试
 * Tests for the HotCodeReplaceManager and related classes
 * 
 * Note: Most tests for HotCodeReplaceManager require a live JDI VirtualMachine connection.
 * These tests focus on the data classes and result types that can be tested in isolation.
 */
class HotCodeReplaceManagerTest {

    @Test
    fun `test HotCodeReplaceResult Success contains correct data`() {
        val result = HotCodeReplaceResult.Success(
            reloadedClasses = listOf("com.example.MyClass", "com.example.AnotherClass"),
            message = "Hot code replacement completed successfully"
        )

        assertTrue(result is HotCodeReplaceResult.Success)
        assertEquals(2, result.reloadedClasses.size)
        assertEquals("com.example.MyClass", result.reloadedClasses[0])
        assertEquals("com.example.AnotherClass", result.reloadedClasses[1])
        assertTrue(result.message.contains("successfully"))
    }

    @Test
    fun `test HotCodeReplaceResult Failure contains error info`() {
        val result = HotCodeReplaceResult.Failure(
            errorMessage = "Class format error",
            failedClasses = listOf("com.example.BadClass")
        )

        assertTrue(result is HotCodeReplaceResult.Failure)
        assertEquals("Class format error", result.errorMessage)
        assertEquals(1, result.failedClasses.size)
        assertEquals("com.example.BadClass", result.failedClasses[0])
    }

    @Test
    fun `test HotCodeReplaceResult NotSupported contains reason`() {
        val result = HotCodeReplaceResult.NotSupported(
            reason = "The target VM does not support class redefinition"
        )

        assertTrue(result is HotCodeReplaceResult.NotSupported)
        assertTrue(result.reason.contains("not support"))
    }

    @Test
    fun `test ClassToRedefine equality`() {
        val bytes1 = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val bytes2 = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val bytes3 = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        val class1 = ClassToRedefine("com.example.MyClass", bytes1)
        val class2 = ClassToRedefine("com.example.MyClass", bytes2)
        val class3 = ClassToRedefine("com.example.MyClass", bytes3)
        val class4 = ClassToRedefine("com.example.OtherClass", bytes1)

        // Same class name and bytes should be equal
        assertEquals(class1, class2)
        assertEquals(class1.hashCode(), class2.hashCode())

        // Different bytes should not be equal
        assertNotEquals(class1, class3)

        // Different class name should not be equal
        assertNotEquals(class1, class4)
    }

    @Test
    fun `test ClassToRedefine contains correct data`() {
        val bytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val classToRedefine = ClassToRedefine("com.example.TestClass", bytes)

        assertEquals("com.example.TestClass", classToRedefine.className)
        assertArrayEquals(bytes, classToRedefine.classBytes)
    }

    @Test
    fun `test HotCodeReplaceCapabilities contains all flags`() {
        val capabilities = HotCodeReplaceCapabilities(
            canRedefineClasses = true,
            canAddMethod = false,
            canUnrestrictedlyRedefineClasses = false
        )

        assertTrue(capabilities.canRedefineClasses)
        assertFalse(capabilities.canAddMethod)
        assertFalse(capabilities.canUnrestrictedlyRedefineClasses)
    }

    @Test
    fun `test HotCodeReplaceCapabilities toString format`() {
        val capabilities = HotCodeReplaceCapabilities(
            canRedefineClasses = true,
            canAddMethod = true,
            canUnrestrictedlyRedefineClasses = false
        )

        val str = capabilities.toString()
        assertTrue(str.contains("Hot Code Replace Capabilities"))
        assertTrue(str.contains("Can redefine classes: true"))
        assertTrue(str.contains("Can add methods: true"))
        assertTrue(str.contains("Unrestricted redefinition: false"))
    }

    @Test
    fun `test HotCodeReplaceResult Success with empty reloaded classes`() {
        val result = HotCodeReplaceResult.Success(
            reloadedClasses = emptyList(),
            message = "No classes to redefine"
        )

        assertTrue(result is HotCodeReplaceResult.Success)
        assertTrue(result.reloadedClasses.isEmpty())
    }

    @Test
    fun `test HotCodeReplaceResult Failure with empty failed classes`() {
        val result = HotCodeReplaceResult.Failure(
            errorMessage = "Unknown error",
            failedClasses = emptyList()
        )

        assertTrue(result is HotCodeReplaceResult.Failure)
        assertTrue(result.failedClasses.isEmpty())
        assertEquals("Unknown error", result.errorMessage)
    }

    @Test
    fun `test sealed class HotCodeReplaceResult exhaustive when`() {
        val results = listOf<HotCodeReplaceResult>(
            HotCodeReplaceResult.Success(listOf("A"), "OK"),
            HotCodeReplaceResult.Failure("Error", listOf("B")),
            HotCodeReplaceResult.NotSupported("Not supported")
        )

        for (result in results) {
            val message = when (result) {
                is HotCodeReplaceResult.Success -> "success"
                is HotCodeReplaceResult.Failure -> "failure"
                is HotCodeReplaceResult.NotSupported -> "not supported"
            }
            assertNotNull(message)
        }
    }
}
