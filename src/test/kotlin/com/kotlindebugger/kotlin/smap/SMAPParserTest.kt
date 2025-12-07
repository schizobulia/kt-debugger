package com.kotlindebugger.kotlin.smap

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SMAPParserTest {

    @Test
    fun `parse simple SMAP`() {
        val smapString = """
            SMAP
            MainKt.kt
            Kotlin
            *S Kotlin
            *F
            + 1 Main.kt
            Main.kt
            *L
            1#1,10:1
            *E
        """.trimIndent()

        val smap = SMAPParser.parse(smapString)

        assertNotNull(smap)
        assertEquals("MainKt.kt", smap!!.outputFileName)
        assertEquals("Kotlin", smap.defaultStratum)
        assertEquals(1, smap.fileMappings.size)

        val fileMapping = smap.fileMappings[0]
        assertEquals("Main.kt", fileMapping.name)
        assertEquals(1, fileMapping.lineMappings.size)

        val lineMapping = fileMapping.lineMappings[0]
        assertEquals(1, lineMapping.sourceStart)
        assertEquals(1, lineMapping.destStart)
        assertEquals(10, lineMapping.range)
    }

    @Test
    fun `parse SMAP with multiple files`() {
        val smapString = """
            SMAP
            MainKt.class
            Kotlin
            *S Kotlin
            *F
            + 1 Main.kt
            Main.kt
            + 2 Utils.kt
            Utils.kt
            *L
            1#1,5:1
            10#2,3:6
            *E
        """.trimIndent()

        val smap = SMAPParser.parse(smapString)

        assertNotNull(smap)
        assertEquals(2, smap!!.fileMappings.size)

        // 验证第一个文件映射
        val file1 = smap.fileMappings.find { it.name == "Main.kt" }
        assertNotNull(file1)
        assertEquals(1, file1!!.lineMappings.size)
        assertEquals(1, file1.lineMappings[0].sourceStart)
        assertEquals(1, file1.lineMappings[0].destStart)
        assertEquals(5, file1.lineMappings[0].range)

        // 验证第二个文件映射
        val file2 = smap.fileMappings.find { it.name == "Utils.kt" }
        assertNotNull(file2)
        assertEquals(1, file2!!.lineMappings.size)
        assertEquals(10, file2.lineMappings[0].sourceStart)
        assertEquals(6, file2.lineMappings[0].destStart)
        assertEquals(3, file2.lineMappings[0].range)
    }

    @Test
    fun `findSourcePosition returns correct position`() {
        val smapString = """
            SMAP
            MainKt.class
            Kotlin
            *S Kotlin
            *F
            + 1 Main.kt
            Main.kt
            *L
            10#1,5:100
            *E
        """.trimIndent()

        val smap = SMAPParser.parse(smapString)!!

        // 测试目标行 100 -> 源行 10
        val result1 = smap.findSourcePosition(100)
        assertNotNull(result1)
        assertEquals("Main.kt", result1!!.sourceFile)
        assertEquals(10, result1.sourceLine)

        // 测试目标行 102 -> 源行 12
        val result2 = smap.findSourcePosition(102)
        assertNotNull(result2)
        assertEquals(12, result2!!.sourceLine)

        // 测试目标行 105 (超出范围)
        val result3 = smap.findSourcePosition(105)
        assertNull(result3)
    }

    @Test
    fun `findDestLines returns correct lines`() {
        val smapString = """
            SMAP
            MainKt.class
            Kotlin
            *S Kotlin
            *F
            + 1 Main.kt
            Main.kt
            *L
            10#1,5:100
            *E
        """.trimIndent()

        val smap = SMAPParser.parse(smapString)!!

        // 源行 10 -> 目标行 100
        val lines1 = smap.findDestLines("Main.kt", 10)
        assertEquals(listOf(100), lines1)

        // 源行 12 -> 目标行 102
        val lines2 = smap.findDestLines("Main.kt", 12)
        assertEquals(listOf(102), lines2)

        // 源行 20 (超出范围)
        val lines3 = smap.findDestLines("Main.kt", 20)
        assertTrue(lines3.isEmpty())
    }

    @Test
    fun `parse inline function SMAP`() {
        // 模拟内联函数的 SMAP
        val smapString = """
            SMAP
            MainKt.class
            Kotlin
            *S Kotlin
            *F
            + 1 Main.kt
            Main.kt
            + 2 Inline.kt
            Inline.kt
            *L
            1#1,5:1
            10#2,3:6
            6#1:9
            *E
        """.trimIndent()

        val smap = SMAPParser.parse(smapString)

        assertNotNull(smap)

        // 目标行 6 应该映射到 Inline.kt 的第 10 行
        val inlinePos = smap!!.findSourcePosition(6)
        assertNotNull(inlinePos)
        assertEquals("Inline.kt", inlinePos!!.sourceFile)
        assertEquals(10, inlinePos.sourceLine)

        // 目标行 9 应该映射到 Main.kt 的第 6 行
        val mainPos = smap.findSourcePosition(9)
        assertNotNull(mainPos)
        assertEquals("Main.kt", mainPos!!.sourceFile)
        assertEquals(6, mainPos.sourceLine)
    }

    @Test
    fun `parse empty SMAP returns null`() {
        val smap = SMAPParser.parse("")
        assertNull(smap)
    }

    @Test
    fun `parse invalid SMAP returns null`() {
        val smap = SMAPParser.parse("invalid content")
        assertNull(smap)
    }

    @Test
    fun `RangeMapping containsDest works correctly`() {
        val fileMapping = FileMapping(1, "Test.kt", "Test.kt")
        val rangeMapping = RangeMapping(
            sourceStart = 10,
            destStart = 100,
            range = 5,
            parent = fileMapping
        )

        assertTrue(rangeMapping.containsDest(100))
        assertTrue(rangeMapping.containsDest(104))
        assertFalse(rangeMapping.containsDest(99))
        assertFalse(rangeMapping.containsDest(105))
    }

    @Test
    fun `RangeMapping containsSource works correctly`() {
        val fileMapping = FileMapping(1, "Test.kt", "Test.kt")
        val rangeMapping = RangeMapping(
            sourceStart = 10,
            destStart = 100,
            range = 5,
            parent = fileMapping
        )

        assertTrue(rangeMapping.containsSource(10))
        assertTrue(rangeMapping.containsSource(14))
        assertFalse(rangeMapping.containsSource(9))
        assertFalse(rangeMapping.containsSource(15))
    }

    @Test
    fun `RangeMapping mapDestToSource works correctly`() {
        val fileMapping = FileMapping(1, "Test.kt", "Test.kt")
        val rangeMapping = RangeMapping(
            sourceStart = 10,
            destStart = 100,
            range = 5,
            parent = fileMapping
        )

        assertEquals(10, rangeMapping.mapDestToSource(100))
        assertEquals(12, rangeMapping.mapDestToSource(102))
        assertEquals(14, rangeMapping.mapDestToSource(104))
    }

    @Test
    fun `RangeMapping mapSourceToDest works correctly`() {
        val fileMapping = FileMapping(1, "Test.kt", "Test.kt")
        val rangeMapping = RangeMapping(
            sourceStart = 10,
            destStart = 100,
            range = 5,
            parent = fileMapping
        )

        assertEquals(100, rangeMapping.mapSourceToDest(10))
        assertEquals(102, rangeMapping.mapSourceToDest(12))
        assertEquals(104, rangeMapping.mapSourceToDest(14))
    }
}
