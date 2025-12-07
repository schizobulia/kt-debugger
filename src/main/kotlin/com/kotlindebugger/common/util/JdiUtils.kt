package com.kotlindebugger.common.util

import com.sun.jdi.*
import com.kotlindebugger.common.model.ThreadStatus

/**
 * JDI 安全操作工具类
 * 封装常见的 JDI 操作，处理可能的异常
 */
object JdiUtils {

    /**
     * 安全获取 Location 的行号
     */
    fun Location.safeLineNumber(): Int {
        return try {
            lineNumber()
        } catch (e: AbsentInformationException) {
            -1
        }
    }

    /**
     * 安全获取 Location 的源文件名
     */
    fun Location.safeSourceName(): String? {
        return try {
            sourceName()
        } catch (e: AbsentInformationException) {
            null
        }
    }

    /**
     * 安全获取 Location 的源文件路径
     */
    fun Location.safeSourcePath(): String? {
        return try {
            sourcePath()
        } catch (e: AbsentInformationException) {
            null
        }
    }

    /**
     * 安全获取方法的所有行位置
     */
    fun Method.safeAllLineLocations(): List<Location> {
        return try {
            allLineLocations()
        } catch (e: AbsentInformationException) {
            emptyList()
        }
    }

    /**
     * 安全获取 ReferenceType 的所有行位置
     */
    fun ReferenceType.safeAllLineLocations(): List<Location> {
        return try {
            allLineLocations()
        } catch (e: AbsentInformationException) {
            emptyList()
        }
    }

    /**
     * 安全获取 ReferenceType 指定行的位置
     */
    fun ReferenceType.safeLocationsOfLine(line: Int): List<Location> {
        return try {
            locationsOfLine(line)
        } catch (e: AbsentInformationException) {
            emptyList()
        }
    }

    /**
     * 安全获取栈帧的可见变量
     */
    fun StackFrame.safeVisibleVariables(): List<LocalVariable> {
        return try {
            visibleVariables()
        } catch (e: AbsentInformationException) {
            emptyList()
        } catch (e: InvalidStackFrameException) {
            emptyList()
        }
    }

    /**
     * 安全获取栈帧中变量的值
     */
    fun StackFrame.safeGetValue(variable: LocalVariable): Value? {
        return try {
            getValue(variable)
        } catch (e: InvalidStackFrameException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * 安全获取对象的字段值
     */
    fun ObjectReference.safeGetValue(field: Field): Value? {
        return try {
            getValue(field)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 JDI Value 转换为可读字符串
     */
    fun Value?.toDisplayString(maxLength: Int = 100): String {
        if (this == null) return "null"

        return when (this) {
            is VoidValue -> "void"
            is BooleanValue -> value().toString()
            is ByteValue -> value().toString()
            is CharValue -> "'${value()}'"
            is ShortValue -> value().toString()
            is IntegerValue -> value().toString()
            is LongValue -> "${value()}L"
            is FloatValue -> "${value()}f"
            is DoubleValue -> value().toString()
            is StringReference -> {
                val str = value()
                if (str.length > maxLength) {
                    "\"${str.take(maxLength)}...\""
                } else {
                    "\"$str\""
                }
            }
            is ArrayReference -> {
                val length = length()
                "${type().name()}[$length]"
            }
            is ObjectReference -> {
                val typeName = referenceType().name()
                // 尝试调用 toString() 获取更友好的显示
                try {
                    val toStringMethod = referenceType().methodsByName("toString").firstOrNull {
                        it.argumentTypes().isEmpty()
                    }
                    if (toStringMethod != null && !typeName.startsWith("java.lang.")) {
                        "$typeName@${uniqueID()}"
                    } else {
                        "$typeName@${uniqueID()}"
                    }
                } catch (e: Exception) {
                    "$typeName@${uniqueID()}"
                }
            }
            else -> toString()
        }
    }

    /**
     * 获取类型的简短名称
     */
    fun Type.shortName(): String {
        val fullName = name()
        return when {
            fullName.contains('.') -> fullName.substringAfterLast('.')
            else -> fullName
        }
    }

    /**
     * 检查是否是 Kotlin 内部类
     */
    fun ReferenceType.isKotlinInternalClass(): Boolean {
        val name = name()
        return name.contains("\$\$") ||
                name.endsWith("\$DefaultImpls") ||
                name.endsWith("\$Companion") ||
                name.contains("\$lambda\$") ||
                name.contains("\$inlined\$")
    }

    /**
     * 获取线程状态
     */
    fun ThreadReference.getThreadStatus(): ThreadStatus {
        return when (status()) {
            ThreadReference.THREAD_STATUS_RUNNING -> ThreadStatus.RUNNING
            ThreadReference.THREAD_STATUS_SLEEPING -> ThreadStatus.SLEEPING
            ThreadReference.THREAD_STATUS_WAIT -> ThreadStatus.WAITING
            ThreadReference.THREAD_STATUS_MONITOR -> ThreadStatus.MONITOR
            ThreadReference.THREAD_STATUS_ZOMBIE -> ThreadStatus.ZOMBIE
            ThreadReference.THREAD_STATUS_NOT_STARTED -> ThreadStatus.NOT_STARTED
            else -> ThreadStatus.UNKNOWN
        }
    }
}
