package functions

/**
 * 函数示例
 * 用于演示调试器在各种函数类型中的行为
 *
 * 调试建议:
 * - 使用 Step Into (F11) 进入函数内部
 * - 使用 Step Out (Shift+F11) 从函数返回
 * - 观察参数和返回值
 */
object Functions {

    /**
     * 基本函数演示
     */
    fun basicFunctions() {
        println("=== 基本函数 ===")

        // 调用普通函数
        val sum = add(10, 20)                // 断点: Step Into 查看函数内部
        println("add(10, 20) = $sum")

        // 调用单表达式函数
        val product = multiply(5, 6)         // 断点: 单表达式函数
        println("multiply(5, 6) = $product")

        // 调用无返回值函数
        greet("Kotlin")                      // 断点: Unit 函数

        // 调用返回多个值的函数
        val (name, age) = getPersonInfo()    // 断点: 解构返回值
        println("Person: $name, $age years old")
    }

    // 普通函数
    fun add(a: Int, b: Int): Int {
        val result = a + b                   // 断点: 查看参数 a, b
        return result
    }

    // 单表达式函数
    fun multiply(a: Int, b: Int): Int = a * b

    // 无返回值函数 (Unit)
    fun greet(name: String) {
        println("  Hello, $name!")           // 断点: Unit 函数内部
    }

    // 返回 Pair (多个值)
    fun getPersonInfo(): Pair<String, Int> {
        return "Alice" to 25                 // 断点: 返回 Pair
    }

    /**
     * 默认参数和命名参数演示
     */
    fun defaultAndNamedParams() {
        println("\n=== 默认参数和命名参数 ===")

        // 使用默认参数
        formatMessage("Hello")               // 断点: 使用所有默认值
        formatMessage("Hello", "World")      // 断点: 覆盖第一个默认参数
        formatMessage("Hello", "World", "!")  // 断点: 覆盖所有默认参数

        // 使用命名参数
        formatMessage(                       // 断点: 命名参数调用
            message = "Test",
            suffix = "!!",
            prefix = ">>>"
        )

        // 部分使用命名参数
        formatMessage("Hi", suffix = "...")   // 断点: 混合参数
    }

    fun formatMessage(
        message: String,
        prefix: String = "[INFO]",
        suffix: String = ""
    ) {
        println("  $prefix $message $suffix") // 断点: 查看参数值
    }

    /**
     * 可变参数演示
     */
    fun varargDemo() {
        println("\n=== 可变参数 ===")

        // 调用可变参数函数
        printAll("a", "b", "c")              // 断点: 可变参数调用

        // 使用 spread 操作符
        val items = arrayOf("x", "y", "z")
        printAll(*items)                     // 断点: spread 操作符

        // 混合使用
        printAll("start", *items, "end")     // 断点: 混合可变参数
    }

    fun printAll(vararg items: String) {
        println("  Items count: ${items.size}")  // 断点: 查看 vararg 数组
        for (item in items) {
            println("    - $item")
        }
    }

    /**
     * 局部函数演示
     */
    fun localFunctions() {
        println("\n=== 局部函数 ===")

        // 局部函数可以访问外部函数的变量
        val multiplier = 3

        fun localMultiply(x: Int): Int {     // 断点: 局部函数定义
            return x * multiplier            // 访问外部变量
        }

        val result1 = localMultiply(5)       // 断点: 调用局部函数
        val result2 = localMultiply(10)

        println("localMultiply(5) = $result1")
        println("localMultiply(10) = $result2")

        // 嵌套的局部函数
        fun outer(x: Int): Int {
            fun inner(y: Int): Int {         // 断点: 嵌套局部函数
                return y * 2
            }
            return inner(x) + 1
        }

        println("outer(5) = ${outer(5)}")
    }

    /**
     * 尾递归函数演示
     */
    fun tailrecDemo() {
        println("\n=== 尾递归函数 ===")

        val n = 10
        val factResult = factorial(n)        // 断点: 调用尾递归函数
        println("factorial($n) = $factResult")

        val fibResult = fibonacci(15)
        println("fibonacci(15) = $fibResult")
    }

    // 尾递归阶乘
    tailrec fun factorial(n: Int, accumulator: Long = 1): Long {
        return if (n <= 1) {
            accumulator                      // 断点: 递归终止条件
        } else {
            factorial(n - 1, n * accumulator)  // 断点: 尾递归调用
        }
    }

    // 尾递归斐波那契
    tailrec fun fibonacci(n: Int, a: Long = 0, b: Long = 1): Long {
        return when {
            n == 0 -> a
            n == 1 -> b
            else -> fibonacci(n - 1, b, a + b)  // 断点: 尾递归
        }
    }

    /**
     * 运行所有函数示例
     */
    fun runAll() {
        basicFunctions()
        defaultAndNamedParams()
        varargDemo()
        localFunctions()
        tailrecDemo()
    }
}
