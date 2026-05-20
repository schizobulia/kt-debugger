package com.kotlindebugger.core.breakpoint

import com.kotlindebugger.common.model.Breakpoint
import com.kotlindebugger.common.model.SourcePosition
import com.kotlindebugger.common.util.JdiUtils.safeLocationsOfLine
import com.kotlindebugger.core.event.EventHandler
import com.kotlindebugger.dap.Logger
import com.kotlindebugger.kotlin.inline.InlineBreakpointManager
import com.kotlindebugger.kotlin.inline.InlineDebugInfoTracker
import com.kotlindebugger.kotlin.smap.SMAPCache
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
    private val eventHandler: EventHandler,
    private val smapCache: SMAPCache = SMAPCache()
) {

    // 内联断点管理器（延迟初始化）
    private var inlineBreakpointManager: InlineBreakpointManager? = null
    private var inlineDebugInfoTracker: InlineDebugInfoTracker? = null

    init {
        // 在初始化时就设置全局 ClassPrepareRequest，监听所有类的加载
        setupGlobalClassPrepareRequest()
    }

    /**
     * 设置全局 ClassPrepareRequest
     * 在 BreakpointManager 初始化时调用，确保能捕获所有类的加载事件
     */
    private fun setupGlobalClassPrepareRequest() {
        try {
            val request = vm.eventRequestManager().createClassPrepareRequest()
            // 不添加 filter，监听所有类
            request.setSuspendPolicy(com.sun.jdi.request.EventRequest.SUSPEND_EVENT_THREAD)
            request.enable()
            classPrepareRequests["*"] = request
        } catch (e: Exception) {
            // 忽略创建失败
        }
    }

    /**
     * 初始化内联断点支持
     * 创建内联调试信息跟踪器和内联断点管理器，以支持在内联函数中设置断点
     */
    fun initializeInlineSupport() {
        if (inlineDebugInfoTracker == null) {
            inlineDebugInfoTracker = InlineDebugInfoTracker(smapCache)
        }
        if (inlineBreakpointManager == null) {
            inlineBreakpointManager = InlineBreakpointManager(
                virtualMachine = vm,
                eventRequestManager = vm.eventRequestManager(),
                debugInfoTracker = inlineDebugInfoTracker!!
            )
        }
    }

    /**
     * 获取内联断点管理器
     */
    fun getInlineBreakpointManager(): InlineBreakpointManager? = inlineBreakpointManager

    /**
     * 获取内联调试信息跟踪器
     */
    fun getInlineDebugInfoTracker(): InlineDebugInfoTracker? = inlineDebugInfoTracker

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
        val condition: String?,
        // Logpoint 消息模板
        val logMessage: String? = null,
        // 命中次数过滤（>0 表示应用 hitCount 过滤）
        val hitCount: Int = 0
    )

    /**
     * 添加行断点
     * @param file 源文件名（如 Main.kt）
     * @param line 行号
     * @param condition 条件表达式（可选）
     * @param logMessage Logpoint 消息模板（可选，设置后命中时打印日志而非暂停）
     * @param hitCount 命中次数（>0 表示在第 N 次命中时才触发）
     * @return 创建的断点，如果无法创建则返回 null
     */
    fun addLineBreakpoint(file: String, line: Int, condition: String? = null, logMessage: String? = null, hitCount: Int = 0): Breakpoint {
        val id = nextId.getAndIncrement()
        val breakpoint = Breakpoint.LineBreakpoint(
            id = id,
            file = file,
            line = line,
            enabled = true,
            condition = condition,
            hitCount = hitCount,
            logMessage = logMessage
        )

        val entry = BreakpointEntry(breakpoint)
        breakpoints[id] = entry

        // 尝试在已加载的类中设置断点
        val classesSet = trySetBreakpoint(file, line, id, condition)

        if (!classesSet) {
            // 如果没有找到匹配的类，添加到待处理列表
            addPendingBreakpoint(file, id, line, condition, logMessage, hitCount)
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
     * 检查指定文件和行号是否有断点
     * @param fileName 源文件名
     * @param line 行号
     * @return 如果该行有启用的断点返回 true，否则返回 false
     */
    fun hasBreakpointAt(fileName: String, line: Int): Boolean {
        return breakpoints.values.any { entry ->
            val bp = entry.breakpoint
            bp.enabled && when (bp) {
                is Breakpoint.LineBreakpoint -> {
                    // 使用路径分隔符正确匹配文件名
                    val matches = filesMatch(bp.file, fileName)
                    matches && bp.line == line
                }
                is Breakpoint.MethodBreakpoint -> false  // 方法断点不按行匹配
            }
        }
    }

    /**
     * 检查两个文件路径是否匹配
     * 支持完整路径匹配、纯文件名匹配和路径后缀匹配
     */
    private fun filesMatch(file1: String, file2: String): Boolean {
        // 精确匹配
        if (file1 == file2) return true
        
        // 标准化路径分隔符
        val normalized1 = file1.replace('\\', '/')
        val normalized2 = file2.replace('\\', '/')
        
        if (normalized1 == normalized2) return true
        
        // 提取纯文件名进行比较
        val name1 = normalized1.substringAfterLast('/')
        val name2 = normalized2.substringAfterLast('/')
        
        // 如果其中一个是纯文件名（不包含路径），则只比较文件名
        if (!normalized1.contains('/') || !normalized2.contains('/')) {
            return name1 == name2
        }
        
        // 检查是否一个路径是另一个的后缀（必须是完整的路径组件）
        if (normalized1.endsWith("/$name2") || normalized2.endsWith("/$name1")) {
            return true
        }
        
        return false
    }

    /**
     * 处理类加载事件
     * 在类加载时检查是否有待设置的断点。
     *
     * 注意：同一个源文件可能对应多个 JVM 类（如顶层类、伴生对象、内联函数展开类等），
     * 断点所在行不一定属于第一个加载的类。因此只移除已成功安装的断点，
     * 未安装的保留供后续同源文件的其他类加载时继续尝试，避免断点遗漏。
     */
    fun onClassPrepared(className: String, referenceType: ReferenceType) {
        // 检查是否有待设置的断点
        val sourceFile = try {
            referenceType.sourceName()
        } catch (e: AbsentInformationException) {
            return
        }

        val pending = pendingBreakpoints[sourceFile] ?: return

        // 收集在当前类中成功安装的断点 ID
        val installedIds = mutableSetOf<Int>()
        pending.forEach { pendingBp ->
            if (setBreakpointInClass(referenceType, pendingBp.line, pendingBp.breakpointId)) {
                installedIds.add(pendingBp.breakpointId)
            }
        }

        // 只移除已安装的断点，其余保留等待后续类加载
        if (installedIds.isNotEmpty()) {
            pending.removeIf { it.breakpointId in installedIds }
            if (pending.isEmpty()) {
                pendingBreakpoints.remove(sourceFile)
            }
        }
    }

    /**
     * 获取指定源文件的待处理断点数（仅用于测试）
     */
    internal fun getPendingBreakpointCount(sourceFile: String): Int =
        pendingBreakpoints[sourceFile]?.size ?: 0

    /**
     * 尝试在已加载的类中设置断点
     */
    private fun trySetBreakpoint(file: String, line: Int, breakpointId: Int, condition: String?): Boolean {
        var found = false

        try {
            val allClasses = vm.allClasses()
            
            for (refType in allClasses) {
                try {
                    val sourceName = refType.sourceName()
                    
                    // 检查是否匹配
                    val matches = sourceName == file || 
                                  sourceName?.endsWith("/$file") == true || 
                                  file.endsWith(sourceName ?: "")
                    
                    if (matches) {
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
        
        // 获取断点条件和 Logpoint 消息
        val condition = entry.breakpoint.condition
        val logMessage = (entry.breakpoint as? Breakpoint.LineBreakpoint)?.logMessage
        // 获取 hitCount （命中次数过滤）
        val hitCount = (entry.breakpoint as? Breakpoint.LineBreakpoint)?.hitCount ?: 0

        for (location in locations) {
            try {
                val request = vm.eventRequestManager().createBreakpointRequest(location)
                request.setSuspendPolicy(com.sun.jdi.request.EventRequest.SUSPEND_ALL)
                // 如果设置了 hitCount，添加命中次数过滤
                if (hitCount > 0) {
                    request.addCountFilter(hitCount)
                }
                request.enable()

                entry.requests.add(request)
                // 传递条件和 logMessage 信息给事件处理器
                eventHandler.registerBreakpoint(request, breakpointId, condition, logMessage)
            } catch (e: Exception) {
                Logger.error("Failed to create breakpoint request: ${e.message}")
            }
        }

        return true
    }

    /**
     * 尝试设置方法断点
     * className 为 "*" 时搜索所有已加载的类
     */
    private fun trySetMethodBreakpoint(className: String, methodName: String, breakpointId: Int): Boolean {
        val refTypes = if (className == "*") {
            vm.allClasses()
        } else {
            vm.classesByName(className)
        }

        if (refTypes.isEmpty()) {
            // 类未加载，添加类准备监听
            if (className != "*") {
                addClassPrepareRequest(className)
            }
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
    private fun addPendingBreakpoint(file: String, breakpointId: Int, line: Int, condition: String?, logMessage: String? = null, hitCount: Int = 0) {
        pendingBreakpoints.computeIfAbsent(file) { mutableListOf() }
            .add(PendingBreakpoint(breakpointId, line, condition, logMessage, hitCount))
        // 全局 ClassPrepareRequest 已在初始化时设置，无需再次添加
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
