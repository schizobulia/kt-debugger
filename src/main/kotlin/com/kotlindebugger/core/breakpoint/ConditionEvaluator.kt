package com.kotlindebugger.core.breakpoint

import com.sun.jdi.*

/**
 * 条件断点表达式求值器
 * 
 * 用于在断点命中时评估条件表达式，决定是否真正暂停程序。
 * 
 * 支持的表达式类型：
 * - 简单变量比较: x > 5, name == "test"
 * - 布尔变量: isEnabled, !isDisabled
 * - 数值比较: count >= 10, index < array.length
 * - 字符串比较: str == "hello", str != null
 * - 方法调用: list.isEmpty(), str.startsWith("prefix")
 */
class ConditionEvaluator(private val vm: VirtualMachine) {

    /**
     * 评估条件表达式
     * @param condition 条件表达式字符串
     * @param thread 当前线程
     * @param location 当前位置
     * @return true 如果条件满足或无法评估，false 如果条件不满足
     */
    fun evaluate(condition: String?, thread: ThreadReference, location: Location): Boolean {
        if (condition.isNullOrBlank()) {
            return true // 无条件，直接返回 true
        }

        return try {
            val frame = thread.frame(0)
            evaluateCondition(condition.trim(), frame)
        } catch (e: Exception) {
            System.err.println("Error evaluating condition '$condition': ${e.message}")
            // 如果条件无法评估，默认停止（保守策略）
            true
        }
    }

    /**
     * 评估条件表达式
     */
    private fun evaluateCondition(condition: String, frame: StackFrame): Boolean {
        // 处理比较运算符
        return when {
            condition.contains("==") -> evaluateEquals(condition, frame, true)
            condition.contains("!=") -> evaluateEquals(condition, frame, false)
            condition.contains(">=") -> evaluateComparison(condition, frame, ">=")
            condition.contains("<=") -> evaluateComparison(condition, frame, "<=")
            condition.contains(">") && !condition.contains(">=") -> evaluateComparison(condition, frame, ">")
            condition.contains("<") && !condition.contains("<=") -> evaluateComparison(condition, frame, "<")
            condition.contains("&&") -> evaluateLogicalAnd(condition, frame)
            condition.contains("||") -> evaluateLogicalOr(condition, frame)
            condition.startsWith("!") -> !evaluateCondition(condition.substring(1).trim(), frame)
            else -> evaluateBooleanExpression(condition, frame)
        }
    }

    /**
     * 评估等于/不等于表达式
     */
    private fun evaluateEquals(condition: String, frame: StackFrame, isEquals: Boolean): Boolean {
        val parts = if (isEquals) {
            condition.split("==", limit = 2)
        } else {
            condition.split("!=", limit = 2)
        }
        
        if (parts.size != 2) return true
        
        val leftExpr = parts[0].trim()
        val rightExpr = parts[1].trim()
        
        val leftValue = evaluateExpression(leftExpr, frame)
        val rightValue = evaluateExpression(rightExpr, frame)
        
        val result = compareValues(leftValue, rightValue)
        return if (isEquals) result else !result
    }

    /**
     * 评估比较表达式 (>, <, >=, <=)
     */
    private fun evaluateComparison(condition: String, frame: StackFrame, operator: String): Boolean {
        val parts = condition.split(operator, limit = 2)
        if (parts.size != 2) return true
        
        val leftExpr = parts[0].trim()
        val rightExpr = parts[1].trim()
        
        val leftValue = evaluateExpression(leftExpr, frame)
        val rightValue = evaluateExpression(rightExpr, frame)
        
        val leftNum = toNumber(leftValue)
        val rightNum = toNumber(rightValue)
        
        if (leftNum == null || rightNum == null) {
            System.err.println("Cannot compare non-numeric values: $leftExpr $operator $rightExpr")
            return true
        }
        
        return when (operator) {
            ">" -> leftNum > rightNum
            "<" -> leftNum < rightNum
            ">=" -> leftNum >= rightNum
            "<=" -> leftNum <= rightNum
            else -> true
        }
    }

    /**
     * 评估逻辑与表达式
     */
    private fun evaluateLogicalAnd(condition: String, frame: StackFrame): Boolean {
        val parts = condition.split("&&", limit = 2)
        if (parts.size != 2) return true
        
        val leftResult = evaluateCondition(parts[0].trim(), frame)
        if (!leftResult) return false // 短路求值
        return evaluateCondition(parts[1].trim(), frame)
    }

    /**
     * 评估逻辑或表达式
     */
    private fun evaluateLogicalOr(condition: String, frame: StackFrame): Boolean {
        val parts = condition.split("||", limit = 2)
        if (parts.size != 2) return true
        
        val leftResult = evaluateCondition(parts[0].trim(), frame)
        if (leftResult) return true // 短路求值
        return evaluateCondition(parts[1].trim(), frame)
    }

    /**
     * 评估布尔表达式（直接的布尔变量或布尔返回值的方法）
     */
    private fun evaluateBooleanExpression(expression: String, frame: StackFrame): Boolean {
        val value = evaluateExpression(expression, frame)
        return toBooleanValue(value)
    }

    /**
     * 评估表达式并返回 JDI Value
     */
    private fun evaluateExpression(expression: String, frame: StackFrame): Value? {
        val trimmed = expression.trim()
        
        // 字面量处理
        if (trimmed == "null") return null
        if (trimmed == "true") return vm.mirrorOf(true)
        if (trimmed == "false") return vm.mirrorOf(false)
        
        // 整数字面量
        trimmed.toLongOrNull()?.let { return vm.mirrorOf(it) }
        
        // 浮点数字面量
        trimmed.toDoubleOrNull()?.let { return vm.mirrorOf(it) }
        
        // 字符串字面量 (带引号)
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || 
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            val strValue = trimmed.substring(1, trimmed.length - 1)
            return vm.mirrorOf(strValue)
        }
        
        // 成员访问 (a.b.c)
        if (trimmed.contains(".") && !trimmed.contains("(")) {
            return evaluateMemberAccess(trimmed, frame)
        }
        
        // 方法调用 (method() 或 obj.method())
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            return evaluateMethodCall(trimmed, frame)
        }
        
        // 简单变量
        return findVariable(trimmed, frame)
    }

    /**
     * 查找变量
     */
    private fun findVariable(name: String, frame: StackFrame): Value? {
        // 先尝试查找局部变量
        try {
            val localVar = frame.visibleVariableByName(name)
            if (localVar != null) {
                return frame.getValue(localVar)
            }
        } catch (e: AbsentInformationException) {
            // 忽略
        }
        
        // 尝试查找 this
        if (name == "this") {
            return frame.thisObject()
        }
        
        // 尝试从 this 对象查找字段
        val thisObj = frame.thisObject()
        if (thisObj != null) {
            val field = thisObj.referenceType().fieldByName(name)
            if (field != null) {
                return thisObj.getValue(field)
            }
        }
        
        return null
    }

    /**
     * 评估成员访问表达式 (a.b.c)
     */
    private fun evaluateMemberAccess(expression: String, frame: StackFrame): Value? {
        val parts = expression.split(".")
        var currentValue: Value? = findVariable(parts[0], frame) ?: return null
        
        for (i in 1 until parts.size) {
            val memberName = parts[i]
            currentValue = when (currentValue) {
                is ObjectReference -> {
                    // 先尝试获取字段
                    val field = currentValue.referenceType().fieldByName(memberName)
                    if (field != null) {
                        currentValue.getValue(field)
                    } else {
                        // 尝试 getter 方法
                        val getterName = "get${memberName.replaceFirstChar { it.uppercase() }}"
                        val method = currentValue.referenceType().methodsByName(getterName).firstOrNull()
                        if (method != null && method.argumentTypes().isEmpty()) {
                            currentValue.invokeMethod(
                                frame.thread(),
                                method,
                                emptyList(),
                                ObjectReference.INVOKE_SINGLE_THREADED
                            )
                        } else {
                            null
                        }
                    }
                }
                is ArrayReference -> {
                    when (memberName) {
                        "length", "size" -> vm.mirrorOf(currentValue.length())
                        else -> null
                    }
                }
                else -> null
            }
            
            if (currentValue == null && i < parts.size - 1) {
                return null
            }
        }
        
        return currentValue
    }

    /**
     * 评估方法调用表达式
     */
    private fun evaluateMethodCall(expression: String, frame: StackFrame): Value? {
        val parenIndex = expression.indexOf('(')
        if (parenIndex < 0) return null
        
        val methodPart = expression.substring(0, parenIndex)
        val argsPart = expression.substring(parenIndex + 1, expression.length - 1).trim()
        
        // 解析参数
        val args = if (argsPart.isEmpty()) {
            emptyList()
        } else {
            argsPart.split(",").map { it.trim() }
        }
        
        // 检查是否是 obj.method() 形式
        return if (methodPart.contains(".")) {
            val lastDotIndex = methodPart.lastIndexOf('.')
            val objExpr = methodPart.substring(0, lastDotIndex)
            val methodName = methodPart.substring(lastDotIndex + 1)
            
            val obj = evaluateExpression(objExpr, frame) as? ObjectReference ?: return null
            invokeMethod(obj, methodName, args, frame)
        } else {
            // 在 this 对象上调用方法
            val thisObj = frame.thisObject() ?: return null
            invokeMethod(thisObj, methodPart, args, frame)
        }
    }

    /**
     * 在对象上调用方法
     */
    private fun invokeMethod(
        obj: ObjectReference,
        methodName: String,
        args: List<String>,
        frame: StackFrame
    ): Value? {
        val methods = obj.referenceType().methodsByName(methodName)
        val method = methods.find { it.argumentTypes().size == args.size } ?: return null
        
        val argValues = args.map { evaluateExpression(it, frame) }
        
        return try {
            obj.invokeMethod(
                frame.thread(),
                method,
                argValues,
                ObjectReference.INVOKE_SINGLE_THREADED
            )
        } catch (e: Exception) {
            System.err.println("Error invoking method $methodName: ${e.message}")
            null
        }
    }

    /**
     * 比较两个 JDI Value
     */
    private fun compareValues(left: Value?, right: Value?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        
        return when {
            left is BooleanValue && right is BooleanValue -> left.value() == right.value()
            left is ByteValue && right is ByteValue -> left.value() == right.value()
            left is CharValue && right is CharValue -> left.value() == right.value()
            left is ShortValue && right is ShortValue -> left.value() == right.value()
            left is IntegerValue && right is IntegerValue -> left.value() == right.value()
            left is LongValue && right is LongValue -> left.value() == right.value()
            left is FloatValue && right is FloatValue -> left.value() == right.value()
            left is DoubleValue && right is DoubleValue -> left.value() == right.value()
            left is StringReference && right is StringReference -> left.value() == right.value()
            // 数值类型混合比较
            left is PrimitiveValue && right is PrimitiveValue -> {
                toNumber(left) == toNumber(right)
            }
            // 对象引用比较
            left is ObjectReference && right is ObjectReference -> left.uniqueID() == right.uniqueID()
            // 字符串与字面量比较
            left is StringReference -> left.value() == right.toString()
            right is StringReference -> left.toString() == right.value()
            else -> false
        }
    }

    /**
     * 将 JDI Value 转换为数值
     */
    private fun toNumber(value: Value?): Double? {
        return when (value) {
            is ByteValue -> value.value().toDouble()
            is ShortValue -> value.value().toDouble()
            is IntegerValue -> value.value().toDouble()
            is LongValue -> value.value().toDouble()
            is FloatValue -> value.value().toDouble()
            is DoubleValue -> value.value()
            is CharValue -> value.value().code.toDouble()
            else -> null
        }
    }

    /**
     * 将 JDI Value 转换为布尔值
     */
    private fun toBooleanValue(value: Value?): Boolean {
        return when (value) {
            is BooleanValue -> value.value()
            null -> false
            else -> {
                // 非 null 对象视为 true
                true
            }
        }
    }
}
