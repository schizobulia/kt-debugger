package com.kotlindebugger.dap

import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
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
 * 
 * 注意：不直接存储 StackFrame，因为当线程恢复执行后 StackFrame 会失效。
 * 改为存储 threadId 和 frameIndex，在需要时动态获取。
 */
data class VariableReference(
    val id: Int,
    val type: VariableReferenceType,
    // 用于 STACK_FRAME 类型：存储线程ID和栈帧索引，而不是直接存储 StackFrame
    val threadId: Long = 0,
    val frameIndex: Int = 0,
    // 用于 OBJECT_FIELDS 和 ARRAY_ELEMENTS 类型
    val objectRef: ObjectReference? = null,
    val arrayStart: Int = 0,
    val arrayCount: Int = -1
)

/**
 * 变量引用管理器
 *
 * 管理变量引用ID的分配和检索,用于支持DAP协议中的变量展开功能。
 * 
 * 关键设计：
 * 1. 不直接存储 StackFrame 对象，而是存储 threadId + frameIndex
 * 2. 线程恢复执行时会清理所有变量引用
 * 3. 获取对象字段时捕获异常，优雅降级
 */
class VariableReferenceManager {
    private val idCounter = AtomicInteger(1000)  // 从1000开始分配ID
    private val references = ConcurrentHashMap<Int, VariableReference>()

    /**
     * 为栈帧创建引用ID
     * 存储 threadId 和 frameIndex 而不是直接存储 StackFrame
     */
    fun createStackFrameReference(thread: ThreadReference, frameIndex: Int): Int {
        val id = idCounter.getAndIncrement()
        references[id] = VariableReference(
            id = id,
            type = VariableReferenceType.STACK_FRAME,
            threadId = thread.uniqueID(),
            frameIndex = frameIndex
        )
        Logger.debug("Created stack frame reference: id=$id, threadId=${thread.uniqueID()}, frameIndex=$frameIndex")
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
        Logger.debug("Created object fields reference: id=$id, type=${objectRef.referenceType().name()}")
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
        Logger.debug("Created array elements reference: id=$id, start=$start, count=$count")
        return id
    }

    /**
     * 根据ID获取变量引用
     */
    fun getReference(id: Int): VariableReference? {
        return references[id]
    }

    /**
     * 根据 VariableReference 获取实际的 StackFrame
     * 返回 null 如果线程不存在或栈帧无效
     */
    fun getStackFrame(varRef: VariableReference, vm: VirtualMachine): com.sun.jdi.StackFrame? {
        if (varRef.type != VariableReferenceType.STACK_FRAME) return null
        
        return try {
            val thread = vm.allThreads().find { it.uniqueID() == varRef.threadId }
            if (thread == null) {
                Logger.debug("Thread not found: ${varRef.threadId}")
                return null
            }
            
            // 检查线程是否处于挂起状态
            if (!thread.isSuspended) {
                Logger.debug("Thread is not suspended: ${varRef.threadId}")
                return null
            }
            
            val frames = thread.frames()
            if (varRef.frameIndex in frames.indices) {
                frames[varRef.frameIndex]
            } else {
                Logger.debug("Frame index out of bounds: ${varRef.frameIndex}, frames count: ${frames.size}")
                null
            }
        } catch (e: Exception) {
            Logger.debug("Failed to get stack frame: ${e.message}")
            null
        }
    }

    /**
     * 删除变量引用
     */
    fun removeReference(id: Int) {
        references.remove(id)
    }

    /**
     * 清除所有引用
     * 在线程恢复执行时调用
     */
    fun clear() {
        val count = references.size
        references.clear()
        Logger.debug("Cleared all variable references (count: $count)")
    }
}
