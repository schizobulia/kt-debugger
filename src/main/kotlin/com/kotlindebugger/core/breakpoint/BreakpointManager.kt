package com.kotlindebugger.core.breakpoint

import com.kotlindebugger.common.model.Breakpoint
import com.kotlindebugger.common.model.SourcePosition
import com.kotlindebugger.common.util.JdiUtils.safeLocationsOfLine
import com.kotlindebugger.core.event.EventHandler
import com.sun.jdi.*
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.ClassPrepareRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 断点管理器
 * 负责断点的创建、删除、启用和禁用
 */
class BreakpointManager(
    private val vm: VirtualMachine,
    private val eventHandler: EventHandler
) {

    /**
     * 初始化内联断点支持（预留接口）
     */
    fun initializeInlineSupport() {
        // TODO: 实现内联断点支持
        println("内联断点功能暂未完全实现")
    }
    private val breakpoints = ConcurrentHashMap<Int, BreakpointEntry>()
    private val nextId = AtomicInteger(1)

    // 等待加载的类 -> 待设置的断点
    private val pendingBreakpoints = ConcurrentHashMap<String, MutableList<PendingBreakpoint>>()

    // 类加载请求
    private val classPrepareRequests = ConcurrentHashMap<String, ClassPrepareRequest>()

    /**
     * 断点条目（包含 JDI 请求）
     */
    private data class BreakpointEntry(
        val breakpoint: Breakpoint,
        val requests: MutableList<BreakpointRequest> = mutableListOf()
    )

    /**
     * 待设置的断点
     */
    private data class PendingBreakpoint(
        val breakpointId: Int,
        val line: Int,
        val condition: String?
    )

    /**
     * 添加行断点
     * @param file 源文件名（如 Main.kt）
     * @param line 行号
     * @param condition 条件表达式（可选）
     * @return 创建的断点，如果无法创建则返回 null
     */
    fun addLineBreakpoint(file: String, line: Int, condition: String? = null): Breakpoint {
        val id = nextId.getAndIncrement()
        val breakpoint = Breakpoint.LineBreakpoint(
            id = id,
            file = file,
            line = line,
            enabled = true,
            condition = condition
        )

        val entry = BreakpointEntry(breakpoint)
        breakpoints[id] = entry

        // 尝试在已加载的类中设置断点
        val classesSet = trySetBreakpoint(file, line, id, condition)

        if (!classesSet) {
            // 如果没有找到匹配的类，添加到待处理列表
            addPendingBreakpoint(file, id, line, condition)
        }

        return breakpoint
    }

    /**
     * 添加方法断点
     */
    fun addMethodBreakpoint(
        className: String,
        methodName: String,
        condition: String? = null
    ): Breakpoint {
        val id = nextId.getAndIncrement()
        val breakpoint = Breakpoint.MethodBreakpoint(
            id = id,
            className = className,
            methodName = methodName,
            enabled = true,
            condition = condition
        )

        val entry = BreakpointEntry(breakpoint)
        breakpoints[id] = entry

        // 尝试设置方法断点
        trySetMethodBreakpoint(className, methodName, id)

        return breakpoint
    }

    /**
     * 删除断点
     */
    fun removeBreakpoint(id: Int): Boolean {
        val entry = breakpoints.remove(id) ?: return false

        // 删除 JDI 断点请求
        entry.requests.forEach { request ->
            eventHandler.unregisterBreakpoint(request)
            vm.eventRequestManager().deleteEventRequest(request)
        }

        return true
    }

    /**
     * 启用断点
     */
    fun enableBreakpoint(id: Int): Boolean {
        val entry = breakpoints[id] ?: return false

        entry.requests.forEach { it.enable() }

        // 更新断点状态
        val updatedBp = when (val bp = entry.breakpoint) {
            is Breakpoint.LineBreakpoint -> bp.copy(enabled = true)
            is Breakpoint.MethodBreakpoint -> bp.copy(enabled = true)
        }
        breakpoints[id] = entry.copy(breakpoint = updatedBp)

        return true
    }

    /**
     * 禁用断点
     */
    fun disableBreakpoint(id: Int): Boolean {
        val entry = breakpoints[id] ?: return false

        entry.requests.forEach { it.disable() }

        // 更新断点状态
        val updatedBp = when (val bp = entry.breakpoint) {
            is Breakpoint.LineBreakpoint -> bp.copy(enabled = false)
            is Breakpoint.MethodBreakpoint -> bp.copy(enabled = false)
        }
        breakpoints[id] = entry.copy(breakpoint = updatedBp)

        return true
    }

    /**
     * 获取所有断点
     */
    fun listBreakpoints(): List<Breakpoint> {
        return breakpoints.values.map { it.breakpoint }
    }

    /**
     * 获取指定断点
     */
    fun getBreakpoint(id: Int): Breakpoint? {
        return breakpoints[id]?.breakpoint
    }

    /**
     * 处理类加载事件
     * 在类加载时检查是否有待设置的断点
     */
    fun onClassPrepared(className: String, referenceType: ReferenceType) {
        // 检查是否有待设置的断点
        val sourceFile = try {
            referenceType.sourceName()
        } catch (e: AbsentInformationException) {
            return
        }

        val pending = pendingBreakpoints[sourceFile] ?: return

        pending.forEach { pendingBp ->
            setBreakpointInClass(referenceType, pendingBp.line, pendingBp.breakpointId)
        }

        // 清理待处理的断点
        pendingBreakpoints.remove(sourceFile)
    }

    /**
     * 尝试在已加载的类中设置断点
     */
    private fun trySetBreakpoint(file: String, line: Int, breakpointId: Int, condition: String?): Boolean {
        var found = false

        try {
            for (refType in vm.allClasses()) {
                try {
                    val sourceName = refType.sourceName()

                    if (sourceName == file || sourceName?.endsWith("/$file") == true || file.endsWith(sourceName ?: "")) {
                        if (setBreakpointInClass(refType, line, breakpointId)) {
                            found = true
                        }
                    }
                } catch (e: AbsentInformationException) {
                    // 类没有调试信息，跳过
                }
            }
        } catch (e: Exception) {
            // VM可能已经断开连接
            System.err.println("Failed to access VM classes: ${e.message}")
        }

        return found
    }

    /**
     * 在指定类中设置行断点
     */
    private fun setBreakpointInClass(refType: ReferenceType, line: Int, breakpointId: Int): Boolean {
        val locations = refType.safeLocationsOfLine(line)
        if (locations.isEmpty()) {
            return false
        }

        val entry = breakpoints[breakpointId] ?: return false

        for (location in locations) {
            try {
                val request = vm.eventRequestManager().createBreakpointRequest(location)
                request.setSuspendPolicy(com.sun.jdi.request.EventRequest.SUSPEND_ALL)
                request.enable()

                entry.requests.add(request)
                eventHandler.registerBreakpoint(request, breakpointId)
            } catch (e: Exception) {
                System.err.println("Failed to create breakpoint request: ${e.message}")
            }
        }

        return true
    }

    /**
     * 尝试设置方法断点
     */
    private fun trySetMethodBreakpoint(className: String, methodName: String, breakpointId: Int): Boolean {
        val refTypes = vm.classesByName(className)
        if (refTypes.isEmpty()) {
            // 类未加载，添加类准备监听
            addClassPrepareRequest(className)
            return false
        }

        var found = false
        for (refType in refTypes) {
            for (method in refType.methodsByName(methodName)) {
                val location = method.location()
                if (location != null) {
                    val entry = breakpoints[breakpointId] ?: continue

                    val request = vm.eventRequestManager().createBreakpointRequest(location)
                    request.setSuspendPolicy(com.sun.jdi.request.EventRequest.SUSPEND_ALL)
                    request.enable()

                    entry.requests.add(request)
                    eventHandler.registerBreakpoint(request, breakpointId)
                    found = true
                }
            }
        }

        return found
    }

    /**
     * 添加待处理断点
     */
    private fun addPendingBreakpoint(file: String, breakpointId: Int, line: Int, condition: String?) {
        pendingBreakpoints.computeIfAbsent(file) { mutableListOf() }
            .add(PendingBreakpoint(breakpointId, line, condition))

        // 添加类准备监听
        addClassPrepareRequest("*")
    }

    /**
     * 添加类准备请求
     */
    private fun addClassPrepareRequest(classPattern: String) {
        if (classPrepareRequests.containsKey(classPattern)) return

        val request = vm.eventRequestManager().createClassPrepareRequest()
        request.addClassFilter(classPattern)
        request.setSuspendPolicy(com.sun.jdi.request.EventRequest.SUSPEND_ALL)
        request.enable()

        classPrepareRequests[classPattern] = request
    }

    /**
     * 清理所有断点
     */
    fun clear() {
        breakpoints.values.forEach { entry ->
            entry.requests.forEach { request ->
                eventHandler.unregisterBreakpoint(request)
                try {
                    vm.eventRequestManager().deleteEventRequest(request)
                } catch (e: Exception) {
                    // 忽略清理时的错误
                }
            }
        }
        breakpoints.clear()
        pendingBreakpoints.clear()

        classPrepareRequests.values.forEach { request ->
            try {
                vm.eventRequestManager().deleteEventRequest(request)
            } catch (e: Exception) {
                // 忽略
            }
        }
        classPrepareRequests.clear()
    }
}
