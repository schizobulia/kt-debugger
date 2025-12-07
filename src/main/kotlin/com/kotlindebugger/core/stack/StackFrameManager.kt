package com.kotlindebugger.core.stack

import com.kotlindebugger.common.model.SourcePosition
import com.kotlindebugger.common.model.StackFrameInfo
import com.kotlindebugger.common.util.JdiUtils.safeLineNumber
import com.kotlindebugger.common.util.JdiUtils.safeSourceName
import com.sun.jdi.*

/**
 * 栈帧管理器
 * 负责获取和管理栈帧信息
 */
class StackFrameManager(private val vm: VirtualMachine) {

    /**
     * 初始化内联调试支持（预留接口）
     */
    fun initializeInlineSupport() {
        // TODO: 实现内联调试支持
        println("内联调试功能暂未完全实现")
    }

    /**
     * 获取指定线程的所有栈帧
     */
    fun getStackFrames(threadId: Long): List<StackFrameInfo> {
        val thread = findThread(threadId) ?: return emptyList()

        if (!thread.isSuspended) {
            return emptyList()
        }

        return try {
            thread.frames().mapIndexed { index, frame ->
                createStackFrameInfo(index, frame)
            }
        } catch (e: IncompatibleThreadStateException) {
            emptyList()
        }
    }

    /**
     * 获取指定线程的当前栈帧
     */
    fun getCurrentFrame(threadId: Long): StackFrameInfo? {
        val frames = getStackFrames(threadId)
        return frames.firstOrNull()
    }

    /**
     * 获取指定线程的指定索引的栈帧
     */
    fun getFrame(threadId: Long, frameIndex: Int): StackFrame? {
        val thread = findThread(threadId) ?: return null

        if (!thread.isSuspended) {
            return null
        }

        return try {
            val frames = thread.frames()
            if (frameIndex in frames.indices) {
                frames[frameIndex]
            } else {
                null
            }
        } catch (e: IncompatibleThreadStateException) {
            null
        }
    }

    /**
     * 获取栈帧的详细信息
     */
    fun getFrameDetails(threadId: Long, frameIndex: Int): StackFrameDetails? {
        val thread = findThread(threadId) ?: return null

        if (!thread.isSuspended) {
            return null
        }

        return try {
            val frames = thread.frames()
            if (frameIndex !in frames.indices) return null

            val frame = frames[frameIndex]
            val location = frame.location()

            StackFrameDetails(
                info = createStackFrameInfo(frameIndex, frame),
                thisObject = getThisObject(frame),
                arguments = getArguments(frame),
                rawFrame = frame
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取栈帧中的 this 对象
     */
    private fun getThisObject(frame: StackFrame): ObjectReference? {
        return try {
            frame.thisObject()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取方法参数
     */
    private fun getArguments(frame: StackFrame): List<Pair<String, Value?>> {
        return try {
            val method = frame.location().method()
            val argValues = frame.getArgumentValues()
            val argNames = try {
                method.arguments().map { it.name() }
            } catch (e: AbsentInformationException) {
                // 没有参数名信息，使用 arg0, arg1 等
                argValues.indices.map { "arg$it" }
            }

            argNames.zip(argValues)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 创建栈帧信息
     */
    private fun createStackFrameInfo(index: Int, frame: StackFrame): StackFrameInfo {
        val location = frame.location()
        val method = location.method()

        return StackFrameInfo(
            index = index,
            className = location.declaringType().name(),
            methodName = method.name(),
            location = createSourcePosition(location),
            isInline = isInlineFrame(location),
            isNative = method.isNative
        )
    }

    /**
     * 创建源位置
     */
    private fun createSourcePosition(location: Location): SourcePosition? {
        val sourceName = location.safeSourceName() ?: return null
        val lineNumber = location.safeLineNumber()
        if (lineNumber < 0) return null
        return SourcePosition(sourceName, lineNumber)
    }

    /**
     * 判断是否是内联函数帧
     * TODO: 使用 SMAP 进行更准确的判断
     */
    private fun isInlineFrame(location: Location): Boolean {
        val typeName = location.declaringType().name()
        return typeName.contains("\$\$inlined\$") ||
                typeName.contains("\$inlined\$")
    }

    /**
     * 查找线程
     */
    private fun findThread(threadId: Long): ThreadReference? {
        return vm.allThreads().find { it.uniqueID() == threadId }
    }

    /**
     * 获取当前暂停的第一个线程
     */
    fun getFirstSuspendedThread(): ThreadReference? {
        return vm.allThreads().find { it.isSuspended }
    }

    /**
     * 栈帧详细信息
     */
    data class StackFrameDetails(
        val info: StackFrameInfo,
        val thisObject: ObjectReference?,
        val arguments: List<Pair<String, Value?>>,
        val rawFrame: StackFrame
    )
}
