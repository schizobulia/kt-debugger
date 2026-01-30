package com.kotlindebugger.core.coroutine

import com.kotlindebugger.common.model.SourcePosition
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference

/**
 * 协程状态枚举
 * 参考 IntelliJ Community 实现
 */
enum class CoroutineState(val displayName: String) {
    CREATED("CREATED"),
    RUNNING("RUNNING"),
    SUSPENDED("SUSPENDED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(state: String?): CoroutineState {
            return entries.find { it.displayName.equals(state, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * 协程信息数据类
 * 包含协程的基本信息：名称、ID、状态、调度器等
 */
data class CoroutineInfo(
    val id: Long?,
    val name: String,
    val state: CoroutineState,
    val dispatcher: String?,
    val lastObservedThread: ThreadReference?,
    val lastObservedFrame: ObjectReference?,
    val continuationStackFrames: List<CoroutineStackFrameItem> = emptyList(),
    val creationStackFrames: List<CoroutineStackFrameItem> = emptyList()
) {
    val isSuspended: Boolean = state == CoroutineState.SUSPENDED
    val isRunning: Boolean = state == CoroutineState.RUNNING
    val isCreated: Boolean = state == CoroutineState.CREATED

    /**
     * 获取协程描述字符串
     */
    fun getDescription(): String {
        val threadInfo = if (isRunning && lastObservedThread != null) {
            " on thread ${lastObservedThread.name()}"
        } else ""
        val dispatcherInfo = dispatcher?.let { " [$it]" } ?: ""
        return "\"$name:${id ?: "?"}\" ${state.displayName}$threadInfo$dispatcherInfo"
    }

    companion object {
        const val DEFAULT_COROUTINE_NAME = "coroutine"
    }
}

/**
 * 协程栈帧项
 * 表示协程调用栈中的单个帧
 */
open class CoroutineStackFrameItem(
    val className: String,
    val methodName: String,
    val location: SourcePosition?,
    val isCreationFrame: Boolean = false
) {
    override fun toString(): String {
        val locationStr = location?.toString() ?: "unknown location"
        return "$className.$methodName($locationStr)"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoroutineStackFrameItem) return false
        return className == other.className && 
               methodName == other.methodName && 
               location == other.location &&
               isCreationFrame == other.isCreationFrame
    }
    
    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + methodName.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + isCreationFrame.hashCode()
        return result
    }
}

/**
 * 创建栈帧项
 * 专门用于表示协程创建位置的栈帧
 */
class CreationCoroutineStackFrameItem(
    className: String,
    methodName: String,
    location: SourcePosition?
) : CoroutineStackFrameItem(className, methodName, location, isCreationFrame = true)

/**
 * 挂起退出模式
 * 用于识别协程暂停点的类型
 */
enum class SuspendExitMode {
    NONE,
    SUSPEND_LAMBDA,
    SUSPEND_METHOD,
    SUSPEND_METHOD_PARAMETER
}
