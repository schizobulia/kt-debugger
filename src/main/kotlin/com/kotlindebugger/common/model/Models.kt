package com.kotlindebugger.common.model

import com.sun.jdi.Location
import com.sun.jdi.ThreadReference

/**
 * 源代码位置
 */
data class SourcePosition(
    val file: String,
    val line: Int,
    val column: Int = 0
) {
    override fun toString(): String = "$file:$line"
}

/**
 * 断点类型
 */
sealed class Breakpoint {
    abstract val id: Int
    abstract val enabled: Boolean
    abstract val condition: String?
    abstract val hitCount: Int

    data class LineBreakpoint(
        override val id: Int,
        val file: String,
        val line: Int,
        override val enabled: Boolean = true,
        override val condition: String? = null,
        override val hitCount: Int = 0
    ) : Breakpoint() {
        override fun toString(): String = "Breakpoint #$id at $file:$line" +
                (if (!enabled) " (disabled)" else "") +
                (if (condition != null) " if ($condition)" else "")
    }

    data class MethodBreakpoint(
        override val id: Int,
        val className: String,
        val methodName: String,
        override val enabled: Boolean = true,
        override val condition: String? = null,
        override val hitCount: Int = 0
    ) : Breakpoint() {
        override fun toString(): String = "Breakpoint #$id at $className.$methodName()" +
                (if (!enabled) " (disabled)" else "")
    }
}

/**
 * 简化的断点事件类型（兼容JDI）
 */
interface JdiBreakpointEvent {
    val thread: ThreadReference
    val location: Location
}

/**
 * 断点事件（用于内部通信）
 */
data class DebugBreakpointEvent(
    val threadId: Long,
    val breakpointId: Int,
    val location: SourcePosition,
    val inlineStack: List<InlineStackFrame> = emptyList()
)

/**
 * 栈帧信息
 */
data class StackFrameInfo(
    val index: Int,
    val className: String,
    val methodName: String,
    val location: SourcePosition?,
    val isInline: Boolean = false,
    val isNative: Boolean = false,
    val inlineDepth: Int = 0
) {
    override fun toString(): String {
        val locationStr = location?.toString() ?: "unknown"
        val inlineMarker = if (isInline) " [inline depth=$inlineDepth]" else ""
        val nativeMarker = if (isNative) " [native]" else ""
        return "#$index  $className.$methodName($locationStr)$inlineMarker$nativeMarker"
    }
}

/**
 * 内联栈帧信息
 */
data class InlineStackFrame(
    val functionName: String,
    val sourceLocation: SourcePosition,
    val inlineDepth: Int
) {
    override fun toString(): String {
        return "Inline #$inlineDepth $functionName at ${sourceLocation.file}:${sourceLocation.line}"
    }
}

/**
 * 变量信息
 */
data class VariableInfo(
    val name: String,
    val typeName: String,
    val value: String,
    val isLocal: Boolean = true
) {
    override fun toString(): String = "$name: $typeName = $value"
}

/**
 * 线程信息
 */
data class ThreadInfo(
    val id: Long,
    val name: String,
    val status: ThreadStatus,
    val isSuspended: Boolean
) {
    override fun toString(): String {
        val suspendedMarker = if (isSuspended) " (suspended)" else ""
        return "Thread #$id \"$name\" $status$suspendedMarker"
    }
}

enum class ThreadStatus {
    RUNNING,
    SLEEPING,
    WAITING,
    MONITOR,
    ZOMBIE,
    NOT_STARTED,
    UNKNOWN
}

/**
 * 单步执行类型
 */
enum class StepType {
    STEP_OVER,  // 执行下一行（不进入方法）
    STEP_INTO,  // 进入方法
    STEP_OUT    // 跳出当前方法
}


/**
 * 调试事件类型
 */
sealed class DebugEvent {
    data class BreakpointHit(
        val breakpoint: Breakpoint,
        val threadId: Long,
        val location: SourcePosition?,
        val inlineStack: List<InlineStackFrame> = emptyList()
    ) : DebugEvent()

    data class StepCompleted(
        val threadId: Long,
        val location: SourcePosition?
    ) : DebugEvent()

    data class ExceptionThrown(
        val exceptionClass: String,
        val message: String?,
        val threadId: Long,
        val location: SourcePosition?
    ) : DebugEvent()

    data class ThreadStarted(val threadInfo: ThreadInfo) : DebugEvent()
    data class ThreadDeath(val threadId: Long) : DebugEvent()

    data class VMStarted(val mainThread: ThreadInfo) : DebugEvent()
    object VMDisconnected : DebugEvent()
    data class VMDeath(val exitCode: Int?) : DebugEvent()

    data class ClassPrepared(val className: String) : DebugEvent()

    /**
     * 热代码替换完成事件
     * Hot Code Replacement completed event
     */
    data class HotCodeReplaceCompleted(
        val reloadedClasses: List<String>,
        val message: String
    ) : DebugEvent()

    /**
     * 热代码替换失败事件
     * Hot Code Replacement failed event
     */
    data class HotCodeReplaceFailed(
        val errorMessage: String,
        val failedClasses: List<String>
    ) : DebugEvent()
}
