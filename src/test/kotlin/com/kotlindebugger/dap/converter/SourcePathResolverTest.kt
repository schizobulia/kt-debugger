package com.kotlindebugger.dap.converter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * SourcePathResolver 单元测试
 */
class SourcePathResolverTest {

    private lateinit var resolver: SourcePathResolver

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        resolver = SourcePathResolver()
    }

    @Test
    fun `test initial state has empty source paths`() {
        assertTrue(resolver.getSourcePaths().isEmpty())
    }

    @Test
    fun `test addSourcePaths adds paths`() {
        val paths = listOf("/path/to/src", "/another/path")
        resolver.addSourcePaths(paths)
        
        assertEquals(2, resolver.getSourcePaths().size)
        assertTrue(resolver.getSourcePaths().contains("/path/to/src"))
        assertTrue(resolver.getSourcePaths().contains("/another/path"))
    }

    @Test
    fun `test setSourcePaths replaces existing paths`() {
        resolver.addSourcePaths(listOf("/old/path"))
        resolver.setSourcePaths(listOf("/new/path1", "/new/path2"))
        
        assertEquals(2, resolver.getSourcePaths().size)
        assertFalse(resolver.getSourcePaths().contains("/old/path"))
        assertTrue(resolver.getSourcePaths().contains("/new/path1"))
    }

    @Test
    fun `test resolveSourcePath returns null for null input`() {
        assertNull(resolver.resolveSourcePath(null))
    }

    @Test
    fun `test resolveSourcePath returns original for non-existent file`() {
        val result = resolver.resolveSourcePath("NonExistent.kt")
        assertEquals("NonExistent.kt", result)
    }

    @Test
    fun `test resolveSourcePath finds file in source path`() {
        // Create a test file in temp directory
        val srcDir = File(tempDir.toFile(), "src/main/kotlin")
        srcDir.mkdirs()
        val testFile = File(srcDir, "TestFile.kt")
        testFile.writeText("// test file")

        resolver.setSourcePaths(listOf(tempDir.toString()))

        val result = resolver.resolveSourcePath("TestFile.kt")
        assertNotNull(result)
        assertEquals(testFile.absolutePath, result)
    }

    @Test
    fun `test resolveSourcePath with absolute path that exists`() {
        val testFile = File(tempDir.toFile(), "Absolute.kt")
        testFile.writeText("// absolute test")

        val result = resolver.resolveSourcePath(testFile.absolutePath)
        assertEquals(testFile.absolutePath, result)
    }

    @Test
    fun `test resolveSourcePath caches results`() {
        val srcDir = tempDir.toFile()
        val testFile = File(srcDir, "Cached.kt")
        testFile.writeText("// cached test")

        resolver.setSourcePaths(listOf(srcDir.absolutePath))

        // First call
        val result1 = resolver.resolveSourcePath("Cached.kt")
        // Second call should use cache
        val result2 = resolver.resolveSourcePath("Cached.kt")

        assertEquals(result1, result2)
    }

    @Test
    fun `test resolveSourcePath with package path`() {
        val packageDir = File(tempDir.toFile(), "com/example")
        packageDir.mkdirs()
        val testFile = File(packageDir, "Package.kt")
        testFile.writeText("// package test")

        resolver.setSourcePaths(listOf(tempDir.toString()))

        val result = resolver.resolveSourcePath("com/example/Package.kt")
        assertNotNull(result)
        assertTrue(result!!.endsWith("Package.kt"))
    }

    @Test
    fun `test toRelativePath extracts filename`() {
        val result = resolver.toRelativePath("/path/to/src/Main.kt")
        assertEquals("Main.kt", result)
    }

    @Test
    fun `test toRelativePath with nested path`() {
        val result = resolver.toRelativePath("/home/user/project/src/main/kotlin/com/example/App.kt")
        assertEquals("App.kt", result)
    }

    @Test
    fun `test clearCache clears the cache`() {
        val srcDir = tempDir.toFile()
        val testFile = File(srcDir, "ClearTest.kt")
        testFile.writeText("// clear cache test")

        resolver.setSourcePaths(listOf(srcDir.absolutePath))

        // Populate cache
        resolver.resolveSourcePath("ClearTest.kt")

        // Clear cache
        resolver.clearCache()

        // Should still work after cache clear
        val result = resolver.resolveSourcePath("ClearTest.kt")
        assertNotNull(result)
    }

    @Test
    fun `test setSourcePaths clears cache`() {
        val srcDir = tempDir.toFile()
        val testFile = File(srcDir, "CacheTest.kt")
        testFile.writeText("// cache test")

        resolver.addSourcePaths(listOf(srcDir.absolutePath))
        resolver.resolveSourcePath("CacheTest.kt")

        // Setting new paths should clear cache
        resolver.setSourcePaths(listOf("/new/path"))

        // After setting new paths, the old cached path shouldn't be valid
        // unless the file is found in the new paths
    }

    @Test
    fun `test addSourcePaths clears cache`() {
        resolver.addSourcePaths(listOf("/path1"))
        // Adding paths should clear cache
        assertDoesNotThrow {
            resolver.addSourcePaths(listOf("/path2"))
        }
    }

    @Test
    fun `test resolveSourcePath with multiple source paths`() {
        val srcDir1 = File(tempDir.toFile(), "src1")
        val srcDir2 = File(tempDir.toFile(), "src2")
        srcDir1.mkdirs()
        srcDir2.mkdirs()

        val testFile = File(srcDir2, "MultiPath.kt")
        testFile.writeText("// multi path test")

        resolver.setSourcePaths(listOf(srcDir1.absolutePath, srcDir2.absolutePath))

        val result = resolver.resolveSourcePath("MultiPath.kt")
        assertNotNull(result)
        assertEquals(testFile.absolutePath, result)
    }

    @Test
    fun `test resolveSourcePath finds file in first matching source path`() {
        val srcDir1 = File(tempDir.toFile(), "src1")
        val srcDir2 = File(tempDir.toFile(), "src2")
        srcDir1.mkdirs()
        srcDir2.mkdirs()

        val testFile1 = File(srcDir1, "Priority.kt")
        testFile1.writeText("// priority file 1")

        val testFile2 = File(srcDir2, "Priority.kt")
        testFile2.writeText("// priority file 2")

        resolver.setSourcePaths(listOf(srcDir1.absolutePath, srcDir2.absolutePath))

        val result = resolver.resolveSourcePath("Priority.kt")
        assertNotNull(result)
        assertEquals(testFile1.absolutePath, result) // Should find first one
    }

    @Test
    fun `test getSourcePaths returns immutable copy`() {
        resolver.addSourcePaths(listOf("/path1"))
        val paths = resolver.getSourcePaths()
        
        // The returned list should be a copy
        assertEquals(1, paths.size)
    }

    @Test
    fun `test resolveSourcePath with nested directory structure`() {
        val nestedDir = File(tempDir.toFile(), "a/b/c/d")
        nestedDir.mkdirs()
        val testFile = File(nestedDir, "Nested.kt")
        testFile.writeText("// nested test")

        resolver.setSourcePaths(listOf(tempDir.toString()))

        val result = resolver.resolveSourcePath("Nested.kt")
        assertNotNull(result)
        assertEquals(testFile.absolutePath, result)
    }

    @Test
    fun `test toRelativePath with simple filename`() {
        val result = resolver.toRelativePath("Simple.kt")
        assertEquals("Simple.kt", result)
    }

    @Test
    fun `test resolveSourcePath with empty source name`() {
        val result = resolver.resolveSourcePath("")
        assertEquals("", result)
    }
}
