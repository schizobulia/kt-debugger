package com.kotlindebugger.kotlin.inline

import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.Location
import com.sun.jdi.StackFrame
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine
import com.sun.jdi.ClassType

/**
 * 内联栈帧构建器
 * 负责构建内联函数的虚拟调用栈
 * Reference: JetBrains/intellij-community
 */
class InlineStackBuilder(
    private val virtualMachine: VirtualMachine,
    private val debugInfoTracker: InlineDebugInfoTracker
) {

    /**
     * 构建包含内联信息的完整调用栈
     */
    fun buildCompleteStack(
        thread: ThreadReference
    ): List<InlineStackFrameWrapper> {
        val completeStack = mutableListOf<InlineStackFrameWrapper>()

        try {
            val frames = thread.frames()

            frames.forEachIndexed { index, originalFrame ->
                val location = originalFrame.location()
                val className = location.declaringType().name()

                // 初始化调试信息
                if (location.declaringType() is ClassType) {
                    debugInfoTracker.initializeDebugInfo(
                        location.declaringType() as ClassType
                    )
                }

                // 重建内联栈
                val inlineFrames = debugInfoTracker.rebuildInlineStack(
                    thread,
                    index,
                    className
                )

                if (inlineFrames.isNotEmpty()) {
                    // 添加内联栈帧
                    inlineFrames.forEach { inlineFrame ->
                        completeStack.add(
                            InlineStackFrameWrapper(
                                originalFrame = originalFrame,
                                inlineFrame = inlineFrame,
                                isVirtual = true,
                                thread = thread
                            )
                        )
                    }
                }

                // 添加原始栈帧
                completeStack.add(
                    InlineStackFrameWrapper(
                        originalFrame = originalFrame,
                        inlineFrame = null,
                        isVirtual = false,
                        thread = thread
                    )
                )
            }

        } catch (e: IncompatibleThreadStateException) {
            // 线程不在可调试状态，返回空列表
        }

        return completeStack
    }

    /**
     * 构建单个内联栈帧
     */
    fun buildInlineFrame(
        originalFrame: StackFrame,
        inlineFrame: InlineFrame,
        thread: ThreadReference
    ): InlineStackFrameWrapper {
        return InlineStackFrameWrapper(
            originalFrame = originalFrame,
            inlineFrame = inlineFrame,
            isVirtual = true,
            thread = thread
        )
    }

    /**
     * 获取内联函数中的变量
     */
    fun getInlineVariables(
        wrapper: InlineStackFrameWrapper
    ): List<DebugVariable> {
        val variables = mutableListOf<DebugVariable>()

        try {
            if (wrapper.inlineFrame == null) {
                // 原始栈帧的变量
                wrapper.originalFrame.visibleVariables().forEach { localVar ->
                    val value = wrapper.originalFrame.getValue(localVar)
                    variables.add(
                        DebugVariable(
                            name = localVar.name(),
                            type = localVar.typeName(),
                            value = value,
                            scope = VariableScope.LOCAL,
                            inlineFrame = null
                        )
                    )
                }
            } else {
                // 内联栈帧的变量
                val inlineFrame = wrapper.inlineFrame
                inlineFrame.variableMapping.forEach { (name, localVar) ->
                    val value = debugInfoTracker.getInlineVariableValue(
                        wrapper.originalFrame,
                        name,
                        inlineFrame
                    )
                    variables.add(
                        DebugVariable(
                            name = name,
                            type = localVar.typeName(),
                            value = value,
                            scope = determineVariableScope(name, localVar.typeName()),
                            inlineFrame = inlineFrame
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 忽略获取变量失败的异常
        }

        return variables
    }

    /**
     * 确定变量作用域
     */
    private fun determineVariableScope(name: String, typeName: String): VariableScope {
        return when {
            name.startsWith("\$this") -> VariableScope.RECEIVER
            name.startsWith("\$") -> VariableScope.CAPTURED
            typeName.contains("Function") -> VariableScope.PARAMETER
            else -> VariableScope.LOCAL
        }
    }

    /**
     * 获取栈帧的源码位置
     */
    fun getSourceLocation(wrapper: InlineStackFrameWrapper): SourceLocation {
        return if (wrapper.inlineFrame != null) {
            wrapper.inlineFrame.sourceLocation
        } else {
            val location = wrapper.originalFrame.location()
            SourceLocation(
                sourcePath = try { location.sourcePath() ?: "" } catch (e: Exception) { "" },
                sourceName = try { location.sourceName() ?: "" } catch (e: Exception) { "" },
                lineNumber = location.lineNumber()
            )
        }
    }

    /**
     * 获取栈帧的方法名称
     */
    fun getMethodName(wrapper: InlineStackFrameWrapper): String {
        return if (wrapper.inlineFrame != null) {
            wrapper.inlineFrame.functionName
        } else {
            wrapper.originalFrame.location().method().name()
        }
    }

    /**
     * 评估表达式
     */
    fun evaluateExpression(
        wrapper: InlineStackFrameWrapper,
        expression: String
    ): Value? {
        try {
            // 这里需要实现表达式评估逻辑
            // 可以使用JDI的评估功能或者集成表达式解释器
            return evaluateInContext(wrapper, expression)
        } catch (e: Exception) {
            // 表达式评估失败
            return null
        }
    }

    /**
     * 在特定上下文中评估表达式
     */
    private fun evaluateInContext(
        wrapper: InlineStackFrameWrapper,
        expression: String
    ): Value? {
        // 简单的表达式评估实现
        // 实际实现需要更复杂的表达式解析和评估
        val variables = getInlineVariables(wrapper)
        val variableMap = variables.associateBy { it.name }

        // 如果表达式是简单的变量名，直接返回变量值
        val variable = variableMap[expression]
        if (variable != null) {
            return variable.value as? Value
        }

        return null
    }
}

/**
 * 内联栈帧包装器
 * 统一处理原始栈帧和虚拟内联栈帧
 */
class InlineStackFrameWrapper(
    val originalFrame: StackFrame,
    val inlineFrame: InlineFrame?,
    val isVirtual: Boolean,
    val thread: ThreadReference
) {

    /**
     * 获取位置信息
     */
    fun location(): Location {
        return if (inlineFrame?.originalLocation != null) {
            inlineFrame.originalLocation!!
        } else {
            originalFrame.location()
        }
    }

    /**
     * 获取线程信息
     */
    fun threadReference(): ThreadReference {
        return thread
    }

    /**
     * 获取内联深度
     */
    fun inlineDepth(): Int {
        return if (inlineFrame != null) inlineFrame.inlineDepth else 0
    }

    /**
     * 获取变量值
     */
    fun getValue(variableName: String): Value? {
        try {
            if (inlineFrame != null) {
                // 从内联帧中查找变量
                val localVar = inlineFrame.variableMapping[variableName]
                if (localVar != null) {
                    return originalFrame.getValue(localVar)
                }
            } else {
                // 从原始栈帧中查找变量
                val localVar = originalFrame.visibleVariables().find { it.name() == variableName }
                if (localVar != null) {
                    return originalFrame.getValue(localVar)
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        }
        return null
    }

    /**
     * 获取所有可见变量
     */
    fun visibleVariables(): List<DebugVariable> {
        val builder = InlineStackBuilder(
            originalFrame.virtualMachine(),
            InlineDebugInfoTracker()
        )
        return builder.getInlineVariables(this)
    }

    /**
     * 判断是否是内联函数帧
     */
    fun isInline(): Boolean {
        return inlineFrame != null
    }

    /**
     * 获取父内联帧
     */
    fun parentInlineFrame(): InlineFrame? {
        return inlineFrame?.parentFrame
    }

    /**
     * 获取函数名
     */
    fun getFunctionName(): String {
        return if (inlineFrame != null) {
            inlineFrame.functionName
        } else {
            originalFrame.location().method().name()
        }
    }

    /**
     * 获取源文件名
     */
    fun getSourceName(): String? {
        return try {
            if (inlineFrame != null) {
                inlineFrame.sourceLocation.sourceName
            } else {
                originalFrame.location().sourceName()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取行号
     */
    fun getLineNumber(): Int {
        return if (inlineFrame != null) {
            inlineFrame.sourceLocation.lineNumber
        } else {
            originalFrame.location().lineNumber()
        }
    }
}
