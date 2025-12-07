package com.kotlindebugger.core.event

import com.kotlindebugger.common.model.*
import com.kotlindebugger.common.util.JdiUtils.safeLineNumber
import com.kotlindebugger.common.util.JdiUtils.safeSourceName
import com.kotlindebugger.common.util.JdiUtils.getThreadStatus
import com.sun.jdi.*
import com.sun.jdi.event.*
import com.sun.jdi.request.BreakpointRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 事件监听器接口
 */
interface DebugEventListener {
    fun onEvent(event: DebugEvent)
}

/**
 * 事件处理器
 * 负责从 JDI 事件队列接收事件并分发给监听器
 */
class EventHandler(private val vm: VirtualMachine) {

    private val listeners = ConcurrentHashMap.newKeySet<DebugEventListener>()
    private val running = AtomicBoolean(false)
    private var eventThread: Thread? = null

    // 断点 ID 映射 (BreakpointRequest -> Breakpoint ID)
    private val breakpointMap = ConcurrentHashMap<BreakpointRequest, Int>()

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
     * 注册断点请求与 ID 的映射
     */
    fun registerBreakpoint(request: BreakpointRequest, breakpointId: Int) {
        breakpointMap[request] = breakpointId
    }

    /**
     * 取消断点注册
     */
    fun unregisterBreakpoint(request: BreakpointRequest) {
        breakpointMap.remove(request)
    }

    /**
     * 启动事件处理循环
     */
    fun start() {
        if (running.getAndSet(true)) {
            return // 已经在运行
        }

        eventThread = Thread({
            processEvents()
        }, "DebugEventHandler").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 停止事件处理
     */
    fun stop() {
        running.set(false)
        eventThread?.interrupt()
        eventThread = null
    }

    /**
     * 事件处理主循环
     */
    private fun processEvents() {
        val eventQueue = vm.eventQueue()

        while (running.get()) {
            try {
                // 等待事件，超时 100ms 以便检查 running 标志
                val eventSet = eventQueue.remove(100) ?: continue

                var shouldResume = true

                for (event in eventSet) {
                    val debugEvent = convertEvent(event)
                    if (debugEvent != null) {
                        // 分发事件给所有监听器
                        listeners.forEach { listener ->
                            try {
                                listener.onEvent(debugEvent)
                            } catch (e: Exception) {
                                System.err.println("Error in event listener: ${e.message}")
                            }
                        }

                        // 对于需要暂停的事件，不自动恢复
                        if (shouldSuspend(debugEvent)) {
                            shouldResume = false
                        }
                    }

                    // VM 断开连接时退出循环
                    if (event is VMDisconnectEvent || event is VMDeathEvent) {
                        running.set(false)
                        shouldResume = false
                        break
                    }
                }

                if (shouldResume && running.get()) {
                    eventSet.resume()
                }

            } catch (e: InterruptedException) {
                // 正常中断，退出循环
                break
            } catch (e: VMDisconnectedException) {
                // VM 已断开
                dispatchEvent(DebugEvent.VMDisconnected)
                break
            } catch (e: Exception) {
                if (running.get()) {
                    System.err.println("Error processing event: ${e.message}")
                }
            }
        }
    }

    /**
     * 将 JDI 事件转换为 DebugEvent
     */
    private fun convertEvent(event: Event): DebugEvent? {
        return when (event) {
            is BreakpointEvent -> {
                val location = event.location()
                val breakpointId = breakpointMap[event.request()] ?: -1
                val bp = Breakpoint.LineBreakpoint(
                    id = breakpointId,
                    file = location.safeSourceName() ?: "unknown",
                    line = location.safeLineNumber()
                )
                DebugEvent.BreakpointHit(
                    breakpoint = bp,
                    threadId = event.thread().uniqueID(),
                    location = createSourcePosition(location)
                )
            }

            is StepEvent -> {
                DebugEvent.StepCompleted(
                    threadId = event.thread().uniqueID(),
                    location = createSourcePosition(event.location())
                )
            }

            is ExceptionEvent -> {
                val exception = event.exception()
                DebugEvent.ExceptionThrown(
                    exceptionClass = exception.referenceType().name(),
                    message = getExceptionMessage(exception),
                    threadId = event.thread().uniqueID(),
                    location = createSourcePosition(event.location())
                )
            }

            is ThreadStartEvent -> {
                DebugEvent.ThreadStarted(createThreadInfo(event.thread()))
            }

            is ThreadDeathEvent -> {
                DebugEvent.ThreadDeath(event.thread().uniqueID())
            }

            is VMStartEvent -> {
                DebugEvent.VMStarted(createThreadInfo(event.thread()))
            }

            is VMDeathEvent -> {
                DebugEvent.VMDeath(null)
            }

            is VMDisconnectEvent -> {
                DebugEvent.VMDisconnected
            }

            is ClassPrepareEvent -> {
                DebugEvent.ClassPrepared(event.referenceType().name())
            }

            else -> null // 忽略其他事件
        }
    }

    /**
     * 判断事件是否需要暂停 VM
     */
    private fun shouldSuspend(event: DebugEvent): Boolean {
        return when (event) {
            is DebugEvent.BreakpointHit -> true
            is DebugEvent.StepCompleted -> true
            is DebugEvent.ExceptionThrown -> true
            is DebugEvent.VMStarted -> false  // VM 启动时保持暂停，让调用者决定何时 resume
            is DebugEvent.ClassPrepared -> true // 类加载时保持暂停，以便设置断点
            else -> false
        }
    }

    /**
     * 分发事件
     */
    private fun dispatchEvent(event: DebugEvent) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                System.err.println("Error in event listener: ${e.message}")
            }
        }
    }

    /**
     * 创建 SourcePosition
     */
    private fun createSourcePosition(location: Location?): SourcePosition? {
        if (location == null) return null
        val sourceName = location.safeSourceName() ?: return null
        val lineNumber = location.safeLineNumber()
        if (lineNumber < 0) return null
        return SourcePosition(sourceName, lineNumber)
    }

    /**
     * 创建 ThreadInfo
     */
    private fun createThreadInfo(thread: ThreadReference): ThreadInfo {
        return ThreadInfo(
            id = thread.uniqueID(),
            name = thread.name(),
            status = thread.getThreadStatus(),
            isSuspended = thread.isSuspended
        )
    }

    /**
     * 获取异常消息
     */
    private fun getExceptionMessage(exception: ObjectReference): String? {
        return try {
            val messageField = exception.referenceType().fieldByName("detailMessage")
            val messageValue = exception.getValue(messageField) as? StringReference
            messageValue?.value()
        } catch (e: Exception) {
            null
        }
    }
}
