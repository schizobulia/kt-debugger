package advanced

/**
 * 内联函数示例
 * 用于演示调试器在内联函数中的行为
 *
 * 调试建议:
 * - 使用 Step Into 进入内联函数
 * - 观察内联函数如何展开
 * - 注意 SMAP 信息如何映射源代码位置
 */
object InlineFunctions {

    /**
     * 基本内联函数演示
     */
    fun basicInline() {
        println("=== 基本内联函数 ===")

        // 调用简单内联函数
        val result = simpleInline(5, 3)        // 断点: 调用内联函数
        println("simpleInline(5, 3) = $result")

        // 调用带 lambda 的内联函数
        val doubled = transform(10) { it * 2 } // 断点: 带 lambda 的内联
        println("transform(10) { it * 2 } = $doubled")

        // 多次调用观察内联展开
        repeat(3) { i ->                       // 断点: repeat 是内联函数
            println("  repeat iteration: $i")
        }
    }

    /**
     * 简单内联函数
     */
    inline fun simpleInline(a: Int, b: Int): Int {
        val sum = a + b                        // 断点: 内联函数内部
        return sum
    }

    /**
     * 带 lambda 参数的内联函数
     */
    inline fun transform(value: Int, operation: (Int) -> Int): Int {
        return operation(value)                // 断点: lambda 调用
    }

    /**
     * 嵌套内联函数演示
     */
    fun nestedInline() {
        println("\n=== 嵌套内联函数 ===")

        val result = outerInline(5) { x ->     // 断点: 外层内联
            innerInline(x) { y ->              // 断点: 内层内联
                y * y
            }
        }
        println("nestedInline result = $result")
    }

    inline fun outerInline(x: Int, block: (Int) -> Int): Int {
        println("  outerInline: x = $x")       // 断点: 外层执行
        return block(x) + 1
    }

    inline fun innerInline(x: Int, block: (Int) -> Int): Int {
        println("  innerInline: x = $x")       // 断点: 内层执行
        return block(x)
    }

    /**
     * reified 类型参数演示
     */
    fun reifiedDemo() {
        println("\n=== reified 类型参数 ===")

        // 类型检查
        val isString = checkType<String>("Hello")  // 断点: reified 类型检查
        println("checkType<String>(\"Hello\"): $isString")

        val isInt = checkType<Int>("Hello")
        println("checkType<Int>(\"Hello\"): $isInt")

        // 类型名获取
        val stringName = getTypeName<String>()     // 断点: 获取类型名
        println("getTypeName<String>(): $stringName")

        val listName = getTypeName<List<Int>>()
        println("getTypeName<List<Int>>(): $listName")

        // 过滤特定类型
        val mixed = listOf(1, "two", 3, "four", 5.0)
        val strings = filterByType<String>(mixed)  // 断点: 过滤类型
        println("filterByType<String>: $strings")

        val ints = filterByType<Int>(mixed)
        println("filterByType<Int>: $ints")
    }

    inline fun <reified T> checkType(value: Any): Boolean {
        return value is T                      // 断点: 类型检查
    }

    inline fun <reified T> getTypeName(): String {
        return T::class.simpleName ?: "Unknown"  // 断点: 获取类名
    }

    inline fun <reified T> filterByType(list: List<Any>): List<T> {
        return list.filterIsInstance<T>()      // 断点: 过滤实例
    }

    /**
     * noinline 和 crossinline 修饰符
     */
    fun modifiersDemo() {
        println("\n=== noinline 和 crossinline ===")

        // noinline - 阻止 lambda 被内联
        val storedLambda = withNoinline { x -> x * 2 }  // 断点: noinline
        println("storedLambda(5) = ${storedLambda(5)}")

        // crossinline - 禁止非局部返回
        withCrossinline {                      // 断点: crossinline
            println("  crossinline block executed")
            // return  // 编译错误：不能非局部返回
        }
        println("After withCrossinline")
    }

    inline fun withNoinline(noinline block: (Int) -> Int): (Int) -> Int {
        println("  Storing lambda (not inlined)")  // 断点: 存储 lambda
        return block
    }

    inline fun withCrossinline(crossinline block: () -> Unit) {
        val runnable = Runnable {              // 断点: crossinline 使用
            block()
        }
        runnable.run()
    }

    /**
     * 内联函数与非局部返回
     */
    fun nonLocalReturn() {
        println("\n=== 非局部返回 ===")

        // 在内联 lambda 中可以使用 return
        println("调用 findFirstEven:")
        val result = findFirstEven(listOf(1, 3, 5, 6, 7, 8))  // 断点: 非局部返回
        println("findFirstEven result = $result")

        // 使用标签返回
        println("\n调用 processWithLabel:")
        processWithLabel()
    }

    inline fun findFirstEven(numbers: List<Int>): Int? {
        for (num in numbers) {
            if (num % 2 == 0) {
                return num                     // 断点: 内联函数中的 return
            }
        }
        return null
    }

    fun processWithLabel() {
        listOf(1, 2, 3, 4, 5).forEach { num ->
            if (num == 3) {
                println("  跳过 $num")
                return@forEach                 // 断点: 标签返回
            }
            println("  处理 $num")
        }
        println("forEach 完成")
    }

    /**
     * 内联属性演示
     */
    fun inlineProperties() {
        println("\n=== 内联属性 ===")

        val text = "Hello"
        val firstChar = text.firstChar         // 断点: 内联属性
        println("text.firstChar = $firstChar")

        val numbers = listOf(1, 2, 3, 4, 5)
        println("numbers.secondElement = ${numbers.secondElement}")
        println("numbers.lastIndex2 = ${numbers.lastIndex2}")
    }

    /**
     * 运行所有内联函数示例
     */
    fun runAll() {
        basicInline()
        nestedInline()
        reifiedDemo()
        modifiersDemo()
        nonLocalReturn()
        inlineProperties()
    }
}

// 内联扩展属性
inline val String.firstChar: Char
    get() = this[0]                            // 断点: 内联属性 getter

inline val <T> List<T>.secondElement: T
    get() = this[1]

inline val <T> List<T>.lastIndex2: Int
    get() = this.size - 1
