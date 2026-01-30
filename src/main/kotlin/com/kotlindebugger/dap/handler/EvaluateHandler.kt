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
 * - KotlinEvaluator: Kotlin表达式求值器
 * - VariableFinder: 变量查找器
 * - JDIEval: JDI接口实现
 * 
 * 支持完整的表达式求值:
 * - 字面量: 数字、字符串、布尔值、null
 * - 变量访问: identifier, this
 * - 成员访问: obj.field, obj?.field
 * - 数组访问: array[index]
 * - 方法调用: obj.method(args)
 * - 算术运算: +, -, *, /, %
 * - 比较运算: ==, !=, <, >, <=, >=
 * - 逻辑运算: &&, ||, !
 * - 位运算: and, or, xor, inv, shl, shr, ushr
 * - 类型检查: is, !is
 * - 类型转换: as, as?
 * - 条件表达式: if-else
 * - Elvis运算符: ?:
 * - 对象创建: ClassName(args)
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
            // 使用新的表达式解析器
            val ast = parseExpression(trimmedExpr)
            val value = evaluateNode(ast)
            createResult(value, trimmedExpr)
        } catch (e: EvaluationException) {
            throw e
        } catch (e: Exception) {
            throw EvaluationException("Failed to evaluate '$trimmedExpr': ${e.message}", e)
        }
    }

    /**
     * 求值AST节点
     */
    private fun evaluateNode(node: ExprNode): Value? {
        return when (node) {
            is LiteralNode -> evaluateLiteral(node)
            is IdentifierNode -> findVariable(node.name)
            is ThisNode -> evaluateThis()
            is UnaryNode -> evaluateUnary(node)
            is BinaryNode -> evaluateBinary(node)
            is MemberAccessNode -> evaluateMemberAccess(node)
            is ArrayAccessNode -> evaluateArrayAccess(node)
            is MethodCallNode -> evaluateMethodCall(node)
            is TypeCheckNode -> evaluateTypeCheck(node)
            is TypeCastNode -> evaluateTypeCast(node)
            is ConditionalNode -> evaluateConditional(node)
            is NewObjectNode -> evaluateNewObject(node)
            is ElvisNode -> evaluateElvis(node)
        }
    }

    /**
     * 求值字面量节点
     */
    private fun evaluateLiteral(node: LiteralNode): Value? {
        return when (node.type) {
            TokenType.NULL -> null
            TokenType.BOOLEAN -> vm.mirrorOf(node.value as Boolean)
            TokenType.INTEGER -> vm.mirrorOf(node.value as Int)
            TokenType.LONG -> vm.mirrorOf(node.value as Long)
            TokenType.FLOAT -> vm.mirrorOf(node.value as Float)
            TokenType.DOUBLE -> vm.mirrorOf(node.value as Double)
            TokenType.STRING -> vm.mirrorOf(node.value as String)
            TokenType.CHAR -> vm.mirrorOf(node.value as Char)
            else -> throw EvaluationException("Unknown literal type: ${node.type}")
        }
    }

    /**
     * 求值this引用
     */
    private fun evaluateThis(): Value? {
        return try {
            frame.thisObject()
        } catch (e: Exception) {
            throw EvaluationException("'this' is not available in static context")
        }
    }

    /**
     * 求值一元运算
     */
    private fun evaluateUnary(node: UnaryNode): Value? {
        val operand = evaluateNode(node.operand)
        
        return when (node.operator) {
            TokenType.MINUS -> evaluateNegate(operand)
            TokenType.PLUS -> operand // Unary plus is a no-op
            TokenType.NOT -> evaluateLogicalNot(operand)
            TokenType.BIT_NOT -> evaluateBitwiseNot(operand)
            else -> throw EvaluationException("Unknown unary operator: ${node.operator}")
        }
    }

    /**
     * 求值取反运算
     */
    private fun evaluateNegate(value: Value?): Value? {
        if (value == null) throw EvaluationException("Cannot negate null")
        
        return when (value) {
            is IntegerValue -> vm.mirrorOf(-value.value())
            is LongValue -> vm.mirrorOf(-value.value())
            is FloatValue -> vm.mirrorOf(-value.value())
            is DoubleValue -> vm.mirrorOf(-value.value())
            is ShortValue -> vm.mirrorOf(-value.value())
            is ByteValue -> vm.mirrorOf(-value.value())
            else -> throw EvaluationException("Cannot negate non-numeric value: ${value.type().name()}")
        }
    }

    /**
     * 求值逻辑非运算
     */
    private fun evaluateLogicalNot(value: Value?): Value {
        return vm.mirrorOf(!toBooleanValue(value))
    }

    /**
     * 求值位非运算
     */
    private fun evaluateBitwiseNot(value: Value?): Value? {
        if (value == null) throw EvaluationException("Cannot apply bitwise NOT to null")
        
        return when (value) {
            is IntegerValue -> vm.mirrorOf(value.value().inv())
            is LongValue -> vm.mirrorOf(value.value().inv())
            else -> throw EvaluationException("Cannot apply bitwise NOT to non-integer value: ${value.type().name()}")
        }
    }

    /**
     * 求值二元运算
     */
    private fun evaluateBinary(node: BinaryNode): Value? {
        // 短路求值逻辑运算
        if (node.operator == TokenType.AND) {
            val left = evaluateNode(node.left)
            if (!toBooleanValue(left)) {
                return vm.mirrorOf(false)
            }
            val right = evaluateNode(node.right)
            return vm.mirrorOf(toBooleanValue(right))
        }
        
        if (node.operator == TokenType.OR) {
            val left = evaluateNode(node.left)
            if (toBooleanValue(left)) {
                return vm.mirrorOf(true)
            }
            val right = evaluateNode(node.right)
            return vm.mirrorOf(toBooleanValue(right))
        }
        
        val left = evaluateNode(node.left)
        val right = evaluateNode(node.right)
        
        return when (node.operator) {
            // 算术运算
            TokenType.PLUS -> evaluateAdd(left, right)
            TokenType.MINUS -> evaluateSubtract(left, right)
            TokenType.STAR -> evaluateMultiply(left, right)
            TokenType.SLASH -> evaluateDivide(left, right)
            TokenType.PERCENT -> evaluateModulo(left, right)
            
            // 比较运算
            TokenType.EQ -> vm.mirrorOf(compareValues(left, right))
            TokenType.NE -> vm.mirrorOf(!compareValues(left, right))
            TokenType.LT -> vm.mirrorOf(compareNumeric(left, right) < 0)
            TokenType.GT -> vm.mirrorOf(compareNumeric(left, right) > 0)
            TokenType.LE -> vm.mirrorOf(compareNumeric(left, right) <= 0)
            TokenType.GE -> vm.mirrorOf(compareNumeric(left, right) >= 0)
            
            // 位运算
            TokenType.BIT_AND -> evaluateBitwiseAnd(left, right)
            TokenType.BIT_OR -> evaluateBitwiseOr(left, right)
            TokenType.BIT_XOR -> evaluateBitwiseXor(left, right)
            TokenType.SHL -> evaluateShiftLeft(left, right)
            TokenType.SHR -> evaluateShiftRight(left, right)
            TokenType.USHR -> evaluateUnsignedShiftRight(left, right)
            
            else -> throw EvaluationException("Unknown binary operator: ${node.operator}")
        }
    }

    /**
     * 求值加法运算
     */
    private fun evaluateAdd(left: Value?, right: Value?): Value? {
        // 字符串连接
        if (left is StringReference || right is StringReference) {
            val leftStr = valueToString(left)
            val rightStr = valueToString(right)
            return vm.mirrorOf(leftStr + rightStr)
        }
        
        if (left == null || right == null) {
            throw EvaluationException("Cannot add null values")
        }
        
        return when {
            left is DoubleValue || right is DoubleValue -> 
                vm.mirrorOf(toDouble(left) + toDouble(right))
            left is FloatValue || right is FloatValue -> 
                vm.mirrorOf(toFloat(left) + toFloat(right))
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) + toLong(right))
            else -> vm.mirrorOf(toInt(left) + toInt(right))
        }
    }

    /**
     * 求值减法运算
     */
    private fun evaluateSubtract(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot subtract null values")
        }
        
        return when {
            left is DoubleValue || right is DoubleValue -> 
                vm.mirrorOf(toDouble(left) - toDouble(right))
            left is FloatValue || right is FloatValue -> 
                vm.mirrorOf(toFloat(left) - toFloat(right))
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) - toLong(right))
            else -> vm.mirrorOf(toInt(left) - toInt(right))
        }
    }

    /**
     * 求值乘法运算
     */
    private fun evaluateMultiply(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot multiply null values")
        }
        
        return when {
            left is DoubleValue || right is DoubleValue -> 
                vm.mirrorOf(toDouble(left) * toDouble(right))
            left is FloatValue || right is FloatValue -> 
                vm.mirrorOf(toFloat(left) * toFloat(right))
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) * toLong(right))
            else -> vm.mirrorOf(toInt(left) * toInt(right))
        }
    }

    /**
     * 求值除法运算
     */
    private fun evaluateDivide(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot divide null values")
        }
        
        // 检查除零
        val rightNum = toDouble(right)
        if (rightNum == 0.0 && right !is DoubleValue && right !is FloatValue) {
            throw EvaluationException("Division by zero")
        }
        
        return when {
            left is DoubleValue || right is DoubleValue -> 
                vm.mirrorOf(toDouble(left) / toDouble(right))
            left is FloatValue || right is FloatValue -> 
                vm.mirrorOf(toFloat(left) / toFloat(right))
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) / toLong(right))
            else -> vm.mirrorOf(toInt(left) / toInt(right))
        }
    }

    /**
     * 求值取模运算
     */
    private fun evaluateModulo(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot modulo null values")
        }
        
        return when {
            left is DoubleValue || right is DoubleValue -> 
                vm.mirrorOf(toDouble(left) % toDouble(right))
            left is FloatValue || right is FloatValue -> 
                vm.mirrorOf(toFloat(left) % toFloat(right))
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) % toLong(right))
            else -> vm.mirrorOf(toInt(left) % toInt(right))
        }
    }

    /**
     * 求值位与运算
     */
    private fun evaluateBitwiseAnd(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot apply bitwise AND to null")
        }
        
        return when {
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) and toLong(right))
            else -> vm.mirrorOf(toInt(left) and toInt(right))
        }
    }

    /**
     * 求值位或运算
     */
    private fun evaluateBitwiseOr(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot apply bitwise OR to null")
        }
        
        return when {
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) or toLong(right))
            else -> vm.mirrorOf(toInt(left) or toInt(right))
        }
    }

    /**
     * 求值位异或运算
     */
    private fun evaluateBitwiseXor(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot apply bitwise XOR to null")
        }
        
        return when {
            left is LongValue || right is LongValue -> 
                vm.mirrorOf(toLong(left) xor toLong(right))
            else -> vm.mirrorOf(toInt(left) xor toInt(right))
        }
    }

    /**
     * 求值左移运算
     */
    private fun evaluateShiftLeft(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot apply shift to null")
        }
        
        val shiftAmount = toInt(right)
        return when (left) {
            is LongValue -> vm.mirrorOf(left.value() shl shiftAmount)
            else -> vm.mirrorOf(toInt(left) shl shiftAmount)
        }
    }

    /**
     * 求值右移运算
     */
    private fun evaluateShiftRight(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot apply shift to null")
        }
        
        val shiftAmount = toInt(right)
        return when (left) {
            is LongValue -> vm.mirrorOf(left.value() shr shiftAmount)
            else -> vm.mirrorOf(toInt(left) shr shiftAmount)
        }
    }

    /**
     * 求值无符号右移运算
     */
    private fun evaluateUnsignedShiftRight(left: Value?, right: Value?): Value? {
        if (left == null || right == null) {
            throw EvaluationException("Cannot apply shift to null")
        }
        
        val shiftAmount = toInt(right)
        return when (left) {
            is LongValue -> vm.mirrorOf(left.value() ushr shiftAmount)
            else -> vm.mirrorOf(toInt(left) ushr shiftAmount)
        }
    }

    /**
     * 求值成员访问节点
     */
    private fun evaluateMemberAccess(node: MemberAccessNode): Value? {
        val obj = evaluateNode(node.obj)
        
        if (obj == null) {
            if (node.safe) {
                return null
            }
            throw EvaluationException("Cannot access member '${node.member}' on null")
        }
        
        return getFieldValue(obj, node.member)
    }

    /**
     * 求值数组访问节点
     */
    private fun evaluateArrayAccess(node: ArrayAccessNode): Value? {
        val array = evaluateNode(node.array)
        val index = evaluateNode(node.index)
        
        if (array !is ArrayReference) {
            throw EvaluationException("Value is not an array")
        }
        
        val idx = toInt(index ?: throw EvaluationException("Array index cannot be null"))
        
        if (idx < 0 || idx >= array.length()) {
            throw EvaluationException("Array index out of bounds: $idx (length: ${array.length()})")
        }
        
        return array.getValue(idx)
    }

    /**
     * 求值方法调用节点
     */
    private fun evaluateMethodCall(node: MethodCallNode): Value? {
        val receiver = if (node.receiver != null) {
            evaluateNode(node.receiver)
        } else {
            null
        }
        
        // Handle safe call: if receiver is null and this is a safe call, return null
        if (receiver == null && node.receiver != null && node.safe) {
            return null
        }
        
        val args = node.arguments.map { evaluateNode(it) }
        
        return when {
            receiver != null && receiver is ObjectReference -> {
                invokeInstanceMethod(receiver, node.methodName, args)
            }
            receiver == null -> {
                invokeLocalMethod(node.methodName, args)
            }
            else -> {
                throw EvaluationException("Cannot invoke method on primitive type")
            }
        }
    }

    /**
     * 求值类型检查节点
     */
    private fun evaluateTypeCheck(node: TypeCheckNode): Value {
        val value = evaluateNode(node.expr)
        val result = isInstanceOf(value, node.typeName)
        return vm.mirrorOf(if (node.negated) !result else result)
    }

    /**
     * 检查值是否是指定类型的实例
     */
    private fun isInstanceOf(value: Value?, typeName: String): Boolean {
        if (value == null) return false
        
        if (value !is ObjectReference) {
            // 基本类型的类型检查
            val valueTypeName = value.type().name()
            return valueTypeName == typeName || 
                   matchesPrimitiveType(valueTypeName, typeName)
        }
        
        val refType = value.referenceType()
        return isAssignableTo(refType, typeName)
    }

    /**
     * 检查基本类型匹配
     */
    private fun matchesPrimitiveType(valueType: String, expectedType: String): Boolean {
        val primitiveToBoxed = mapOf(
            "int" to "java.lang.Integer",
            "long" to "java.lang.Long",
            "short" to "java.lang.Short",
            "byte" to "java.lang.Byte",
            "float" to "java.lang.Float",
            "double" to "java.lang.Double",
            "boolean" to "java.lang.Boolean",
            "char" to "java.lang.Character"
        )
        return primitiveToBoxed[valueType] == expectedType ||
               primitiveToBoxed.entries.find { it.value == valueType }?.key == expectedType
    }

    /**
     * 检查类型是否可赋值给目标类型
     */
    private fun isAssignableTo(refType: ReferenceType, targetTypeName: String): Boolean {
        if (refType.name() == targetTypeName) return true
        
        // 检查超类
        if (refType is ClassType) {
            var superClass = refType.superclass()
            while (superClass != null) {
                if (superClass.name() == targetTypeName) return true
                superClass = superClass.superclass()
            }
            
            // 检查接口
            for (iface in refType.allInterfaces()) {
                if (iface.name() == targetTypeName) return true
            }
        }
        
        return false
    }

    /**
     * 求值类型转换节点
     */
    private fun evaluateTypeCast(node: TypeCastNode): Value? {
        val value = evaluateNode(node.expr)
        
        if (value == null) {
            if (node.safe) return null
            throw EvaluationException("Cannot cast null to ${node.typeName}")
        }
        
        if (!isInstanceOf(value, node.typeName)) {
            if (node.safe) return null
            throw EvaluationException("Cannot cast ${value.type().name()} to ${node.typeName}")
        }
        
        return value
    }

    /**
     * 求值条件表达式节点
     */
    private fun evaluateConditional(node: ConditionalNode): Value? {
        val condition = evaluateNode(node.condition)
        return if (toBooleanValue(condition)) {
            evaluateNode(node.thenExpr)
        } else {
            evaluateNode(node.elseExpr)
        }
    }

    /**
     * 求值对象创建节点
     */
    private fun evaluateNewObject(node: NewObjectNode): Value? {
        val className = resolveClassName(node.typeName)
        val classType = findClassType(className)
            ?: throw EvaluationException("Class not found: ${node.typeName}")
        
        val args = node.arguments.map { evaluateNode(it) }
        
        return invokeConstructor(classType, args)
    }

    /**
     * 求值Elvis运算符节点
     */
    private fun evaluateElvis(node: ElvisNode): Value? {
        val left = evaluateNode(node.left)
        return left ?: evaluateNode(node.right)
    }

    /**
     * 解析类名
     */
    private fun resolveClassName(typeName: String): String {
        // 处理简单类名到全限定名的映射
        val commonTypes = mapOf(
            "String" to "java.lang.String",
            "Integer" to "java.lang.Integer",
            "Long" to "java.lang.Long",
            "Double" to "java.lang.Double",
            "Float" to "java.lang.Float",
            "Boolean" to "java.lang.Boolean",
            "List" to "java.util.ArrayList",
            "ArrayList" to "java.util.ArrayList",
            "Map" to "java.util.HashMap",
            "HashMap" to "java.util.HashMap",
            "Set" to "java.util.HashSet",
            "HashSet" to "java.util.HashSet"
        )
        
        return commonTypes[typeName] ?: typeName
    }

    /**
     * 查找类类型
     */
    private fun findClassType(className: String): ClassType? {
        return vm.classesByName(className).filterIsInstance<ClassType>().firstOrNull()
    }

    /**
     * 调用构造函数
     */
    private fun invokeConstructor(classType: ClassType, args: List<Value?>): Value? {
        val constructors = classType.methodsByName("<init>")
        val constructor = constructors.find { it.argumentTypes().size == args.size }
            ?: throw EvaluationException("No matching constructor with ${args.size} arguments for ${classType.name()}")
        
        val thread = vm.allThreads().find { it.isSuspended }
            ?: throw EvaluationException("No suspended thread available for constructor invocation")
        
        return try {
            classType.newInstance(thread, constructor, args, ObjectReference.INVOKE_SINGLE_THREADED)
        } catch (e: InvocationException) {
            throw EvaluationException("Constructor threw: ${e.exception().referenceType().name()}")
        } catch (e: Exception) {
            throw EvaluationException("Failed to create new instance: ${e.message}")
        }
    }

    // Type conversion helper methods

    private fun toInt(value: Value): Int = when (value) {
        is IntegerValue -> value.value()
        is LongValue -> value.value().toInt()
        is ShortValue -> value.value().toInt()
        is ByteValue -> value.value().toInt()
        is CharValue -> value.value().code
        is FloatValue -> value.value().toInt()
        is DoubleValue -> value.value().toInt()
        else -> throw EvaluationException("Cannot convert to int: ${value.type().name()}")
    }

    private fun toLong(value: Value): Long = when (value) {
        is LongValue -> value.value()
        is IntegerValue -> value.value().toLong()
        is ShortValue -> value.value().toLong()
        is ByteValue -> value.value().toLong()
        is CharValue -> value.value().code.toLong()
        is FloatValue -> value.value().toLong()
        is DoubleValue -> value.value().toLong()
        else -> throw EvaluationException("Cannot convert to long: ${value.type().name()}")
    }

    private fun toFloat(value: Value): Float = when (value) {
        is FloatValue -> value.value()
        is DoubleValue -> value.value().toFloat()
        is IntegerValue -> value.value().toFloat()
        is LongValue -> value.value().toFloat()
        is ShortValue -> value.value().toFloat()
        is ByteValue -> value.value().toFloat()
        else -> throw EvaluationException("Cannot convert to float: ${value.type().name()}")
    }

    private fun toDouble(value: Value): Double = when (value) {
        is DoubleValue -> value.value()
        is FloatValue -> value.value().toDouble()
        is IntegerValue -> value.value().toDouble()
        is LongValue -> value.value().toDouble()
        is ShortValue -> value.value().toDouble()
        is ByteValue -> value.value().toDouble()
        else -> throw EvaluationException("Cannot convert to double: ${value.type().name()}")
    }

    private fun toBooleanValue(value: Value?): Boolean = when (value) {
        null -> false
        is BooleanValue -> value.value()
        else -> true // Non-null objects are truthy
    }

    private fun valueToString(value: Value?): String = when (value) {
        null -> "null"
        is StringReference -> value.value()
        is BooleanValue -> value.value().toString()
        is CharValue -> value.value().toString()
        is PrimitiveValue -> value.toString()
        else -> formatValue(value)
    }

    /**
     * 比较两个值是否相等
     */
    private fun compareValues(left: Value?, right: Value?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        
        return when {
            left is BooleanValue && right is BooleanValue -> left.value() == right.value()
            left is CharValue && right is CharValue -> left.value() == right.value()
            left is StringReference && right is StringReference -> left.value() == right.value()
            left is PrimitiveValue && right is PrimitiveValue -> toDouble(left) == toDouble(right)
            left is ObjectReference && right is ObjectReference -> left.uniqueID() == right.uniqueID()
            else -> false
        }
    }

    /**
     * 数值比较
     */
    private fun compareNumeric(left: Value?, right: Value?): Int {
        if (left == null || right == null) {
            throw EvaluationException("Cannot compare null values")
        }
        
        val leftNum = toDouble(left)
        val rightNum = toDouble(right)
        return leftNum.compareTo(rightNum)
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
    @Suppress("UNUSED_PARAMETER")
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
