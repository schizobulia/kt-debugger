package com.kotlindebugger.dap.handler

import com.kotlindebugger.dap.DAPServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 新增 DAP 功能单元测试
 */
class NewFeaturesHandlerTest {

    private lateinit var server: DAPServer

    @BeforeEach
    fun setup() {
        val input = ByteArrayInputStream(ByteArray(0))
        val output = ByteArrayOutputStream()
        server = DAPServer(input, output)
    }

    // TerminateHandler Tests

    @Test
    fun `terminate handler has correct command`() {
        val handler = TerminateHandler(server)
        assertEquals("terminate", handler.command)
    }

    @Test
    fun `terminate handler succeeds without session`() = runBlocking {
        val handler = TerminateHandler(server)
        var caughtException: Exception? = null
        try {
            handler.handle(null, null)
        } catch (e: Exception) {
            caughtException = e
        }
        assertNull(caughtException, "Terminate handler should not throw when no session")
    }

    @Test
    fun `terminate handler returns null`() = runBlocking {
        val handler = TerminateHandler(server)
        val result = handler.handle(null, null)
        assertNull(result)
    }

    // BreakpointLocationsHandler Tests

    @Test
    fun `breakpointLocations handler has correct command`() {
        val handler = BreakpointLocationsHandler(server)
        assertEquals("breakpointLocations", handler.command)
    }

    @Test
    fun `breakpointLocations handler returns all lines in range when no session`() = runBlocking {
        val handler = BreakpointLocationsHandler(server)
        val args = buildJsonObject {
            putJsonObject("source") { put("path", "/path/to/Main.kt") }
            put("line", 10)
            put("endLine", 15)
        }
        val result = handler.handle(args, null)
        val breakpoints = result!!.jsonObject["breakpoints"]!!.jsonArray
        assertEquals(6, breakpoints.size)
        val lines = breakpoints.map { it.jsonObject["line"]!!.jsonPrimitive.int }
        assertEquals(listOf(10, 11, 12, 13, 14, 15), lines)
    }

    @Test
    fun `breakpointLocations handler handles single line`() = runBlocking {
        val handler = BreakpointLocationsHandler(server)
        val args = buildJsonObject {
            putJsonObject("source") { put("path", "/path/to/Main.kt") }
            put("line", 42)
        }
        val result = handler.handle(args, null)
        val breakpoints = result!!.jsonObject["breakpoints"]!!.jsonArray
        assertEquals(1, breakpoints.size)
        assertEquals(42, breakpoints[0].jsonObject["line"]!!.jsonPrimitive.int)
    }

    @Test
    fun `breakpointLocations handler includes column in response`() = runBlocking {
        val handler = BreakpointLocationsHandler(server)
        val args = buildJsonObject {
            putJsonObject("source") { put("path", "/path/to/Test.kt") }
            put("line", 5)
            put("endLine", 7)
        }
        val result = handler.handle(args, null)
        val breakpoints = result!!.jsonObject["breakpoints"]!!.jsonArray
        breakpoints.forEach { bp ->
            assertTrue(bp.jsonObject.containsKey("column"))
            assertEquals(1, bp.jsonObject["column"]!!.jsonPrimitive.int)
        }
    }

    // LoadedSourcesHandler Tests

    @Test
    fun `loadedSources handler has correct command`() {
        val handler = LoadedSourcesHandler(server)
        assertEquals("loadedSources", handler.command)
    }

    @Test
    fun `loadedSources handler returns empty sources without session`() = runBlocking {
        val handler = LoadedSourcesHandler(server)
        val result = handler.handle(null, null)
        assertNotNull(result)
        val sources = result!!.jsonObject["sources"]!!.jsonArray
        assertEquals(0, sources.size)
    }

    @Test
    fun `loadedSources handler returns valid JSON`() = runBlocking {
        val handler = LoadedSourcesHandler(server)
        val result = handler.handle(null, null)
        assertNotNull(result)
        assertNotNull(result!!.jsonObject)
    }

    // hitCondition Parsing Tests

    @Test
    fun `parseHitCondition returns 0 for null`() {
        assertEquals(0, parseHitCondition(null))
    }

    @Test
    fun `parseHitCondition returns 0 for blank string`() {
        assertEquals(0, parseHitCondition(""))
        assertEquals(0, parseHitCondition("  "))
    }

    @Test
    fun `parseHitCondition parses plain integer`() {
        assertEquals(5, parseHitCondition("5"))
        assertEquals(1, parseHitCondition("1"))
        assertEquals(100, parseHitCondition("100"))
    }

    @Test
    fun `parseHitCondition parses equality condition`() {
        assertEquals(5, parseHitCondition("== 5"))
        assertEquals(3, parseHitCondition("=3"))
        assertEquals(10, parseHitCondition("= 10"))
    }

    @Test
    fun `parseHitCondition parses greater-than condition`() {
        assertEquals(5, parseHitCondition(">= 5"))
        assertEquals(3, parseHitCondition("> 3"))
    }

    @Test
    fun `parseHitCondition handles whitespace around number`() {
        assertEquals(7, parseHitCondition("  7  "))
    }

    // New Capabilities in InitializeHandler

    @Test
    fun `initialize handler declares supportsFunctionBreakpoints true`() = runBlocking {
        val handler = InitializeHandler()
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"supportsFunctionBreakpoints\":true"))
    }

    @Test
    fun `initialize handler declares supportsHitConditionalBreakpoints true`() = runBlocking {
        val handler = InitializeHandler()
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"supportsHitConditionalBreakpoints\":true"))
    }

    @Test
    fun `initialize handler declares supportsBreakpointLocationsRequest true`() = runBlocking {
        val handler = InitializeHandler()
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"supportsBreakpointLocationsRequest\":true"))
    }

    @Test
    fun `initialize handler declares supportsLoadedSourcesRequest true`() = runBlocking {
        val handler = InitializeHandler()
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"supportsLoadedSourcesRequest\":true"))
    }

    @Test
    fun `initialize handler declares supportsTerminateRequest true`() = runBlocking {
        val handler = InitializeHandler()
        val result = handler.handle(null, null)
        val resultStr = result.toString()
        assertTrue(resultStr.contains("\"supportsTerminateRequest\":true"))
    }
}
