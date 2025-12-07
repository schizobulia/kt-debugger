package com.kotlindebugger.core.variable

import com.kotlindebugger.common.model.VariableInfo
import com.kotlindebugger.common.util.JdiUtils.safeGetValue
import com.kotlindebugger.common.util.JdiUtils.safeVisibleVariables
import com.kotlindebugger.common.util.JdiUtils.toDisplayString
import com.kotlindebugger.common.util.JdiUtils.shortName
import com.sun.jdi.*

/**
 * 变量查看器
 * 负责获取和显示变量信息
 */
class VariableInspector(private val vm: VirtualMachine) {

    /**
     * 获取栈帧中的所有可见变量
     */
    fun getLocalVariables(frame: StackFrame): List<VariableInfo> {
        val variables = mutableListOf<VariableInfo>()

        // 获取 this 对象（如果有）
        try {
            val thisObject = frame.thisObject()
            if (thisObject != null) {
                variables.add(
                    VariableInfo(
                        name = "this",
                        typeName = thisObject.referenceType().name(),
                        value = formatValue(thisObject),
                        isLocal = false
                    )
                )
            }
        } catch (e: Exception) {
            // 静态方法没有 this
        }

        // 获取局部变量
        val localVars = frame.safeVisibleVariables()
        for (variable in localVars) {
            val value = frame.safeGetValue(variable)
            variables.add(
                VariableInfo(
                    name = variable.name(),
                    typeName = variable.typeName(),
                    value = formatValue(value),
                    isLocal = true
                )
            )
        }

        return variables
    }

    /**
     * 获取对象的字段
     */
    fun getObjectFields(objectRef: ObjectReference): List<VariableInfo> {
        val refType = objectRef.referenceType()
        val fields = refType.allFields()

        return fields.map { field ->
            val value = objectRef.safeGetValue(field)
            VariableInfo(
                name = field.name(),
                typeName = field.typeName(),
                value = formatValue(value),
                isLocal = false
            )
        }
    }

    /**
     * 获取数组元素
     */
    fun getArrayElements(arrayRef: ArrayReference, start: Int = 0, count: Int = -1): List<VariableInfo> {
        val length = arrayRef.length()
        val actualCount = if (count < 0) length - start else minOf(count, length - start)

        if (start >= length || actualCount <= 0) {
            return emptyList()
        }

        val values = arrayRef.getValues(start, actualCount)
        return values.mapIndexed { index, value ->
            VariableInfo(
                name = "[${start + index}]",
                typeName = arrayRef.type().shortName(),
                value = formatValue(value),
                isLocal = false
            )
        }
    }

    /**
     * 查找栈帧中的变量
     */
    fun findVariable(frame: StackFrame, name: String): Value? {
        // 先检查是否是 this
        if (name == "this") {
            return try {
                frame.thisObject()
            } catch (e: Exception) {
                null
            }
        }

        // 查找局部变量
        val variables = frame.safeVisibleVariables()
        val variable = variables.find { it.name() == name } ?: return null

        return frame.safeGetValue(variable)
    }

    /**
     * 获取变量的详细信息（包括子元素）
     */
    fun getVariableDetails(value: Value?, maxDepth: Int = 2): VariableDetails {
        return getValueDetails(value, 0, maxDepth)
    }

    /**
     * 递归获取值的详细信息
     */
    private fun getValueDetails(value: Value?, depth: Int, maxDepth: Int): VariableDetails {
        if (value == null) {
            return VariableDetails("null", "null", emptyList())
        }

        val typeName = value.type().name()
        val displayValue = formatValue(value)

        // 如果达到最大深度，不展开子元素
        if (depth >= maxDepth) {
            return VariableDetails(typeName, displayValue, emptyList())
        }

        val children = when (value) {
            is ArrayReference -> {
                val length = value.length()
                if (length <= MAX_ARRAY_DISPLAY) {
                    getArrayElements(value).map { varInfo ->
                        VariableDetails(varInfo.typeName, varInfo.value, emptyList())
                    }
                } else {
                    // 只显示前几个元素
                    getArrayElements(value, 0, MAX_ARRAY_DISPLAY).map { varInfo ->
                        VariableDetails(varInfo.typeName, varInfo.value, emptyList())
                    }
                }
            }

            is ObjectReference -> {
                // 对于常见的集合类型，尝试获取元素
                if (isCollectionType(value)) {
                    getCollectionElements(value).map { varInfo ->
                        VariableDetails(varInfo.typeName, varInfo.value, emptyList())
                    }
                } else {
                    // 普通对象，显示字段
                    getObjectFields(value).map { varInfo ->
                        VariableDetails(varInfo.typeName, varInfo.value, emptyList())
                    }
                }
            }

            else -> emptyList()
        }

        return VariableDetails(typeName, displayValue, children)
    }

    /**
     * 检查是否是集合类型
     */
    private fun isCollectionType(objectRef: ObjectReference): Boolean {
        val typeName = objectRef.referenceType().name()
        return typeName.startsWith("java.util.") &&
                (typeName.contains("List") || typeName.contains("Set") || typeName.contains("Map"))
    }

    /**
     * 获取集合元素
     */
    private fun getCollectionElements(objectRef: ObjectReference): List<VariableInfo> {
        return try {
            val refType = objectRef.referenceType()

            // 尝试调用 size() 方法
            val sizeMethod = refType.methodsByName("size").firstOrNull { it.argumentTypes().isEmpty() }
            val size = if (sizeMethod != null) {
                val thread = vm.allThreads().find { it.isSuspended } ?: return emptyList()
                val sizeValue = objectRef.invokeMethod(
                    thread,
                    sizeMethod,
                    emptyList(),
                    ObjectReference.INVOKE_SINGLE_THREADED
                ) as? IntegerValue
                sizeValue?.value() ?: 0
            } else {
                0
            }

            // 返回大小信息
            listOf(
                VariableInfo(
                    name = "size",
                    typeName = "int",
                    value = size.toString(),
                    isLocal = false
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 格式化值为显示字符串
     */
    private fun formatValue(value: Value?): String {
        return value.toDisplayString(MAX_STRING_LENGTH)
    }

    /**
     * 变量详细信息
     */
    data class VariableDetails(
        val typeName: String,
        val value: String,
        val children: List<VariableDetails>
    )

    companion object {
        private const val MAX_STRING_LENGTH = 100
        private const val MAX_ARRAY_DISPLAY = 10
    }
}
