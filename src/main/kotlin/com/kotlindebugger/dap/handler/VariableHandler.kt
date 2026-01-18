package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.kotlindebugger.dap.protocol.Scope
import com.kotlindebugger.dap.protocol.Variable
import com.kotlindebugger.dap.VariableReferenceType
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.InvalidStackFrameException
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.VMDisconnectedException
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

        // 获取当前线程
        val thread = debugSession.getCurrentThread()
            ?: throw IllegalStateException("No current thread")
        
        // 获取 JDI ThreadReference
        val jdiThread = debugSession.getVirtualMachine()
            .allThreads()
            .find { it.uniqueID() == thread.id }
            ?: throw IllegalStateException("Thread not found")

        Logger.debug("Got thread: ${jdiThread.name()}, frameId: $frameId")

        // 创建栈帧的变量引用（存储 threadId 和 frameIndex）
        val variablesReference = server.variableReferenceManager.createStackFrameReference(jdiThread, frameId)
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
        if (varRef == null) {
            // 变量引用不存在（可能已被清理），返回空列表而不是抛异常
            Logger.debug("Variable reference not found: $variablesReference, returning empty list")
            return buildJsonObject {
                put("variables", Json.encodeToJsonElement(emptyList<Variable>()))
            }
        }

        Logger.debug("Variable reference type: ${varRef.type}")

        val variables = try {
            when (varRef.type) {
                VariableReferenceType.STACK_FRAME -> {
                    // 获取栈帧的局部变量
                    Logger.debug("Getting stack frame variables")
                    val debugSession = server.getDebugSession()
                    if (debugSession == null) {
                        Logger.debug("No debug session, returning empty list")
                        emptyList()
                    } else {
                        val vm = debugSession.getVirtualMachine()
                        val stackFrame = server.variableReferenceManager.getStackFrame(varRef, vm)
                        if (stackFrame == null) {
                            Logger.debug("Stack frame is invalid or thread is running, returning empty list")
                            emptyList()
                        } else {
                            getStackFrameVariables(stackFrame)
                        }
                    }
                }
                VariableReferenceType.OBJECT_FIELDS -> {
                    // 获取对象的字段
                    Logger.debug("Getting object fields")
                    if (varRef.objectRef == null) {
                        Logger.debug("Object reference is null, returning empty list")
                        emptyList()
                    } else {
                        getObjectFields(varRef.objectRef)
                    }
                }
                VariableReferenceType.ARRAY_ELEMENTS -> {
                    // 获取数组元素
                    Logger.debug("Getting array elements")
                    val arrayRef = varRef.objectRef as? ArrayReference
                    if (arrayRef == null) {
                        Logger.debug("Array reference is null, returning empty list")
                        emptyList()
                    } else {
                        getArrayElements(arrayRef, varRef.arrayStart, varRef.arrayCount)
                    }
                }
            }
        } catch (e: InvalidStackFrameException) {
            Logger.debug("InvalidStackFrameException: ${e.message}, returning empty list")
            emptyList()
        } catch (e: ObjectCollectedException) {
            Logger.debug("ObjectCollectedException: ${e.message}, returning empty list")
            emptyList()
        } catch (e: VMDisconnectedException) {
            Logger.debug("VMDisconnectedException: ${e.message}, returning empty list")
            emptyList()
        } catch (e: Exception) {
            Logger.debug("Exception while getting variables: ${e.message}, returning empty list")
            emptyList()
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
        // 首先检查对象是否仍然有效
        try {
            // 尝试获取对象类型，如果对象已被回收会抛出异常
            val refType = objectRef.referenceType()
            val fields = refType.allFields()

            return fields.mapNotNull { field ->
                try {
                    val value = objectRef.getValue(field)
                    Variable(
                        name = field.name(),
                        value = formatValue(value),
                        type = field.typeName(),
                        variablesReference = if (value != null) createVariableReference(value) else 0
                    )
                } catch (e: ObjectCollectedException) {
                    Logger.debug("Object collected while getting field ${field.name()}")
                    Variable(
                        name = field.name(),
                        value = "<object collected>",
                        type = field.typeName(),
                        variablesReference = 0
                    )
                } catch (e: Exception) {
                    Logger.debug("Failed to get field ${field.name()}: ${e.message}")
                    Variable(
                        name = field.name(),
                        value = "<error: ${e.message}>",
                        type = field.typeName(),
                        variablesReference = 0
                    )
                }
            }
        } catch (e: ObjectCollectedException) {
            Logger.debug("Object has been garbage collected: ${e.message}")
            return emptyList()
        } catch (e: Exception) {
            Logger.debug("Failed to get object fields: ${e.message}")
            return emptyList()
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
    private fun createVariableReference(value: com.sun.jdi.Value?): Int {
        if (value == null) return 0
        
        return try {
            when (value) {
                is ObjectReference -> {
                    val typeName = value.referenceType().name()
                    
                    // String 类型不需要展开（用户通常不需要看内部实现）
                    if (typeName == "java.lang.String") {
                        return 0
                    }
                    
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
        } catch (e: Exception) {
            Logger.debug("Failed to create variable reference: ${e.message}")
            0  // 发生错误时返回0，表示不可展开
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
