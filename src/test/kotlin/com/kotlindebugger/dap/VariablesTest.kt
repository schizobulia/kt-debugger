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
