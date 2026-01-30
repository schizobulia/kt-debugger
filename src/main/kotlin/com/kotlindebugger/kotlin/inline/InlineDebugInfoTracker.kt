package com.kotlindebugger.kotlin.inline

import com.kotlindebugger.kotlin.smap.SMAP
import com.kotlindebugger.kotlin.smap.SMAPCache
import com.kotlindebugger.kotlin.smap.SMAPParser
import com.sun.jdi.ClassType
import com.sun.jdi.Location
import com.sun.jdi.LocalVariable
import com.sun.jdi.Method
import com.sun.jdi.StackFrame
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

/**
 * 内联调试信息跟踪器
 * 负责跟踪内联函数的调用信息和管理内联栈
 * Reference: JetBrains/intellij-community
 */
class InlineDebugInfoTracker(
    private val smapCache: SMAPCache = SMAPCache()
) {

    private val inlineStack = mutableListOf<InlineFrame>()
    private val debugInfoCache = mutableMapOf<String, InlineDebugInfo>()

    /**
     * 内联调试信息
     */
    data class InlineDebugInfo(
        val smap: SMAP?,
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

        val smap = extractSMAP(classType)
        val inlineMethods = extractInlineMethods(classType)
        val variableMappings = extractVariableMappings(classType)

        val debugInfo = InlineDebugInfo(
            smap = smap,
            inlineMethods = inlineMethods,
            variableMappings = variableMappings
        )

        debugInfoCache[className] = debugInfo
        return debugInfo
    }

    /**
     * 从类中提取SMAP信息
     */
    private fun extractSMAP(classType: ClassType): SMAP? {
        val className = classType.name()
        
        // 先从缓存获取
        smapCache.get(className)?.let { return it }
        
        // 从类中提取SMAP字符串
        val smapString = SMAPCache.extractFromReferenceType(classType)
        return smapCache.getOrParse(className, smapString)
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
        val lineNumber = location.lineNumber()
        val smap = debugInfo.smap ?: return

        // 使用SMAP查找源位置
        val sourceResult = smap.findSourcePosition(lineNumber)
        if (sourceResult != null) {
            val sourceLocation = SourceLocation(
                sourcePath = sourceResult.sourcePath,
                sourceName = sourceResult.sourceFile,
                lineNumber = sourceResult.sourceLine
            )

            // 查找这个位置对应的内联方法
            val inlineMethods = findInlineMethodsAtLocation(
                sourceResult.sourceLine,
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
            if (methodInlineInfos.isNotEmpty()) {
                inlineMethods[method.name()] = methodInlineInfos
            }
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
            // 这里可以通过分析方法名和SMAP信息来识别内联函数
            val methodName = method.name()
            
            // 检查是否包含内联标记
            if (methodName.contains("\$\$inlined\$") || methodName.contains("\$inlined\$")) {
                // 尝试从方法名中提取原始函数名
                val originalName = extractOriginalFunctionName(methodName)
                
                try {
                    val locations = method.allLineLocations()
                    if (locations.isNotEmpty()) {
                        val startLine = locations.first().lineNumber()
                        val endLine = locations.last().lineNumber()
                        
                        inlineInfos.add(
                            InlineMethodInfo(
                                methodName = originalName,
                                inlineStartLine = startLine,
                                inlineEndLine = endLine,
                                capturedVariables = emptyList(),
                                inlineDepth = 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    // 忽略无法获取行信息的方法
                }
            }
        } catch (e: Exception) {
            // 忽略无法解析的方法
        }

        return inlineInfos
    }

    /**
     * 从方法名中提取原始函数名
     */
    private fun extractOriginalFunctionName(methodName: String): String {
        // 从类似 "invoke$$inlined$forEach$1" 的名称中提取 "forEach"
        val inlinedIndex = methodName.indexOf("\$\$inlined\$")
        if (inlinedIndex >= 0) {
            val afterInlined = methodName.substring(inlinedIndex + 10)
            val dollarIndex = afterInlined.indexOf('$')
            return if (dollarIndex >= 0) {
                afterInlined.substring(0, dollarIndex)
            } else {
                afterInlined
            }
        }
        
        val simpleInlinedIndex = methodName.indexOf("\$inlined\$")
        if (simpleInlinedIndex >= 0) {
            val afterInlined = methodName.substring(simpleInlinedIndex + 9)
            val dollarIndex = afterInlined.indexOf('$')
            return if (dollarIndex >= 0) {
                afterInlined.substring(0, dollarIndex)
            } else {
                afterInlined
            }
        }
        
        return methodName
    }

    /**
     * 提取变量映射信息
     */
    private fun extractVariableMappings(classType: ClassType): Map<String, Map<String, LocalVariable>> {
        val variableMappings = mutableMapOf<String, Map<String, LocalVariable>>()

        classType.allMethods().forEach { method ->
            try {
                val mappings = method.variables().associateBy { it.name() }
                variableMappings[method.name()] = mappings
            } catch (e: Exception) {
                // 忽略无法获取变量信息的方法
            }
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
        val smap = debugInfo.smap ?: return false

        val lineNumber = location.lineNumber()
        return smap.findSourcePosition(lineNumber) != null
    }

    /**
     * 获取内联深度
     */
    fun getInlineDepth(location: Location, className: String): Int {
        return getCurrentInlineStack().size
    }

    /**
     * 获取调试信息缓存
     */
    fun getDebugInfo(className: String): InlineDebugInfo? {
        return debugInfoCache[className]
    }

    /**
     * 清除调试信息缓存
     */
    fun clearDebugInfoCache() {
        debugInfoCache.clear()
    }
}
