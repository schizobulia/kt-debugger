package advanced

/**
 * 异常处理示例
 * 用于演示调试器在异常情况下的行为
 *
 * 调试建议:
 * - 设置断点在 throw 语句处
 * - 观察异常的传播和捕获
 * - 使用异常断点功能
 */

// 自定义异常
class ValidationException(message: String) : Exception(message)
class BusinessException(message: String, val errorCode: Int) : Exception(message)

object Exceptions {

    /**
     * 基本异常处理
     */
    fun basicExceptionHandling() {
        println("=== 基本异常处理 ===")

        // try-catch
        try {
            val result = divide(10, 0)         // 断点: 可能抛异常的调用
            println("Result: $result")
        } catch (e: ArithmeticException) {
            println("捕获 ArithmeticException: ${e.message}")  // 断点: 异常捕获
        }

        // try-catch-finally
        try {
            riskyOperation(true)
        } catch (e: Exception) {
            println("捕获异常: ${e.message}")
        } finally {
            println("finally 块始终执行")       // 断点: finally
        }

        // 多个 catch 块
        try {
            parseAndProcess("abc")
        } catch (e: NumberFormatException) {
            println("数字格式错误: ${e.message}")  // 断点: 特定异常
        } catch (e: IllegalArgumentException) {
            println("参数错误: ${e.message}")
        } catch (e: Exception) {
            println("其他异常: ${e.message}")
        }
    }

    fun divide(a: Int, b: Int): Int {
        if (b == 0) {
            throw ArithmeticException("除数不能为零")  // 断点: 抛出异常
        }
        return a / b
    }

    fun riskyOperation(shouldFail: Boolean) {
        if (shouldFail) {
            throw RuntimeException("操作失败")  // 断点: 运行时异常
        }
    }

    fun parseAndProcess(input: String) {
        val number = input.toInt()             // 断点: NumberFormatException
        if (number < 0) {
            throw IllegalArgumentException("数字必须为正")
        }
    }

    /**
     * try 作为表达式
     */
    fun tryAsExpression() {
        println("\n=== try 作为表达式 ===")

        // try 返回值
        val result = try {
            "123".toInt()                      // 断点: try 表达式成功
        } catch (e: NumberFormatException) {
            0
        }
        println("try 表达式结果 (成功): $result")

        val result2 = try {
            "abc".toInt()                      // 断点: try 表达式失败
        } catch (e: NumberFormatException) {
            -1
        }
        println("try 表达式结果 (失败): $result2")
    }

    /**
     * 自定义异常
     */
    fun customExceptions() {
        println("\n=== 自定义异常 ===")

        try {
            validateUser("", 15)               // 断点: 验证失败
        } catch (e: ValidationException) {
            println("验证失败: ${e.message}")
        }

        try {
            processOrder(-1)                   // 断点: 业务异常
        } catch (e: BusinessException) {
            println("业务错误 [${e.errorCode}]: ${e.message}")
        }
    }

    fun validateUser(name: String, age: Int) {
        if (name.isBlank()) {
            throw ValidationException("用户名不能为空")  // 断点: 自定义异常
        }
        if (age < 18) {
            throw ValidationException("年龄必须大于18岁")
        }
    }

    fun processOrder(orderId: Int) {
        if (orderId <= 0) {
            throw BusinessException("无效的订单ID", 1001)  // 断点: 带错误码
        }
    }

    /**
     * 异常链
     */
    fun exceptionChaining() {
        println("\n=== 异常链 ===")

        try {
            performComplexOperation()
        } catch (e: Exception) {
            println("顶层异常: ${e.message}")    // 断点: 顶层捕获

            var cause = e.cause
            while (cause != null) {
                println("  原因: ${cause.message}")  // 断点: 遍历异常链
                cause = cause.cause
            }
        }
    }

    fun performComplexOperation() {
        try {
            lowLevelOperation()
        } catch (e: Exception) {
            throw RuntimeException("复杂操作失败", e)  // 断点: 包装异常
        }
    }

    fun lowLevelOperation() {
        throw IllegalStateException("底层错误")  // 断点: 原始异常
    }

    /**
     * runCatching 和 Result
     */
    fun runCatchingDemo() {
        println("\n=== runCatching 和 Result ===")

        // 使用 runCatching
        val result1 = runCatching {            // 断点: runCatching 成功
            "42".toInt()
        }
        println("result1.isSuccess: ${result1.isSuccess}")
        println("result1.getOrNull(): ${result1.getOrNull()}")

        val result2 = runCatching {            // 断点: runCatching 失败
            "abc".toInt()
        }
        println("result2.isSuccess: ${result2.isSuccess}")
        println("result2.exceptionOrNull(): ${result2.exceptionOrNull()?.message}")

        // getOrDefault
        val value1 = result1.getOrDefault(-1)
        val value2 = result2.getOrDefault(-1)  // 断点: 默认值
        println("getOrDefault: $value1, $value2")

        // getOrElse
        val value3 = result2.getOrElse { e ->  // 断点: getOrElse
            println("  处理异常: ${e.message}")
            0
        }
        println("getOrElse: $value3")

        // map 和 recover
        val mapped = result1.map { it * 2 }    // 断点: map 成功
        println("mapped: ${mapped.getOrNull()}")

        val recovered = result2.recover { 100 }  // 断点: recover
        println("recovered: ${recovered.getOrNull()}")

        // fold
        val folded = result2.fold(
            onSuccess = { "成功: $it" },
            onFailure = { "失败: ${it.message}" }  // 断点: fold
        )
        println("fold: $folded")
    }

    /**
     * use 函数 (资源自动关闭)
     */
    fun useDemo() {
        println("\n=== use 函数 (资源管理) ===")

        // 模拟资源类
        class Resource(val name: String) : AutoCloseable {
            init {
                println("  Resource '$name' 打开")  // 断点: 资源打开
            }

            fun process() {
                println("  Resource '$name' 处理中")  // 断点: 资源使用
            }

            override fun close() {
                println("  Resource '$name' 关闭")  // 断点: 资源关闭
            }
        }

        // 正常使用
        Resource("res1").use { resource ->
            resource.process()
        }

        // 异常时也会关闭
        try {
            Resource("res2").use { resource ->
                resource.process()
                throw RuntimeException("处理中出错")  // 断点: 异常
            }
        } catch (e: Exception) {
            println("捕获异常: ${e.message}")
        }
    }

    /**
     * 运行所有异常示例
     */
    fun runAll() {
        basicExceptionHandling()
        tryAsExpression()
        customExceptions()
        exceptionChaining()
        runCatchingDemo()
        useDemo()
    }
}
