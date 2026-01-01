package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.kotlindebugger.dap.protocol.Scope
import com.kotlindebugger.dap.protocol.Variable
import com.kotlindebugger.dap.VariableReferenceType
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import kotlinx.serialization.json.*

class ScopesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "scopes"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'scopes' command")

        val frameId = args?.get("frameId")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("frameId is required")

        Logger.debug("Frame ID: $frameId")

        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        debugSession.selectFrame(frameId)

        // 获取实际的StackFrame对象
        val thread = debugSession.getCurrentThread()
            ?: throw IllegalStateException("No current thread")
        val stackFrame = debugSession.getVirtualMachine()
            .allThreads()
            .find { it.uniqueID() == thread.id }
            ?.frame(frameId)
            ?: throw IllegalStateException("Frame not found")

        Logger.debug("Got stack frame: ${stackFrame.location()}")

        // 创建栈帧的变量引用
        val variablesReference = server.variableReferenceManager.createStackFrameReference(stackFrame)
        Logger.debug("Created variables reference: $variablesReference")

        val scopes = listOf(
            Scope(
                name = "Locals",
                variablesReference = variablesReference,
                expensive = false
            )
        )

        return buildJsonObject {
            put("scopes", Json.encodeToJsonElement(scopes))
        }
    }
}

class VariablesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "variables"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'variables' command")

        val variablesReference = args?.get("variablesReference")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("variablesReference is required")

        Logger.debug("Variables reference: $variablesReference")

        // 获取变量引用
        val varRef = server.variableReferenceManager.getReference(variablesReference)
            ?: throw IllegalStateException("Invalid variablesReference: $variablesReference")

        Logger.debug("Variable reference type: ${varRef.type}")

        val variables = when (varRef.type) {
            VariableReferenceType.STACK_FRAME -> {
                // 获取栈帧的局部变量
                Logger.debug("Getting stack frame variables")
                getStackFrameVariables(varRef.stackFrame!!)
            }
            VariableReferenceType.OBJECT_FIELDS -> {
                // 获取对象的字段
                Logger.debug("Getting object fields")
                getObjectFields(varRef.objectRef!!)
            }
            VariableReferenceType.ARRAY_ELEMENTS -> {
                // 获取数组元素
                Logger.debug("Getting array elements")
                getArrayElements(varRef.objectRef as ArrayReference, varRef.arrayStart, varRef.arrayCount)
            }
        }

        Logger.debug("Returning ${variables.size} variables")
        return buildJsonObject {
            put("variables", Json.encodeToJsonElement(variables))
        }
    }

    /**
     * 获取栈帧的局部变量
     */
    private fun getStackFrameVariables(frame: com.sun.jdi.StackFrame): List<Variable> {
        val variables = mutableListOf<Variable>()

        // 获取 this 对象(如果有)
        try {
            val thisObject = frame.thisObject()
            if (thisObject != null) {
                variables.add(
                    Variable(
                        name = "this",
                        value = formatValue(thisObject),
                        type = thisObject.referenceType().name(),
                        variablesReference = server.variableReferenceManager.createObjectFieldsReference(thisObject)
                    )
                )
            }
        } catch (e: Exception) {
            // 静态方法没有 this
        }

        // 获取局部变量
        val localVars = frame.visibleVariables()
        for (variable in localVars) {
            val value = frame.getValue(variable)
            variables.add(
                Variable(
                    name = variable.name(),
                    value = formatValue(value),
                    type = variable.typeName(),
                    variablesReference = createVariableReference(value)
                )
            )
        }

        return variables
    }

    /**
     * 获取对象的字段
     */
    private fun getObjectFields(objectRef: ObjectReference): List<Variable> {
        val refType = objectRef.referenceType()
        val fields = refType.allFields()

        return fields.map { field ->
            val value = objectRef.getValue(field)
            Variable(
                name = field.name(),
                value = formatValue(value),
                type = field.typeName(),
                variablesReference = createVariableReference(value)
            )
        }
    }

    /**
     * 获取数组元素
     */
    private fun getArrayElements(arrayRef: ArrayReference, start: Int, count: Int): List<Variable> {
        val length = arrayRef.length()
        val actualCount = if (count < 0) length - start else minOf(count, length - start)

        if (start >= length || actualCount <= 0) {
            return emptyList()
        }

        val values = arrayRef.getValues(start, actualCount)
        return values.mapIndexed { index, value ->
            Variable(
                name = "[${start + index}]",
                value = formatValue(value),
                type = arrayRef.type().name(),
                variablesReference = 0  // 数组元素不再展开
            )
        }
    }

    /**
     * 为变量创建引用ID(如果可以展开)
     */
    private fun createVariableReference(value: com.sun.jdi.Value): Int {
        return when (value) {
            is ObjectReference -> {
                if (value is ArrayReference) {
                    // 数组可以展开
                    server.variableReferenceManager.createArrayElementsReference(value, 0, -1)
                } else if (value.referenceType().allFields().isNotEmpty()) {
                    // 有字段的对象可以展开
                    server.variableReferenceManager.createObjectFieldsReference(value)
                } else {
                    0  // 空对象,不可展开
                }
            }
            else -> 0  // 基本类型,不可展开
        }
    }

    /**
     * 格式化值为显示字符串
     */
    private fun formatValue(value: com.sun.jdi.Value?): String {
        if (value == null) return "null"

        return when (value) {
            is com.sun.jdi.ArrayReference -> {
                val length = value.length()
                "Array[$length]"
            }
            is ObjectReference -> {
                val typeName = value.type().name()
                when {
                    typeName == "java.lang.String" -> {
                        try {
                            "\"" + (value as com.sun.jdi.StringReference).value() + "\""
                        } catch (e: Exception) {
                            "\"${typeName}@${Integer.toHexString(value.hashCode())}\""
                        }
                    }
                    else -> {
                        try {
                            value.toString()
                        } catch (e: Exception) {
                            "instance of $typeName"
                        }
                    }
                }
            }
            else -> value.toString()
        }
    }
}
