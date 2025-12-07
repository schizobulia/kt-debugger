package com.kotlindemo.debugger.inline

import com.sun.jdi.ClassType
import com.sun.jdi.Location
import com.sun.jdi.LocalVariable
import com.sun.jdi.StackFrame
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

/**
 * 内联调试信息跟踪器
 * 负责跟踪内联函数的调用信息和管理内联栈
 */
class InlineDebugInfoTracker {

    private val inlineStack = mutableListOf<InlineFrame>()
    private val debugInfoCache = mutableMapOf<String, InlineDebugInfo>()
    private val smapParser = SMAPParser()

    /**
     * 内联调试信息
     */
    data class InlineDebugInfo(
        val sourceMap: Map<String, SourceMapInfo>,
        val inlineMethods: Map<String, List<InlineMethodInfo>>,
        val variableMappings: Map<String, Map<String, LocalVariable>>
    )

    /**
     * 内联方法信息
     */
    data class InlineMethodInfo(
        val methodName: String,
        val inlineStartLine: Int,
        val inlineEndLine: Int,
        val capturedVariables: List<String>,
        val inlineDepth: Int
    )

    /**
     * 初始化调试信息
     */
    fun initializeDebugInfo(classType: ClassType): InlineDebugInfo {
        val className = classType.name()
        if (debugInfoCache.containsKey(className)) {
            return debugInfoCache[className]!!
        }

        val sourceMaps = smapParser.parseSMAP(classType)
        val inlineMethods = extractInlineMethods(classType)
        val variableMappings = extractVariableMappings(classType)

        val debugInfo = InlineDebugInfo(
            sourceMap = sourceMaps,
            inlineMethods = inlineMethods,
            variableMappings = variableMappings
        )

        debugInfoCache[className] = debugInfo
        return debugInfo
    }

    /**
     * 进入内联函数
     */
    fun enterInlineFrame(
        functionName: String,
        sourceLocation: SourceLocation,
        location: Location,
        variables: List<LocalVariable>,
        inlineDepth: Int
    ) {
        val variableMapping = variables.associateBy { it.name() }

        val inlineFrame = InlineFrame(
            functionName = functionName,
            sourceLocation = sourceLocation,
            inlineDepth = inlineDepth,
            variableMapping = variableMapping,
            originalLocation = location,
            parentFrame = inlineStack.lastOrNull()
        )

        inlineStack.add(inlineFrame)
    }

    /**
     * 退出内联函数
     */
    fun exitInlineFrame() {
        if (inlineStack.isNotEmpty()) {
            inlineStack.removeLastOrNull()
        }
    }

    /**
     * 获取当前内联栈
     */
    fun getCurrentInlineStack(): List<InlineFrame> {
        return inlineStack.toList()
    }

    /**
     * 清空内联栈
     */
    fun clearInlineStack() {
        inlineStack.clear()
    }

    /**
     * 根据位置重建内联栈
     */
    fun rebuildInlineStack(
        thread: ThreadReference,
        frameIndex: Int,
        className: String
    ): List<InlineFrame> {
        clearInlineStack()

        val debugInfo = debugInfoCache[className] ?: return emptyList()

        try {
            // 获取当前栈帧
            val frame = thread.frame(frameIndex)
            val location = frame.location()

            // 根据SMAP信息重建内联栈
            rebuildStackFromLocation(location, debugInfo)

        } catch (e: Exception) {
            // 忽略异常，返回空栈
        }

        return getCurrentInlineStack()
    }

    /**
     * 根据位置重建栈
     */
    private fun rebuildStackFromLocation(
        location: Location,
        debugInfo: InlineDebugInfo
    ) {
        val methodName = location.method().name()
        val sourceMap = debugInfo.sourceMap[methodName]

        if (sourceMap != null) {
            val sourceLocation = smapParser.mapBytecodeToSource(location, sourceMap)
            if (sourceLocation != null) {
                // 查找这个位置对应的内联方法
                val inlineMethods = findInlineMethodsAtLocation(
                    sourceLocation.lineNumber,
                    debugInfo.inlineMethods
                )

                // 重建内联栈
                inlineMethods.forEachIndexed { index, method ->
                    enterInlineFrame(
                        functionName = method.methodName,
                        sourceLocation = sourceLocation.copy(lineNumber = method.inlineStartLine),
                        location = location,
                        variables = emptyList(),
                        inlineDepth = index + 1
                    )
                }
            }
        }
    }

    /**
     * 查找位置对应的内联方法
     */
    private fun findInlineMethodsAtLocation(
        lineNumber: Int,
        inlineMethods: Map<String, List<InlineMethodInfo>>
    ): List<InlineMethodInfo> {
        return inlineMethods.values.flatten().filter { method ->
            lineNumber >= method.inlineStartLine && lineNumber <= method.inlineEndLine
        }.sortedBy { it.inlineDepth }
    }

    /**
     * 提取内联方法信息
     */
    private fun extractInlineMethods(classType: ClassType): Map<String, List<InlineMethodInfo>> {
        val inlineMethods = mutableMapOf<String, List<InlineMethodInfo>>()

        classType.allMethods().forEach { method ->
            val methodInlineInfos = extractInlineMethodInfos(method)
            inlineMethods[method.name()] = methodInlineInfos
        }

        return inlineMethods
    }

    /**
     * 提取单个方法的内联信息
     */
    private fun extractInlineMethodInfos(method: Method): List<InlineMethodInfo> {
        val inlineInfos = mutableListOf<InlineMethodInfo>()

        try {
            // 分析方法的字节码，识别内联调用
            val locations = method.allLineLocations()

            // 这里需要更复杂的字节码分析逻辑
            // 暂时返回空列表，实际实现需要解析方法体中的内联调用

        } catch (e: Exception) {
            // 忽略无法解析的方法
        }

        return inlineInfos
    }

    /**
     * 提取变量映射信息
     */
    private fun extractVariableMappings(classType: ClassType): Map<String, Map<String, LocalVariable>> {
        val variableMappings = mutableMapOf<String, Map<String, LocalVariable>>()

        classType.allMethods().forEach { method ->
            val mappings = method.variables().associateBy { it.name() }
            variableMappings[method.name()] = mappings
        }

        return variableMappings
    }

    /**
     * 获取内联变量的值
     */
    fun getInlineVariableValue(
        frame: StackFrame,
        variableName: String,
        inlineFrame: InlineFrame
    ): Value? {
        try {
            // 首先尝试从当前栈帧获取
            val localVariable = inlineFrame.variableMapping[variableName]
            if (localVariable != null) {
                return frame.getValue(localVariable)
            }

            // 如果是捕获的变量，需要从外部作用域查找
            if (inlineFrame.parentFrame != null) {
                return getInlineVariableValue(frame, variableName, inlineFrame.parentFrame)
            }

        } catch (e: Exception) {
            // 忽略异常
        }

        return null
    }

    /**
     * 检查位置是否在内联函数中
     */
    fun isInInlineFunction(location: Location, className: String): Boolean {
        val debugInfo = debugInfoCache[className] ?: return false
        val methodName = location.method().name()
        val sourceMap = debugInfo.sourceMap[methodName] ?: return false

        val sourceLocation = smapParser.mapBytecodeToSource(location, sourceMap)
        return sourceLocation != null
    }

    /**
     * 获取内联深度
     */
    fun getInlineDepth(location: Location, className: String): Int {
        return getCurrentInlineStack().size
    }
}