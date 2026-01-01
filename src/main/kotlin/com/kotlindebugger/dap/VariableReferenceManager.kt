package com.kotlindebugger.dap

import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 变量引用类型
 */
enum class VariableReferenceType {
    STACK_FRAME,    // 栈帧引用
    OBJECT_FIELDS,  // 对象字段引用
    ARRAY_ELEMENTS  // 数组元素引用
}

/**
 * 变量引用数据
 */
data class VariableReference(
    val id: Int,
    val type: VariableReferenceType,
    val stackFrame: StackFrame? = null,
    val objectRef: ObjectReference? = null,
    val arrayStart: Int = 0,
    val arrayCount: Int = -1
)

/**
 * 变量引用管理器
 *
 * 管理变量引用ID的分配和检索,用于支持DAP协议中的变量展开功能
 */
class VariableReferenceManager {
    private val idCounter = AtomicInteger(1000)  // 从1000开始分配ID
    private val references = ConcurrentHashMap<Int, VariableReference>()

    /**
     * 为栈帧创建引用ID
     */
    fun createStackFrameReference(frame: StackFrame): Int {
        val id = idCounter.getAndIncrement()
        references[id] = VariableReference(
            id = id,
            type = VariableReferenceType.STACK_FRAME,
            stackFrame = frame
        )
        return id
    }

    /**
     * 为对象字段创建引用ID
     */
    fun createObjectFieldsReference(objectRef: ObjectReference): Int {
        val id = idCounter.getAndIncrement()
        references[id] = VariableReference(
            id = id,
            type = VariableReferenceType.OBJECT_FIELDS,
            objectRef = objectRef
        )
        return id
    }

    /**
     * 为数组元素创建引用ID
     */
    fun createArrayElementsReference(arrayRef: ObjectReference, start: Int, count: Int): Int {
        val id = idCounter.getAndIncrement()
        references[id] = VariableReference(
            id = id,
            type = VariableReferenceType.ARRAY_ELEMENTS,
            objectRef = arrayRef,
            arrayStart = start,
            arrayCount = count
        )
        return id
    }

    /**
     * 根据ID获取变量引用
     */
    fun getReference(id: Int): VariableReference? {
        return references[id]
    }

    /**
     * 删除变量引用
     */
    fun removeReference(id: Int) {
        references.remove(id)
    }

    /**
     * 清除所有引用
     */
    fun clear() {
        references.clear()
    }
}
