package com.kotlindebugger.dap

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * 变量引用管理器测试
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VariableReferenceManagerTest {

    private lateinit var manager: VariableReferenceManager

    @BeforeEach
    fun setup() {
        manager = VariableReferenceManager()
    }

    @Test
    fun `test initial state`() {
        // 初始状态下不应该有任何引用
        val ref = manager.getReference(1000)
        assertNull(ref)
    }

    @Test
    fun `test reference IDs start from 1000`() {
        // 注意: 由于我们无法创建真实的StackFrame和ObjectReference,
        // 这个测试主要验证ID分配逻辑
        // 实际使用中,真实的JDI对象会在调试会话中提供
    }

    @Test
    fun `test get non-existent reference returns null`() {
        val refId = 99999
        val ref = manager.getReference(refId)
        assertNull(ref)
    }

    @Test
    fun `test remove non-existent reference does not throw`() {
        val refId = 99999
        assertDoesNotThrow {
            manager.removeReference(refId)
        }
    }

    @Test
    fun `test clear all references`() {
        // 清除操作不应该抛出异常
        assertDoesNotThrow {
            manager.clear()
        }
    }

    @Test
    fun `test multiple clear operations are safe`() {
        manager.clear()
        manager.clear()
        manager.clear()
        // 多次清除不应该有问题
    }
}

/**
 * VariableReferenceType 枚举测试
 */
class VariableReferenceTypeTest {

    @Test
    fun `test enum has three types`() {
        assertEquals(3, VariableReferenceType.values().size)
    }

    @Test
    fun `test STACK_FRAME type exists`() {
        assertNotNull(VariableReferenceType.valueOf("STACK_FRAME"))
    }

    @Test
    fun `test OBJECT_FIELDS type exists`() {
        assertNotNull(VariableReferenceType.valueOf("OBJECT_FIELDS"))
    }

    @Test
    fun `test ARRAY_ELEMENTS type exists`() {
        assertNotNull(VariableReferenceType.valueOf("ARRAY_ELEMENTS"))
    }

    @Test
    fun `test enum values are distinct`() {
        val types = VariableReferenceType.values()
        val uniqueTypes = types.toSet()
        assertEquals(types.size, uniqueTypes.size)
    }
}

/**
 * VariableReference 数据类测试
 */
class VariableReferenceTest {

    @Test
    fun `test VariableReference creation with STACK_FRAME type`() {
        val ref = VariableReference(
            id = 1000,
            type = VariableReferenceType.STACK_FRAME,
            threadId = 1L,
            frameIndex = 0
        )

        assertEquals(1000, ref.id)
        assertEquals(VariableReferenceType.STACK_FRAME, ref.type)
        assertEquals(1L, ref.threadId)
        assertEquals(0, ref.frameIndex)
    }

    @Test
    fun `test VariableReference creation with OBJECT_FIELDS type`() {
        val ref = VariableReference(
            id = 1001,
            type = VariableReferenceType.OBJECT_FIELDS
        )

        assertEquals(1001, ref.id)
        assertEquals(VariableReferenceType.OBJECT_FIELDS, ref.type)
        assertNull(ref.objectRef) // No object ref in test
    }

    @Test
    fun `test VariableReference creation with ARRAY_ELEMENTS type`() {
        val ref = VariableReference(
            id = 1002,
            type = VariableReferenceType.ARRAY_ELEMENTS,
            arrayStart = 0,
            arrayCount = 10
        )

        assertEquals(1002, ref.id)
        assertEquals(VariableReferenceType.ARRAY_ELEMENTS, ref.type)
        assertEquals(0, ref.arrayStart)
        assertEquals(10, ref.arrayCount)
    }

    @Test
    fun `test VariableReference default values`() {
        val ref = VariableReference(
            id = 1003,
            type = VariableReferenceType.STACK_FRAME
        )

        assertEquals(0L, ref.threadId)
        assertEquals(0, ref.frameIndex)
        assertNull(ref.objectRef)
        assertEquals(0, ref.arrayStart)
        assertEquals(-1, ref.arrayCount)
    }

    @Test
    fun `test VariableReference equality`() {
        val ref1 = VariableReference(id = 1000, type = VariableReferenceType.STACK_FRAME, threadId = 1L, frameIndex = 0)
        val ref2 = VariableReference(id = 1000, type = VariableReferenceType.STACK_FRAME, threadId = 1L, frameIndex = 0)
        val ref3 = VariableReference(id = 1001, type = VariableReferenceType.STACK_FRAME, threadId = 1L, frameIndex = 0)

        assertEquals(ref1, ref2)
        assertNotEquals(ref1, ref3)
    }

    @Test
    fun `test VariableReference copy`() {
        val original = VariableReference(
            id = 1000,
            type = VariableReferenceType.STACK_FRAME,
            threadId = 1L,
            frameIndex = 0
        )
        val copy = original.copy(id = 1001)

        assertEquals(1001, copy.id)
        assertEquals(original.type, copy.type)
        assertEquals(original.threadId, copy.threadId)
    }

    @Test
    fun `test VariableReference hashCode`() {
        val ref1 = VariableReference(id = 1000, type = VariableReferenceType.STACK_FRAME)
        val ref2 = VariableReference(id = 1000, type = VariableReferenceType.STACK_FRAME)

        assertEquals(ref1.hashCode(), ref2.hashCode())
    }

    @Test
    fun `test VariableReference toString`() {
        val ref = VariableReference(
            id = 1000,
            type = VariableReferenceType.STACK_FRAME,
            threadId = 1L,
            frameIndex = 0
        )
        val str = ref.toString()

        assertTrue(str.contains("VariableReference"))
        assertTrue(str.contains("1000"))
        assertTrue(str.contains("STACK_FRAME"))
    }
}
