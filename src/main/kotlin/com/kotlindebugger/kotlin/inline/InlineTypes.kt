package com.kotlindebugger.kotlin.inline

import com.sun.jdi.Location
import com.sun.jdi.LocalVariable

/**
 * 内联调试相关的数据类型定义
 * Reference: JetBrains/intellij-community
 */

/**
 * 源码位置信息
 */
data class SourceLocation(
    val sourcePath: String,
    val sourceName: String,
    val lineNumber: Int,
    val columnNumber: Int = 0
)

/**
 * 内联帧信息
 */
data class InlineFrame(
    val functionName: String,
    val sourceLocation: SourceLocation,
    val inlineDepth: Int,
    val variableMapping: Map<String, LocalVariable>,
    val originalLocation: Location?, // 原始字节码位置
    val parentFrame: InlineFrame? = null
)

/**
 * 内联上下文信息
 */
data class InlineContext(
    val inlineFrames: List<InlineFrame>,
    val currentDepth: Int,
    val capturedVariables: Map<String, Any>
)

/**
 * 断点位置信息
 */
data class BreakpointLocation(
    val bytecodeLocation: Location,
    val sourceLocation: SourceLocation,
    val inlineFrame: InlineFrame?
)

/**
 * 调试变量映射
 */
data class DebugVariable(
    val name: String,
    val type: String,
    val value: Any?,
    val scope: VariableScope,
    val inlineFrame: InlineFrame?
)

/**
 * 变量作用域
 */
enum class VariableScope {
    LOCAL,
    CAPTURED,
    PARAMETER,
    RECEIVER
}

/**
 * SMAP信息（用于内联调试）
 */
data class SourceMapInfo(
    val stratum: String,
    val fileId: String,
    val path: String,
    val lineMappings: List<LineMapping>
)

/**
 * 行号映射
 */
data class LineMapping(
    val inputStartLine: Int,
    val inputLineCount: Int,
    val outputStartLine: Int,
    val outputLineCount: Int,
    val repeatCount: Int = 1
)
