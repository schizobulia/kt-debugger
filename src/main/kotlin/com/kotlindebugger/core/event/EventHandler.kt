package com.kotlindebugger.core.event

import com.kotlindebugger.common.model.*
import com.kotlindebugger.common.util.JdiUtils.safeLineNumber
import com.kotlindebugger.common.util.JdiUtils.safeSourceName
import com.kotlindebugger.common.util.JdiUtils.getThreadStatus
import com.kotlindebugger.core.breakpoint.ConditionEvaluator
import com.sun.jdi.*
import com.sun.jdi.event.*
import com.sun.jdi.request.BreakpointRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 断点信息（包含条件）
 */
data class BreakpointInfo(
    val id: Int,
    val condition: String?
)

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

    // 断点 ID 和条件映射 (BreakpointRequest -> BreakpointInfo)
    private val breakpointMap = ConcurrentHashMap<BreakpointRequest, BreakpointInfo>()
    
    // 条件表达式求值器
    private val conditionEvaluator = ConditionEvaluator(vm)

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
     * @param request JDI 断点请求
     * @param breakpointId 断点 ID
     * @param condition 条件表达式（可选）
     */
    fun registerBreakpoint(request: BreakpointRequest, breakpointId: Int, condition: String? = null) {
        breakpointMap[request] = BreakpointInfo(breakpointId, condition)
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
     * 对于条件断点，会先评估条件，只有条件满足时才生成事件
     */
    private fun convertEvent(event: Event): DebugEvent? {
        return when (event) {
            is BreakpointEvent -> {
                val location = event.location()
                val breakpointInfo = breakpointMap[event.request()]
                val breakpointId = breakpointInfo?.id ?: -1
                val condition = breakpointInfo?.condition
                
                // 评估条件断点
                if (condition != null && condition.isNotBlank()) {
                    val conditionMet = try {
                        conditionEvaluator.evaluate(condition, event.thread(), location)
                    } catch (e: Exception) {
                        System.err.println("Error evaluating breakpoint condition: ${e.message}")
                        true // 如果条件评估失败，默认停止
                    }
                    
                    if (!conditionMet) {
                        // 条件不满足，返回 null 让程序继续运行
                        return null
                    }
                }
                
                val bp = Breakpoint.LineBreakpoint(
                    id = breakpointId,
                    file = location.safeSourceName() ?: "unknown",
                    line = location.safeLineNumber(),
                    condition = condition
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
            is DebugEvent.ClassPrepared -> false // 类加载时不暂停，DebugSession 会处理 resume
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
