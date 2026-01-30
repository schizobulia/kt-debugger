package functions

/**
 * 高阶函数和 Lambda 表达式示例
 * 用于演示调试器在函数式编程构造中的行为
 *
 * 调试建议:
 * - 在 lambda 内部设置断点
 * - 使用 Step Into 进入 lambda 表达式
 * - 观察闭包捕获的变量
 */
object Lambdas {

    /**
     * Lambda 表达式基础
     */
    fun lambdaBasics() {
        println("=== Lambda 表达式基础 ===")

        // 基本 lambda
        val sum = { a: Int, b: Int -> a + b }  // 断点: lambda 定义
        val result = sum(5, 3)                 // 断点: lambda 调用
        println("sum(5, 3) = $result")

        // 单参数 lambda (使用 it)
        val double = { x: Int -> x * 2 }
        println("double(10) = ${double(10)}")  // 断点: 使用 it

        // 无参数 lambda
        val greet = { println("  Hello from lambda!") }
        greet()                                // 断点: 无参数 lambda

        // lambda 类型声明
        val operation: (Int, Int) -> Int = { x, y -> x * y }
        println("operation(4, 5) = ${operation(4, 5)}")
    }

    /**
     * 高阶函数 (接受函数作为参数)
     */
    fun higherOrderFunctions() {
        println("\n=== 高阶函数 ===")

        // 使用高阶函数
        val result1 = calculate(10, 20) { a, b -> a + b }  // 断点: 传递 lambda
        println("calculate with add: $result1")

        val result2 = calculate(10, 20) { a, b -> a * b }  // 断点: 不同的 lambda
        println("calculate with multiply: $result2")

        // 函数作为返回值
        val adder = createAdder(5)             // 断点: 返回函数
        println("adder(10) = ${adder(10)}")
        println("adder(20) = ${adder(20)}")
    }

    // 高阶函数: 接受函数参数
    fun calculate(a: Int, b: Int, operation: (Int, Int) -> Int): Int {
        return operation(a, b)                 // 断点: 在高阶函数内部
    }

    // 高阶函数: 返回函数
    fun createAdder(addend: Int): (Int) -> Int {
        return { x -> x + addend }             // 断点: 返回的 lambda
    }

    /**
     * 闭包演示
     */
    fun closures() {
        println("\n=== 闭包 ===")

        var counter = 0                        // 断点: 闭包捕获的变量

        val increment = {
            counter++                          // 断点: 修改闭包变量
            println("  Counter: $counter")
        }

        increment()                            // 断点: 第一次调用
        increment()                            // 断点: 第二次调用
        increment()                            // 断点: 第三次调用

        println("Final counter: $counter")     // 断点: 最终值
    }

    /**
     * 集合的函数式操作
     */
    fun collectionOperations() {
        println("\n=== 集合函数式操作 ===")

        val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        println("原始列表: $numbers")

        // map
        val doubled = numbers.map { it * 2 }   // 断点: map 操作
        println("map (x2): $doubled")

        // filter
        val evens = numbers.filter { it % 2 == 0 }  // 断点: filter 操作
        println("filter (偶数): $evens")

        // find
        val firstGreaterThan5 = numbers.find { it > 5 }  // 断点: find 操作
        println("find (>5): $firstGreaterThan5")

        // reduce
        val sum = numbers.reduce { acc, n ->   // 断点: reduce 操作
            acc + n
        }
        println("reduce (sum): $sum")

        // fold
        val product = numbers.take(5).fold(1) { acc, n ->  // 断点: fold 操作
            acc * n
        }
        println("fold (product of first 5): $product")

        // 链式调用
        val result = numbers
            .filter { it % 2 == 1 }            // 断点: 链式 filter
            .map { it * it }                   // 断点: 链式 map
            .take(3)                           // 断点: 链式 take
        println("链式操作 (奇数的平方, 取前3): $result")
    }

    /**
     * forEach 和 forEachIndexed
     */
    fun forEachDemo() {
        println("\n=== forEach 操作 ===")

        val fruits = listOf("苹果", "香蕉", "橙子", "葡萄")

        // forEach
        println("forEach:")
        fruits.forEach { fruit ->              // 断点: forEach lambda
            println("  $fruit")
        }

        // forEachIndexed
        println("forEachIndexed:")
        fruits.forEachIndexed { index, fruit -> // 断点: 带索引的 forEach
            println("  [$index] $fruit")
        }
    }

    /**
     * let, run, with, apply, also 演示
     */
    fun scopeFunctions() {
        println("\n=== 作用域函数 ===")

        // let - 用于空安全调用和变换
        val nullableString: String? = "Hello"
        nullableString?.let {                  // 断点: let 作用域
            println("let: 长度是 ${it.length}")
        }

        // run - 对象配置和计算结果
        val result = "Hello".run {             // 断点: run 作用域
            println("run: 字符串是 $this")
            this.length + 10  // 返回值
        }
        println("run 结果: $result")

        // with - 调用对象的多个方法
        val numbers = mutableListOf(1, 2, 3)
        with(numbers) {                        // 断点: with 作用域
            add(4)
            add(5)
            println("with: $this")
        }

        // apply - 对象配置
        val stringBuilder = StringBuilder().apply {  // 断点: apply 作用域
            append("Hello")
            append(" ")
            append("World")
        }
        println("apply 结果: $stringBuilder")

        // also - 附加操作
        val list = mutableListOf(1, 2, 3).also {  // 断点: also 作用域
            println("also: 创建了列表 $it")
        }
        list.add(4)
        println("also 后的列表: $list")
    }

    /**
     * 函数引用
     */
    fun functionReferences() {
        println("\n=== 函数引用 ===")

        val numbers = listOf(1, -2, 3, -4, 5)

        // 使用 lambda
        val positives1 = numbers.filter { it > 0 }
        println("lambda filter: $positives1")

        // 使用函数引用
        val positives2 = numbers.filter(::isPositive)  // 断点: 函数引用
        println("函数引用 filter: $positives2")

        // 成员函数引用
        val strings = listOf("hello", "world")
        val lengths = strings.map(String::length)      // 断点: 成员引用
        println("成员函数引用 map: $lengths")
    }

    fun isPositive(n: Int): Boolean {
        return n > 0                           // 断点: 被引用的函数
    }

    /**
     * 运行所有 Lambda 示例
     */
    fun runAll() {
        lambdaBasics()
        higherOrderFunctions()
        closures()
        collectionOperations()
        forEachDemo()
        scopeFunctions()
        functionReferences()
    }
}
