package com.kotlindebugger.core.coroutine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * CoroutineUtils 单元测试
 * 测试协程工具函数的正确性
 */
class CoroutineUtilsTest {

    @Test
    fun `isCoroutineThread detects coroutine thread names`() {
        assertTrue(CoroutineUtils.isCoroutineThread("DefaultDispatcher-worker-1"))
        assertTrue(CoroutineUtils.isCoroutineThread("coroutine#1"))
        assertTrue(CoroutineUtils.isCoroutineThread("CommonPool-worker-1"))
        assertTrue(CoroutineUtils.isCoroutineThread("kotlinx.coroutines.DefaultExecutor"))
        
        assertFalse(CoroutineUtils.isCoroutineThread("main"))
        assertFalse(CoroutineUtils.isCoroutineThread("Thread-1"))
        assertFalse(CoroutineUtils.isCoroutineThread("ForkJoinPool.commonPool-worker-1"))
    }

    @Test
    fun `isCoroutineGeneratedClass detects coroutine classes`() {
        assertTrue(CoroutineUtils.isCoroutineGeneratedClass("com.example.MyClass\$\$suspended"))
        assertTrue(CoroutineUtils.isCoroutineGeneratedClass("kotlin.coroutines.ContinuationImpl"))
        assertTrue(CoroutineUtils.isCoroutineGeneratedClass("kotlin.coroutines.jvm.internal.BaseContinuationImpl"))
        assertTrue(CoroutineUtils.isCoroutineGeneratedClass("kotlin.coroutines.jvm.internal.SuspendLambda"))
        
        assertFalse(CoroutineUtils.isCoroutineGeneratedClass("com.example.MyClass"))
        assertFalse(CoroutineUtils.isCoroutineGeneratedClass("java.lang.Object"))
    }

    @Test
    fun `isCoroutineInternalMethod detects internal methods`() {
        assertTrue(CoroutineUtils.isCoroutineInternalMethod("invokeSuspend"))
        assertTrue(CoroutineUtils.isCoroutineInternalMethod("resumeWith"))
        assertTrue(CoroutineUtils.isCoroutineInternalMethod("invoke"))
        assertTrue(CoroutineUtils.isCoroutineInternalMethod("create"))
        assertTrue(CoroutineUtils.isCoroutineInternalMethod("<init>"))
        assertTrue(CoroutineUtils.isCoroutineInternalMethod("access\$getX"))
        
        assertFalse(CoroutineUtils.isCoroutineInternalMethod("doSomething"))
        assertFalse(CoroutineUtils.isCoroutineInternalMethod("main"))
        assertFalse(CoroutineUtils.isCoroutineInternalMethod("run"))
    }

    @Test
    fun `isContinuationVariable detects continuation variables`() {
        assertTrue(CoroutineUtils.isContinuationVariable("\$continuation"))
        assertTrue(CoroutineUtils.isContinuationVariable("\$completion"))
        assertTrue(CoroutineUtils.isContinuationVariable("continuation"))
        assertTrue(CoroutineUtils.isContinuationVariable("completion"))
        assertTrue(CoroutineUtils.isContinuationVariable("my\$continuation"))
        
        assertFalse(CoroutineUtils.isContinuationVariable("myVariable"))
        assertFalse(CoroutineUtils.isContinuationVariable("x"))
        assertFalse(CoroutineUtils.isContinuationVariable("cont"))
    }

    @Test
    fun `CREATION_STACK_TRACE_SEPARATOR is correct`() {
        assertEquals("\b\b\b", CoroutineUtils.CREATION_STACK_TRACE_SEPARATOR)
    }

    @Test
    fun `CREATION_CLASS_NAME is correct`() {
        assertEquals("_COROUTINE._CREATION", CoroutineUtils.CREATION_CLASS_NAME)
    }
}
