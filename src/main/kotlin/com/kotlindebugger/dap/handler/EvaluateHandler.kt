package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.kotlindebugger.dap.VariableReferenceType
import com.sun.jdi.*
import kotlinx.serialization.json.*

/**
 * DAP Evaluate 请求处理器
 * 
 * 支持的 context 类型:
 * - "watch": 监视器表达式求值
 * - "repl": 调试控制台REPL求值
 * - "hover": 鼠标悬停时的表达式求值
 * - "variables": 变量视图中的表达式求值
 * 
 * 参考 IntelliJ IDEA 的实现:
 * - KotlinEvaluator: Kotlin表达式求值器
 * - VariableFinder: 变量查找器
 * - JDIEval: JDI接口实现
 */
class EvaluateHandler(private val server: DAPServer) : RequestHandler {
    override val command = "evaluate"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'evaluate' command")

        val expression = args?.get("expression")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("expression is required")

        val frameId = args["frameId"]?.jsonPrimitive?.intOrNull
        val context = args["context"]?.jsonPrimitive?.contentOrNull ?: "watch"

        Logger.debug("Expression: $expression, frameId: $frameId, context: $context")

        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        // 获取当前栈帧
        val frame = getStackFrame(debugSession, frameId)
            ?: throw IllegalStateException("No valid stack frame")

        // 创建表达式求值器
        val evaluator = ExpressionEvaluator(debugSession.getVirtualMachine(), frame, server)

        return try {
            val result = evaluator.evaluate(expression, context)
            buildJsonObject {
                put("result", result.displayValue)
                put("type", result.typeName)
                put("variablesReference", result.variablesReference)
            }
        } catch (e: EvaluationException) {
            Logger.debug("Evaluation failed: ${e.message}")
            buildJsonObject {
                put("result", "Error: ${e.message}")
                put("type", "error")
                put("variablesReference", 0)
            }
        } catch (e: Exception) {
            Logger.error("Unexpected evaluation error", e)
            buildJsonObject {
                put("result", "Error: ${e.message ?: "Unknown error"}")
                put("type", "error")
                put("variablesReference", 0)
            }
        }
    }

    /**
     * 获取指定的栈帧
     */
    private fun getStackFrame(debugSession: DebugSession, frameId: Int?): StackFrame? {
        val thread = debugSession.getCurrentThread() ?: return null
        val vm = debugSession.getVirtualMachine()
        
        val jdiThread = vm.allThreads().find { it.uniqueID() == thread.id }
            ?: return null

        if (!jdiThread.isSuspended) {
            return null
        }

        val frames = jdiThread.frames()
        val index = frameId ?: 0
        return if (index in frames.indices) frames[index] else null
    }
}

/**
 * 表达式求值异常
 */
class EvaluationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 求值结果
 */
data class EvaluationResult(
    val displayValue: String,
    val typeName: String,
    val variablesReference: Int,
    val value: Value? = null
)

/**
 * 表达式求值器
 * 
 * 参考 IntelliJ IDEA 的实现思路：
 * 1. 简单表达式：直接从当前栈帧查找变量
 * 2. 成员访问表达式：解析 a.b.c 形式的表达式
 * 3. 数组访问表达式：解析 array[index] 形式的表达式
 * 4. 方法调用表达式：解析 obj.method() 形式的表达式
 * 
 * 注意：完整的表达式求值需要编译器支持，这里实现一个简化版本，
 * 主要支持变量访问、字段访问、数组访问和简单方法调用。
 */
class ExpressionEvaluator(
    private val vm: VirtualMachine,
    private val frame: StackFrame,
    private val server: DAPServer
) {
    
    /**
     * 求值表达式
     * @param expression 要求值的表达式
     * @param context 求值上下文 (watch, repl, hover, variables)
     */
    fun evaluate(expression: String, context: String): EvaluationResult {
        Logger.debug("Evaluating expression: '$expression' in context: $context")
        
        val trimmedExpr = expression.trim()
        
        // 空表达式
        if (trimmedExpr.isEmpty()) {
            throw EvaluationException("Empty expression")
        }

        // 尝试解析和求值
        return try {
            val value = evaluateExpression(trimmedExpr)
            createResult(value, trimmedExpr)
        } catch (e: EvaluationException) {
            throw e
        } catch (e: Exception) {
            throw EvaluationException("Failed to evaluate '$trimmedExpr': ${e.message}", e)
        }
    }

    /**
     * 解析并求值表达式
     */
    private fun evaluateExpression(expression: String): Value? {
        // 1. 检查是否是字面量
        parseLiteral(expression)?.let { return it }
        
        // 2. 检查是否是简单变量
        if (isSimpleIdentifier(expression)) {
            return findVariable(expression)
        }
        
        // 3. 检查是否是数组访问 (e.g., arr[0])
        if (expression.contains('[') && expression.endsWith(']')) {
            return evaluateArrayAccess(expression)
        }
        
        // 4. 检查是否是成员访问或方法调用 (e.g., obj.field 或 obj.method())
        if (expression.contains('.')) {
            return evaluateMemberAccess(expression)
        }
        
        // 5. 检查是否是方法调用 (e.g., method())
        if (expression.contains('(') && expression.endsWith(')')) {
            return evaluateMethodCall(null, expression)
        }
        
        throw EvaluationException("Cannot evaluate expression: $expression")
    }

    /**
     * 解析字面量值
     */
    private fun parseLiteral(expression: String): Value? {
        // null 字面量
        if (expression == "null") {
            return null
        }
        
        // 布尔字面量
        if (expression == "true") {
            return vm.mirrorOf(true)
        }
        if (expression == "false") {
            return vm.mirrorOf(false)
        }
        
        // 整数字面量
        expression.toLongOrNull()?.let { return vm.mirrorOf(it.toInt()) }
        
        // 长整型字面量 (以L结尾)
        if (expression.endsWith("L") || expression.endsWith("l")) {
            expression.dropLast(1).toLongOrNull()?.let { return vm.mirrorOf(it) }
        }
        
        // 浮点数字面量 (以F结尾)
        if (expression.endsWith("F") || expression.endsWith("f")) {
            expression.dropLast(1).toFloatOrNull()?.let { return vm.mirrorOf(it) }
        }
        
        // Double 字面量
        expression.toDoubleOrNull()?.let { return vm.mirrorOf(it) }
        
        // 字符串字面量 (用引号包围)
        if ((expression.startsWith("\"") && expression.endsWith("\"")) ||
            (expression.startsWith("'") && expression.endsWith("'"))) {
            val content = expression.substring(1, expression.length - 1)
            return vm.mirrorOf(content)
        }
        
        return null
    }

    /**
     * 检查是否是简单标识符
     */
    private fun isSimpleIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name[0].isLetter() && name[0] != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' }
    }

    /**
     * 查找变量
     */
    private fun findVariable(name: String): Value? {
        // 1. 检查 this
        if (name == "this") {
            return try {
                frame.thisObject()
            } catch (e: Exception) {
                throw EvaluationException("'this' is not available in static context")
            }
        }
        
        // 2. 查找局部变量
        try {
            val localVars = frame.visibleVariables()
            val variable = localVars.find { it.name() == name }
            if (variable != null) {
                return frame.getValue(variable)
            }
        } catch (e: AbsentInformationException) {
            Logger.debug("No debug information available for local variables")
        }
        
        // 3. 查找实例字段 (this.field)
        try {
            val thisObj = frame.thisObject()
            if (thisObj != null) {
                val refType = thisObj.referenceType()
                val field = refType.fieldByName(name)
                if (field != null) {
                    return thisObj.getValue(field)
                }
            }
        } catch (e: Exception) {
            // 静态上下文，没有 this
        }
        
        // 4. 查找静态字段
        try {
            val refType = frame.location().declaringType()
            val field = refType.fieldByName(name)
            if (field != null && field.isStatic) {
                return refType.getValue(field)
            }
        } catch (e: Exception) {
            Logger.debug("Failed to find static field: $name")
        }
        
        throw EvaluationException("Cannot find variable: $name")
    }

    /**
     * 求值数组访问表达式
     */
    private fun evaluateArrayAccess(expression: String): Value? {
        val bracketIndex = expression.indexOf('[')
        val arrayExpr = expression.substring(0, bracketIndex)
        val indexExpr = expression.substring(bracketIndex + 1, expression.length - 1)
        
        // 求值数组对象
        val arrayValue = evaluateExpression(arrayExpr)
        if (arrayValue !is ArrayReference) {
            throw EvaluationException("'$arrayExpr' is not an array")
        }
        
        // 求值索引
        val indexValue = evaluateExpression(indexExpr)
        val index = when (indexValue) {
            is IntegerValue -> indexValue.value()
            is LongValue -> indexValue.value().toInt()
            is ShortValue -> indexValue.value().toInt()
            is ByteValue -> indexValue.value().toInt()
            else -> throw EvaluationException("Array index must be an integer")
        }
        
        // 检查边界
        if (index < 0 || index >= arrayValue.length()) {
            throw EvaluationException("Array index out of bounds: $index (length: ${arrayValue.length()})")
        }
        
        return arrayValue.getValue(index)
    }

    /**
     * 求值成员访问表达式 (obj.field 或 obj.method())
     */
    private fun evaluateMemberAccess(expression: String): Value? {
        val parts = splitMemberAccess(expression)
        if (parts.size < 2) {
            throw EvaluationException("Invalid member access expression: $expression")
        }
        
        var currentValue: Value? = evaluateExpression(parts[0])
        
        for (i in 1 until parts.size) {
            val member = parts[i]
            
            if (currentValue == null) {
                throw EvaluationException("Cannot access member '$member' on null")
            }
            
            currentValue = if (member.contains('(') && member.endsWith(')')) {
                // 方法调用
                evaluateMethodCall(currentValue, member)
            } else if (member.contains('[') && member.endsWith(']')) {
                // 数组访问
                val bracketIndex = member.indexOf('[')
                val fieldName = member.substring(0, bracketIndex)
                val indexExpr = member.substring(bracketIndex + 1, member.length - 1)
                
                val fieldValue = if (fieldName.isEmpty()) {
                    currentValue
                } else {
                    getFieldValue(currentValue, fieldName)
                }
                
                if (fieldValue !is ArrayReference) {
                    throw EvaluationException("'$fieldName' is not an array")
                }
                
                val indexValue = evaluateExpression(indexExpr)
                val index = when (indexValue) {
                    is IntegerValue -> indexValue.value()
                    else -> throw EvaluationException("Array index must be an integer")
                }
                
                fieldValue.getValue(index)
            } else {
                // 字段访问
                getFieldValue(currentValue, member)
            }
        }
        
        return currentValue
    }

    /**
     * 分割成员访问表达式
     */
    private fun splitMemberAccess(expression: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0  // 括号深度
        var inBracket = 0  // 方括号深度
        
        for (char in expression) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }
                ')' -> {
                    depth--
                    current.append(char)
                }
                '[' -> {
                    inBracket++
                    current.append(char)
                }
                ']' -> {
                    inBracket--
                    current.append(char)
                }
                '.' -> {
                    if (depth == 0 && inBracket == 0) {
                        if (current.isNotEmpty()) {
                            parts.add(current.toString())
                            current.clear()
                        }
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        
        return parts
    }

    /**
     * 获取字段值
     */
    private fun getFieldValue(value: Value?, fieldName: String): Value? {
        if (value == null) {
            throw EvaluationException("Cannot access field '$fieldName' on null")
        }
        
        if (value !is ObjectReference) {
            throw EvaluationException("Cannot access field '$fieldName' on primitive type")
        }
        
        val refType = value.referenceType()
        val field = refType.fieldByName(fieldName)
            ?: throw EvaluationException("Field '$fieldName' not found in ${refType.name()}")
        
        return value.getValue(field)
    }

    /**
     * 求值方法调用
     */
    private fun evaluateMethodCall(receiver: Value?, methodExpr: String): Value? {
        val parenIndex = methodExpr.indexOf('(')
        val methodName = methodExpr.substring(0, parenIndex)
        val argsString = methodExpr.substring(parenIndex + 1, methodExpr.length - 1).trim()
        
        // 解析参数
        val args = if (argsString.isEmpty()) {
            emptyList()
        } else {
            parseArguments(argsString)
        }
        
        // 求值参数
        val argValues = args.map { evaluateExpression(it) }
        
        return when {
            receiver != null && receiver is ObjectReference -> {
                invokeInstanceMethod(receiver, methodName, argValues)
            }
            receiver == null -> {
                // 尝试在当前类上调用静态方法或实例方法
                invokeLocalMethod(methodName, argValues)
            }
            else -> {
                throw EvaluationException("Cannot invoke method on primitive type")
            }
        }
    }

    /**
     * 解析方法参数
     */
    private fun parseArguments(argsString: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var stringChar = ' '
        
        for (char in argsString) {
            when {
                (char == '"' || char == '\'') && !inString -> {
                    inString = true
                    stringChar = char
                    current.append(char)
                }
                char == stringChar && inString -> {
                    inString = false
                    current.append(char)
                }
                inString -> {
                    current.append(char)
                }
                char == '(' || char == '[' -> {
                    depth++
                    current.append(char)
                }
                char == ')' || char == ']' -> {
                    depth--
                    current.append(char)
                }
                char == ',' && depth == 0 -> {
                    args.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            args.add(current.toString().trim())
        }
        
        return args
    }

    /**
     * 调用实例方法
     */
    private fun invokeInstanceMethod(obj: ObjectReference, methodName: String, args: List<Value?>): Value? {
        val refType = obj.referenceType()
        
        // 查找匹配的方法
        val methods = refType.methodsByName(methodName)
        if (methods.isEmpty()) {
            throw EvaluationException("Method '$methodName' not found in ${refType.name()}")
        }
        
        // 选择参数数量匹配的方法
        val method = methods.find { it.argumentTypes().size == args.size }
            ?: throw EvaluationException("No matching method '$methodName' with ${args.size} arguments")
        
        // 获取挂起的线程
        val thread = vm.allThreads().find { it.isSuspended }
            ?: throw EvaluationException("No suspended thread available for method invocation")
        
        return try {
            obj.invokeMethod(thread, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
        } catch (e: InvocationException) {
            val exception = e.exception()
            throw EvaluationException("Method invocation threw: ${exception.referenceType().name()}")
        } catch (e: Exception) {
            throw EvaluationException("Failed to invoke method '$methodName': ${e.message}")
        }
    }

    /**
     * 调用当前上下文中的方法
     */
    private fun invokeLocalMethod(methodName: String, args: List<Value?>): Value? {
        // 尝试在 this 上调用
        try {
            val thisObj = frame.thisObject()
            if (thisObj != null) {
                return invokeInstanceMethod(thisObj, methodName, args)
            }
        } catch (e: EvaluationException) {
            // 继续尝试静态方法
        } catch (e: Exception) {
            // 没有 this
        }
        
        // 尝试调用静态方法
        val refType = frame.location().declaringType()
        val methods = refType.methodsByName(methodName).filter { it.isStatic }
        
        if (methods.isEmpty()) {
            throw EvaluationException("Cannot find method '$methodName'")
        }
        
        val method = methods.find { it.argumentTypes().size == args.size }
            ?: throw EvaluationException("No matching static method '$methodName' with ${args.size} arguments")
        
        val thread = vm.allThreads().find { it.isSuspended }
            ?: throw EvaluationException("No suspended thread available")
        
        return try {
            if (refType is ClassType) {
                refType.invokeMethod(thread, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
            } else {
                throw EvaluationException("Cannot invoke static method on non-class type")
            }
        } catch (e: InvocationException) {
            throw EvaluationException("Static method invocation threw: ${e.exception().referenceType().name()}")
        }
    }

    /**
     * 创建求值结果
     */
    private fun createResult(value: Value?, expression: String): EvaluationResult {
        val displayValue = formatValue(value)
        val typeName = value?.type()?.name() ?: "null"
        val variablesReference = createVariableReference(value)
        
        return EvaluationResult(
            displayValue = displayValue,
            typeName = typeName,
            variablesReference = variablesReference,
            value = value
        )
    }

    /**
     * 格式化值为显示字符串
     */
    private fun formatValue(value: Value?): String {
        if (value == null) return "null"
        
        return when (value) {
            is StringReference -> "\"${value.value()}\""
            is BooleanValue -> value.value().toString()
            is CharValue -> "'${value.value()}'"
            is PrimitiveValue -> value.toString()
            is ArrayReference -> {
                val length = value.length()
                val typeName = value.type().name().substringBefore('[')
                "$typeName[$length]"
            }
            is ObjectReference -> {
                val typeName = value.referenceType().name()
                // 尝试调用 toString() 方法获取更好的显示
                try {
                    val toStringMethod = value.referenceType().methodsByName("toString")
                        .find { it.argumentTypes().isEmpty() }
                    if (toStringMethod != null) {
                        val thread = vm.allThreads().find { it.isSuspended }
                        if (thread != null) {
                            val result = value.invokeMethod(
                                thread,
                                toStringMethod,
                                emptyList(),
                                ObjectReference.INVOKE_SINGLE_THREADED
                            )
                            if (result is StringReference) {
                                return result.value()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // toString 调用失败，使用默认格式
                }
                "${typeName.substringAfterLast('.')}@${value.uniqueID()}"
            }
            else -> value.toString()
        }
    }

    /**
     * 为值创建变量引用（用于展开对象/数组）
     */
    private fun createVariableReference(value: Value?): Int {
        if (value == null) return 0
        
        return when (value) {
            is ArrayReference -> {
                if (value.length() > 0) {
                    server.variableReferenceManager.createArrayElementsReference(value, 0, value.length())
                } else {
                    0
                }
            }
            is ObjectReference -> {
                // 排除基本包装类型和 String
                val typeName = value.referenceType().name()
                if (typeName in SIMPLE_TYPES) {
                    0
                } else {
                    server.variableReferenceManager.createObjectFieldsReference(value)
                }
            }
            else -> 0
        }
    }

    companion object {
        private val SIMPLE_TYPES = setOf(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.lang.Character"
        )
    }
}
