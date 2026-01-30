package com.kotlindebugger.core

import com.kotlindebugger.common.model.*
import com.kotlindebugger.common.util.JdiUtils
import com.kotlindebugger.core.breakpoint.BreakpointManager
import com.kotlindebugger.core.breakpoint.ExceptionBreakpointManager
import com.kotlindebugger.core.breakpoint.ExceptionBreakpointResult
import com.kotlindebugger.core.coroutine.CoroutineDebugger
import com.kotlindebugger.core.coroutine.CoroutineInfo
import com.kotlindebugger.core.event.DebugEventListener
import com.kotlindebugger.core.event.EventHandler
import com.kotlindebugger.core.jdi.DebugTarget
import com.kotlindebugger.core.jdi.VMConnector
import com.kotlindebugger.core.source.SourceViewer
import com.kotlindebugger.core.stack.StackFrameManager
import com.kotlindebugger.core.stepping.SteppingController
import com.kotlindebugger.core.variable.VariableInspector
import com.kotlindebugger.kotlin.position.KotlinPositionManager
import com.kotlindebugger.kotlin.smap.SMAPCache
import com.sun.jdi.*
import com.sun.jdi.event.ClassPrepareEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 调试会话状态
 */
enum class SessionState {
    NOT_STARTED,
    RUNNING,
    SUSPENDED,
    TERMINATED
}

/**
 * 调试会话
 * 管理整个调试过程的核心类
 */
class DebugSession(private val target: DebugTarget) : DebugEventListener {

    private val connector = VMConnector()
    private lateinit var vm: VirtualMachine

    private lateinit var eventHandler: EventHandler
    private lateinit var breakpointManager: BreakpointManager
    private lateinit var exceptionBreakpointManager: ExceptionBreakpointManager
    private lateinit var stackFrameManager: StackFrameManager
    private lateinit var steppingController: SteppingController
    private lateinit var variableInspector: VariableInspector
    private lateinit var sourceViewer: SourceViewer
    private lateinit var positionManager: KotlinPositionManager
    private lateinit var coroutineDebugger: CoroutineDebugger

    private val smapCache = SMAPCache()

    private val state = AtomicReference(SessionState.NOT_STARTED)
    private val listeners = mutableListOf<DebugEventListener>()

    // 当前暂停的线程
    private var currentThread: ThreadReference? = null
    private var currentFrameIndex: Int = 0

    // 用于等待事件的锁
    private var eventLatch: CountDownLatch? = null
    private var lastEvent: DebugEvent? = null

    /**
     * 启动调试会话
     */
    fun start() {
        if (state.get() != SessionState.NOT_STARTED) {
            throw IllegalStateException("Session already started")
        }

        // 连接到 VM
        vm = connector.connect(target)

        // 初始化组件
        eventHandler = EventHandler(vm)
        positionManager = KotlinPositionManager(vm, smapCache)
        sourceViewer = SourceViewer() // TODO: 可以从配置中读取源代码根目录
        breakpointManager = BreakpointManager(vm, eventHandler)
        exceptionBreakpointManager = ExceptionBreakpointManager(vm)
        stackFrameManager = StackFrameManager(vm)
        steppingController = SteppingController(vm, eventHandler, positionManager)
        variableInspector = VariableInspector(vm)
        coroutineDebugger = CoroutineDebugger(vm)

        // 初始化内联调试支持
        stackFrameManager.initializeInlineSupport()
        breakpointManager.initializeInlineSupport()

        // 注册事件监听
        eventHandler.addListener(this)
        eventHandler.start()

        state.set(SessionState.RUNNING)
    }

    /**
     * 停止调试会话
     */
    fun stop() {
        if (state.get() == SessionState.TERMINATED) {
            return
        }

        // 安全地清理组件，处理可能未初始化的情况
        try {
            if (::breakpointManager.isInitialized) {
                breakpointManager.clear()
            }
        } catch (e: Exception) {
            // 忽略清理时的错误
        }

        try {
            if (::exceptionBreakpointManager.isInitialized) {
                exceptionBreakpointManager.clearExceptionRequests()
            }
        } catch (e: Exception) {
            // 忽略清理时的错误
        }

        try {
            if (::eventHandler.isInitialized) {
                eventHandler.stop()
            }
        } catch (e: Exception) {
            // 忽略清理时的错误
        }

        try {
            if (::vm.isInitialized) {
                vm.dispose()
            }
        } catch (e: Exception) {
            // 忽略关闭时的错误
        }

        state.set(SessionState.TERMINATED)
    }

    /**
     * 继续执行
     */
    fun resume() {
        if (state.get() != SessionState.SUSPENDED) {
            return
        }

        vm.resume()
        state.set(SessionState.RUNNING)
        currentThread = null
    }

    /**
     * 强制继续执行（跳过状态检查）
     * 用于 configurationDone 后恢复 VM 运行
     */
    fun forceResume() {
        try {
            vm.resume()
            state.set(SessionState.RUNNING)
            currentThread = null
        } catch (e: Exception) {
            // 忽略 resume 错误
        }
    }

    /**
     * 暂停执行
     */
    fun suspend() {
        if (state.get() != SessionState.RUNNING) {
            return
        }

        vm.suspend()
        state.set(SessionState.SUSPENDED)

        // 选择第一个暂停的线程
        currentThread = stackFrameManager.getFirstSuspendedThread()
        currentFrameIndex = 0
    }

    // ==================== 断点管理 ====================

    /**
     * 添加行断点
     */
    fun addBreakpoint(file: String, line: Int, condition: String? = null): Breakpoint {
        return breakpointManager.addLineBreakpoint(file, line, condition)
    }

    /**
     * 删除断点
     */
    fun removeBreakpoint(id: Int): Boolean {
        return breakpointManager.removeBreakpoint(id)
    }

    /**
     * 列出所有断点
     */
    fun listBreakpoints(): List<Breakpoint> {
        return breakpointManager.listBreakpoints()
    }

    /**
     * 启用断点
     */
    fun enableBreakpoint(id: Int): Boolean {
        return breakpointManager.enableBreakpoint(id)
    }

    /**
     * 禁用断点
     */
    fun disableBreakpoint(id: Int): Boolean {
        return breakpointManager.disableBreakpoint(id)
    }

    // ==================== 异常断点管理 ====================

    /**
     * 设置异常断点
     * @param filters 过滤器列表，如 ["caught", "uncaught"]
     * @return 设置的断点结果列表
     */
    fun setExceptionBreakpoints(filters: List<String>): List<ExceptionBreakpointResult> {
        return exceptionBreakpointManager.setExceptionBreakpoints(filters)
    }

    /**
     * 检查是否启用了异常断点
     */
    fun isExceptionBreakpointsEnabled(): Boolean {
        return exceptionBreakpointManager.isEnabled()
    }

    /**
     * 检查是否应该在此异常处暂停
     */
    fun shouldStopOnException(isCaught: Boolean): Boolean {
        return exceptionBreakpointManager.shouldStopOnException(isCaught)
    }

    // ==================== 栈帧管理 ====================

    /**
     * 获取当前线程的栈帧
     */
    fun getStackFrames(): List<StackFrameInfo> {
        val thread = currentThread ?: return emptyList()
        return stackFrameManager.getStackFrames(thread.uniqueID())
    }

    /**
     * 获取当前栈帧
     */
    fun getCurrentFrame(): StackFrameInfo? {
        val frames = getStackFrames()
        return frames.getOrNull(currentFrameIndex)
    }

    /**
     * 切换到指定栈帧
     */
    fun selectFrame(index: Int): Boolean {
        val frames = getStackFrames()
        if (index !in frames.indices) {
            return false
        }
        currentFrameIndex = index
        return true
    }

    /**
     * 获取当前帧索引
     */
    fun getCurrentFrameIndex(): Int = currentFrameIndex

    // ==================== 变量查看 ====================

    /**
     * 获取当前栈帧的局部变量
     */
    fun getLocalVariables(): List<VariableInfo> {
        val thread = currentThread ?: return emptyList()
        val frame = stackFrameManager.getFrame(thread.uniqueID(), currentFrameIndex) ?: return emptyList()
        return variableInspector.getLocalVariables(frame)
    }

    /**
     * 获取指定变量的值
     */
    fun getVariable(name: String): VariableInfo? {
        val thread = currentThread ?: return null
        val frame = stackFrameManager.getFrame(thread.uniqueID(), currentFrameIndex) ?: return null
        val value = variableInspector.findVariable(frame, name) ?: return null

        return VariableInfo(
            name = name,
            typeName = value.type().name(),
            value = JdiUtils.run { value.toDisplayString() },
            isLocal = true
        )
    }

    // ==================== 线程管理 ====================

    /**
     * 获取所有线程
     */
    fun getThreads(): List<ThreadInfo> {
        return vm.allThreads().map { thread ->
            ThreadInfo(
                id = thread.uniqueID(),
                name = thread.name(),
                status = JdiUtils.run { thread.getThreadStatus() },
                isSuspended = thread.isSuspended
            )
        }
    }

    /**
     * 切换到指定线程
     */
    fun selectThread(threadId: Long): Boolean {
        val thread = vm.allThreads().find { it.uniqueID() == threadId } ?: return false
        if (!thread.isSuspended) {
            return false
        }
        currentThread = thread
        currentFrameIndex = 0
        return true
    }

    /**
     * 获取当前线程
     */
    fun getCurrentThread(): ThreadInfo? {
        val thread = currentThread ?: return null
        return ThreadInfo(
            id = thread.uniqueID(),
            name = thread.name(),
            status = JdiUtils.run { thread.getThreadStatus() },
            isSuspended = thread.isSuspended
        )
    }

    // ==================== 单步执行 ====================

    /**
     * Step Over - 执行下一行（不进入方法）
     */
    fun stepOver() {
        val thread = currentThread ?: throw IllegalStateException("No current thread")
        if (!thread.isSuspended) {
            throw IllegalStateException("Thread is not suspended")
        }

        steppingController.step(thread, StepType.STEP_OVER, this)
    }

    /**
     * Step Into - 进入方法调用
     */
    fun stepInto() {
        val thread = currentThread ?: throw IllegalStateException("No current thread")
        if (!thread.isSuspended) {
            throw IllegalStateException("Thread is not suspended")
        }

        steppingController.step(thread, StepType.STEP_INTO, this)
    }

    /**
     * Step Out - 跳出当前方法
     */
    fun stepOut() {
        val thread = currentThread ?: throw IllegalStateException("No current thread")
        if (!thread.isSuspended) {
            throw IllegalStateException("Thread is not suspended")
        }

        steppingController.step(thread, StepType.STEP_OUT, this)
    }

    /**
     * 检查是否正在单步执行
     */
    fun isStepping(): Boolean = steppingController.isStepping()

    // ==================== 协程调试 ====================

    /**
     * 获取所有协程信息
     */
    fun getCoroutines(): List<CoroutineInfo> {
        return coroutineDebugger.getAllCoroutines()
    }

    /**
     * 检查协程调试探针是否已安装
     */
    fun isCoroutineDebugProbesInstalled(): Boolean {
        return coroutineDebugger.isCoroutineDebugProbesInstalled()
    }

    /**
     * 获取协程调试状态描述
     */
    fun getCoroutineDebugStatus(): String {
        return coroutineDebugger.getDebugStatusDescription()
    }

    // ==================== 位置信息 ====================

    /**
     * 获取当前源代码位置
     */
    fun getCurrentPosition(): SourcePosition? {
        val thread = currentThread ?: return null
        val frame = stackFrameManager.getFrame(thread.uniqueID(), currentFrameIndex) ?: return null
        return positionManager.getSourcePosition(frame.location())
    }

    /**
     * 获取当前源代码（带上下文）
     */
    fun getCurrentSourceCode(contextLines: Int = 5): SourceViewer.SourceCodeView? {
        val position = getCurrentPosition() ?: return null
        return sourceViewer.getSourceCode(position, contextLines)
    }

    
    // ==================== 事件监听 ====================

    /**
     * 添加事件监听器
     */
    fun addListener(listener: DebugEventListener) {
        listeners.add(listener)
    }

    /**
     * 移除事件监听器
     */
    fun removeListener(listener: DebugEventListener) {
        listeners.remove(listener)
    }

    /**
     * 等待下一个事件
     */
    fun waitForEvent(timeout: Long = 0): DebugEvent? {
        eventLatch = CountDownLatch(1)
        lastEvent = null

        if (timeout > 0) {
            eventLatch?.await(timeout, TimeUnit.MILLISECONDS)
        } else {
            eventLatch?.await()
        }

        return lastEvent
    }

    // ==================== 事件处理 ====================

    override fun onEvent(event: DebugEvent) {
        when (event) {
            is DebugEvent.VMStarted -> {
                // VM started, if suspend=false, ensure it's running
                val launchTarget = target as? DebugTarget.Launch
                if (launchTarget?.suspend != true) {
                    // VM should be running, not suspended
                    vm.resume()
                }
            }

            is DebugEvent.BreakpointHit -> {
                state.set(SessionState.SUSPENDED)
                currentThread = vm.allThreads().find { it.uniqueID() == event.threadId }
                currentFrameIndex = 0
            }

            is DebugEvent.StepCompleted -> {
                state.set(SessionState.SUSPENDED)
                currentThread = vm.allThreads().find { it.uniqueID() == event.threadId }
                currentFrameIndex = 0
            }

            is DebugEvent.ExceptionThrown -> {
                state.set(SessionState.SUSPENDED)
                currentThread = vm.allThreads().find { it.uniqueID() == event.threadId }
                currentFrameIndex = 0
            }

            is DebugEvent.VMDisconnected, is DebugEvent.VMDeath -> {
                state.set(SessionState.TERMINATED)
            }

            is DebugEvent.ClassPrepared -> {
                // 处理待设置的断点
                val refType = vm.classesByName(event.className).firstOrNull()
                if (refType != null) {
                    breakpointManager.onClassPrepared(event.className, refType)
                }
                // EventHandler 会自动 resume（shouldSuspend 返回 false）
            }

            else -> {}
        }

        // 通知用户监听器
        listeners.forEach { it.onEvent(event) }

        // 释放等待锁
        lastEvent = event
        eventLatch?.countDown()
    }

    // ==================== 状态查询 ====================

    fun getState(): SessionState = state.get()

    fun isRunning(): Boolean = state.get() == SessionState.RUNNING

    fun isSuspended(): Boolean = state.get() == SessionState.SUSPENDED

    fun isTerminated(): Boolean = state.get() == SessionState.TERMINATED

    fun getVirtualMachine(): VirtualMachine = vm
}
