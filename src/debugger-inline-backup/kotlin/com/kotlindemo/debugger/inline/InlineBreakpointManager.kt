package com.kotlindemo.debugger.inline

import com.sun.jdi.ClassType
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.EventRequestManager

/**
 * 内联断点管理器
 * 负责管理内联函数中的断点
 */
class InlineBreakpointManager(
    private val virtualMachine: VirtualMachine,
    private val eventRequestManager: EventRequestManager,
    private val debugInfoTracker: InlineDebugInfoTracker
) {

    private val breakpoints = mutableMapOf<String, InlineBreakpoint>()
    private val smapParser = SMAPParser()

    /**
     * 内联断点信息
     */
    data class InlineBreakpoint(
        val id: String,
        val sourceLocation: SourceLocation,
        val condition: String?,
        val hitCount: Int,
        val locations: List<BreakpointLocation>,
        val isActive: Boolean = true
    )

    /**
     * 在内联函数中设置断点
     */
    fun setInlineBreakpoint(
        sourceLocation: SourceLocation,
        className: String,
        methodName: String? = null,
        condition: String? = null,
        hitCount: Int = 0
    ): String {
        val breakpointId = generateBreakpointId(sourceLocation)

        // 获取类类型
        val classType = virtualMachine.classesByName(className)
            .filterIsInstance<ClassType>()
            .firstOrNull()
            ?: throw IllegalArgumentException("Class not found: $className")

        // 初始化调试信息
        debugInfoTracker.initializeDebugInfo(classType)

        // 查找对应的字节码位置
        val locations = resolveBreakpointLocations(
            sourceLocation,
            classType,
            methodName
        )

        if (locations.isEmpty()) {
            throw IllegalArgumentException("Cannot resolve breakpoint location")
        }

        // 创建断点请求
        val breakpointRequests = locations.map { location ->
            createBreakpointRequest(location, condition, hitCount)
        }

        // 创建内联断点对象
        val inlineBreakpoint = InlineBreakpoint(
            id = breakpointId,
            sourceLocation = sourceLocation,
            condition = condition,
            hitCount = hitCount,
            locations = locations
        )

        breakpoints[breakpointId] = inlineBreakpoint

        return breakpointId
    }

    /**
     * 解析断点位置
     */
    private fun resolveBreakpointLocations(
        sourceLocation: SourceLocation,
        classType: ClassType,
        methodName: String?
    ): List<BreakpointLocation> {
        val locations = mutableListOf<BreakpointLocation>()

        // 遍历所有方法，查找匹配的位置
        val methodsToSearch = if (methodName != null) {
            classType.allMethods().filter { it.name() == methodName }
        } else {
            classType.allMethods()
        }

        methodsToSearch.forEach { method ->
            val methodLocations = resolveMethodBreakpointLocations(
                sourceLocation,
                method,
                classType
            )
            locations.addAll(methodLocations)
        }

        return locations
    }

    /**
     * 解析单个方法中的断点位置
     */
    private fun resolveMethodBreakpointLocations(
        sourceLocation: SourceLocation,
        method: Method,
        classType: ClassType
    ): List<BreakpointLocation> {
        val locations = mutableListOf<BreakpointLocation>()

        try {
            // 获取SMAP信息
            val debugInfo = debugInfoTracker.initializeDebugInfo(classType)
            val sourceMapInfo = debugInfo.sourceMap[method.name()]

            if (sourceMapInfo != null) {
                // 使用SMAP解析器映射位置
                val bytecodeLocations = smapParser.mapSourceToBytecode(
                    sourceLocation,
                    sourceMapInfo,
                    method
                )

                // 查找对应的内联帧信息
                val inlineFrames = findInlineFramesAtLocation(
                    sourceLocation,
                    sourceMapInfo,
                    method
                )

                bytecodeLocations.forEach { location ->
                    val inlineFrame = inlineFrames.find { frame ->
                        location.lineNumber() >= frame.sourceLocation.lineNumber &&
                        location.lineNumber() <= frame.sourceLocation.lineNumber + 10 // 简单的范围检查
                    }

                    locations.add(
                        BreakpointLocation(
                            bytecodeLocation = location,
                            sourceLocation = sourceLocation,
                            inlineFrame = inlineFrame
                        )
                    )
                }
            } else {
                // 回退到简单的行号匹配
                method.allLineLocations().forEach { location ->
                    if (location.lineNumber() == sourceLocation.lineNumber) {
                        locations.add(
                            BreakpointLocation(
                                bytecodeLocation = location,
                                sourceLocation = sourceLocation,
                                inlineFrame = null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }

        return locations
    }

    /**
     * 查找位置对应的内联帧
     */
    private fun findInlineFramesAtLocation(
        sourceLocation: SourceLocation,
        sourceMapInfo: SourceMapInfo,
        method: Method
    ): List<InlineFrame> {
        // 这里需要根据SMAP信息和方法的内联信息来构建InlineFrame
        // 暂时返回空列表
        return emptyList()
    }

    /**
     * 创建断点请求
     */
    private fun createBreakpointRequest(
        location: Location,
        condition: String?,
        hitCount: Int
    ): BreakpointRequest {
        val request = eventRequestManager.createBreakpointRequest(location)

        // 设置条件
        if (!condition.isNullOrEmpty()) {
            request.addCountFilter(hitCount)
        }

        // 设置命中次数
        if (hitCount > 0) {
            request.addCountFilter(hitCount)
        }

        // 设置挂起策略
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

        return request
    }

    /**
     * 删除断点
     */
    fun removeBreakpoint(breakpointId: String): Boolean {
        val breakpoint = breakpoints[breakpointId] ?: return false

        // 删除所有相关的断点请求
        eventRequestManager.breakpointRequests().forEach { request ->
            if (breakpoint.locations.any { it.bytecodeLocation == request.location() }) {
                eventRequestManager.deleteEventRequest(request)
            }
        }

        breakpoints.remove(breakpointId)
        return true
    }

    /**
     * 获取所有断点
     */
    fun getAllBreakpoints(): List<InlineBreakpoint> {
        return breakpoints.values.toList()
    }

    /**
     * 根据ID获取断点
     */
    fun getBreakpoint(breakpointId: String): InlineBreakpoint? {
        return breakpoints[breakpointId]
    }

    /**
     * 激活/停用断点
     */
    fun setBreakpointEnabled(breakpointId: String, enabled: Boolean): Boolean {
        val breakpoint = breakpoints[breakpointId] ?: return false

        eventRequestManager.breakpointRequests().forEach { request ->
            if (breakpoint.locations.any { it.bytecodeLocation == request.location() }) {
                if (enabled) {
                    request.enable()
                } else {
                    request.disable()
                }
            }
        }

        // 更新断点状态
        val updatedBreakpoint = breakpoint.copy(isActive = enabled)
        breakpoints[breakpointId] = updatedBreakpoint

        return true
    }

    /**
     * 处理断点事件
     */
    fun handleBreakpointEvent(event: BreakpointEvent): InlineBreakpointEvent? {
        val location = event.location()

        // 查找对应的断点
        val matchingBreakpoint = breakpoints.values.find { breakpoint ->
            breakpoint.locations.any { it.bytecodeLocation == location }
        }

        if (matchingBreakpoint != null) {
            // 获取断点位置信息
            val breakpointLocation = matchingBreakpoint.locations.find {
                it.bytecodeLocation == location
            }

            // 重建内联栈
            val thread = event.thread()
            val className = location.declaringType().name()
            val inlineStack = debugInfoTracker.rebuildInlineStack(
                thread,
                0,
                className
            )

            return InlineBreakpointEvent(
                originalEvent = event,
                breakpoint = matchingBreakpoint,
                location = breakpointLocation,
                inlineStack = inlineStack
            )
        }

        return null
    }

    /**
     * 生成断点ID
     */
    private fun generateBreakpointId(sourceLocation: SourceLocation): String {
        return "${sourceLocation.sourceName}:${sourceLocation.lineNumber}:${System.currentTimeMillis()}"
    }

    /**
     * 检查位置是否可以设置断点
     */
    fun canSetBreakpoint(
        sourceLocation: SourceLocation,
        className: String
    ): Boolean {
        val classType = virtualMachine.classesByName(className)
            .filterIsInstance<ClassType>()
            .firstOrNull()
            ?: return false

        try {
            // 查找对应的位置
            val locations = resolveBreakpointLocations(
                sourceLocation,
                classType,
                null
            )

            return locations.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 更新断点条件
     */
    fun updateBreakpointCondition(
        breakpointId: String,
        condition: String?
    ): Boolean {
        val breakpoint = breakpoints[breakpointId] ?: return false

        // 删除现有的断点请求
        removeBreakpoint(breakpointId)

        // 重新创建断点请求
        val classType = virtualMachine.classesByName(breakpoint.sourceLocation.sourcePath)
            .filterIsInstance<ClassType>()
            .firstOrNull()
            ?: return false

        val newBreakpointId = setInlineBreakpoint(
            sourceLocation = breakpoint.sourceLocation,
            className = classType.name(),
            condition = condition,
            hitCount = breakpoint.hitCount
        )

        // 保持原ID，复制新的断点信息
        breakpoints[breakpointId] = breakpoints[newBreakpointId]!!.copy(id = breakpointId)
        breakpoints.remove(newBreakpointId)

        return true
    }
}

/**
 * 内联断点事件
 */
data class InlineBreakpointEvent(
    val originalEvent: BreakpointEvent,
    val breakpoint: InlineBreakpointManager.InlineBreakpoint,
    val location: BreakpointLocation?,
    val inlineStack: List<InlineFrame>
)