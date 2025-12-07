import java.util.Scanner
import kotlin.random.Random

/**
 * 交互式调试测试程序
 * 等待用户输入，方便设置断点和调试
 */
fun main() {
    println("=== Kotlin Debug Test Program ===")
    println("Commands: calc, list, random, inline, nested, reified, lambda, loop, quit")
    println()

    val scanner = Scanner(System.`in`)
    var running = true

    while (running) {
        print("> ")
        val input = scanner.nextLine().trim().lowercase()

        when {
            input == "quit" || input == "q" -> {
                running = false
                println("Goodbye!")
            }
            input == "calc" -> {
                testCalculation()
            }
            input == "list" -> {
                testListOperations()
            }
            input == "random" -> {
                testRandomNumbers()
            }
            input == "inline" -> {
                testInlineFunction()
            }
            input == "lambda" -> {
                testLambda()
            }
            input == "loop" -> {
                testLoop()
            }
            input == "nested" -> {
                testNestedInline()
            }
            input == "reified" -> {
                testReifiedInline()
            }
            input == "complex" -> {
                testComplexInline()
            }
            input == "help" || input == "?" -> {
                printHelp()
            }
            input.startsWith("add ") -> {
                val parts = input.removePrefix("add ").split(" ")
                if (parts.size == 2) {
                    val a = parts[0].toIntOrNull()
                    val b = parts[1].toIntOrNull()
                    if (a != null && b != null) {
                        val result = add(a, b)
                        println("$a + $b = $result")
                    } else {
                        println("Invalid numbers")
                    }
                } else {
                    println("Usage: add <num1> <num2>")
                }
            }
            input.isEmpty() -> {
                // 忽略空输入
            }
            else -> {
                println("Unknown command: $input")
                println("Type 'help' for available commands")
            }
        }
    }
}

fun printHelp() {
    println("""
        Available commands:
          calc     - Test calculation with variables
          list     - Test list operations
          random   - Generate random numbers
          inline   - Test simple inline function
          nested   - Test nested inline functions
          reified  - Test inline functions with reified types
          complex  - Test complex inline scenarios
          lambda   - Test lambda expressions
          loop     - Test loop with counter
          add X Y  - Add two numbers
          help     - Show this help
          quit     - Exit program
    """.trimIndent())
}

/**
 * 测试基本计算 - 适合设置断点查看变量
 */
fun testCalculation() {
    println("Testing calculation...")

    val x = 42
    val y = 10
    val sum = x + y          // 断点: 查看 x, y
    val product = x * y      // 断点: 查看 sum
    val result = sum + product

    println("x = $x, y = $y")
    println("sum = $sum, product = $product")
    println("result = $result")
}

/**
 * 测试列表操作 - 适合查看集合类型变量
 */
fun testListOperations() {
    println("Testing list operations...")

    val numbers = mutableListOf(1, 2, 3, 4, 5)
    val doubled = numbers.map { it * 2 }     // 断点: 查看 numbers
    val filtered = doubled.filter { it > 5 } // 断点: 查看 doubled
    val sum = filtered.sum()                 // 断点: 查看 filtered

    println("Original: $numbers")
    println("Doubled: $doubled")
    println("Filtered (>5): $filtered")
    println("Sum: $sum")
}

/**
 * 测试随机数 - 每次结果不同
 */
fun testRandomNumbers() {
    println("Generating random numbers...")

    val count = 5
    val numbers = mutableListOf<Int>()

    for (i in 1..count) {
        val num = Random.nextInt(1, 100)  // 断点: 查看每次生成的随机数
        numbers.add(num)
        println("  [$i] Generated: $num")
    }

    val max = numbers.maxOrNull() ?: 0
    val min = numbers.minOrNull() ?: 0
    val avg = numbers.average()

    println("Max: $max, Min: $min, Avg: $avg")
}

/**
 * 内联函数测试 - 测试 SMAP 和内联栈帧
 */
inline fun inlineCalculate(a: Int, b: Int, operation: (Int, Int) -> Int): Int {
    val result = operation(a, b)  // 断点: 测试内联函数调试
    return result
}


/**
 * Lambda 测试 - 测试 Lambda 断点
 */
fun testLambda() {
    println("Testing lambda expressions...")

    val items = listOf("apple", "banana", "cherry", "date")

    items.forEach { item ->
        val upper = item.uppercase()  // 断点: 在 Lambda 内部
        println("  $item -> $upper")
    }

    val lengths = items.map { it.length }
    println("Lengths: $lengths")

    val longItems = items.filter { it.length > 5 }
    println("Long items (>5 chars): $longItems")
}

/**
 * 循环测试 - 测试循环中的断点
 */
fun testLoop() {
    println("Testing loop...")

    var sum = 0
    var i = 1

    while (i <= 10) {
        sum += i        // 断点: 观察 i 和 sum 的变化
        println("  i=$i, sum=$sum")
        i++
    }

    println("Final sum: $sum")
}

/**
 * 简单加法函数 - 测试函数调用栈
 */
fun add(a: Int, b: Int): Int {
    val result = a + b  // 断点: 查看参数和局部变量
    return result
}

/**
 * 带有对象的测试类
 */
data class Person(
    val name: String,
    val age: Int
) {
    fun greet(): String {
        return "Hello, I'm $name and I'm $age years old"
    }
}

fun testObjects() {
    val person = Person("Alice", 30)  // 断点: 查看对象属性
    println(person.greet())

    val people = listOf(
        Person("Bob", 25),
        Person("Charlie", 35),
        Person("Diana", 28)
    )

    people.forEach { p ->
        println("  ${p.name}: ${p.age}")  // 断点: 查看列表中的对象
    }
}

// ==================== 内联函数测试 ====================

/**
 * 简单内联函数
 */
inline fun simpleInline(name: String): String {
    return "Hello, $name!"  // 在这里设置断点测试内联调试
}

/**
 * 带lambda的内联函数
 */
inline fun inlineWithLambda(value: Int, operation: (Int) -> Int): Int {
    val result = operation(value)  // 在这里设置断点
    return result * 2
}

/**
 * 嵌套内联函数
 */
inline fun outerInline(x: Int) {
    println("Outer inline: x = $x")  // 在这里设置断点
    innerInline(x * 2)
}

inline fun innerInline(y: Int) {
    println("Inner inline: y = $y")  // 在这里设置断点，测试嵌套内联
}

/**
 * 带reified参数的内联函数
 */
inline fun <reified T> checkType(value: Any): Boolean {
    val isType = value is T  // 在这里设置断点，测试reified类型
    return isType
}

/**
 * 内联属性访问器
 */
inline val <T> T.expanded: T
    get() = this

/**
 * 复杂的内联场景
 */
inline fun complexInlineScenario(processor: (String, Int) -> String): String {
    val name = "Alice"
    val age = 25

    // 在内联函数内部创建局部变量
    val temp = "Processing: "

    // 调用传入的lambda
    val result = temp + processor(name, age)

    return result.uppercase()
}

/**
 * 测试简单内联函数
 */
fun testInlineFunction() {
    println("Testing simple inline function...")
    val result = simpleInline("World")  // 在这里设置断点，step into测试
    println("Result: $result")
}

/**
 * 测试嵌套内联函数
 */
fun testNestedInline() {
    println("Testing nested inline functions...")
    outerInline(5)  // 在这里设置断点，step into测试嵌套
}

/**
 * 测试带reified参数的内联函数
 */
fun testReifiedInline() {
    println("Testing reified inline function...")

    val test1 = checkType<String>("Hello")    // 断点：测试String类型检查
    println("checkType<String>(\"Hello\"): $test1")

    val test2 = checkType<Int>("Hello")       // 断点：测试类型检查失败
    println("checkType<Int>(\"Hello\"): $test2")

    val test3 = checkType<Int>(42)            // 断点：测试Int类型检查
    println("checkType<Int>(42): $test3")

    val test4 = checkType<List<String>>(listOf("a", "b"))  // 断点：测试泛型类型
    println("checkType<List<String>>(listOf(\"a\", \"b\")): $test4")
}

/**
 * 测试复杂内联场景
 */
fun testComplexInline() {
    println("Testing complex inline scenarios...")

    // 测试带lambda的内联函数
    println("\n1. 测试带lambda的内联函数")
    val result1 = inlineWithLambda(5) { it * 3 }  // 在这里设置断点
    println("Result: $result1")

    // 测试内联属性
    println("\n2. 测试内联属性")
    val value = "Test"
    val expanded = value.expanded  // 在这里设置断点
    println("expanded: $expanded")

    // 测试复杂内联场景
    println("\n3. 测试复杂内联场景")
    val result2 = complexInlineScenario { name, age ->
        "$name is $age years old"  // 在lambda内部设置断点
    }
    println("Result: $result2")

    // 测试内联属性扩展
    println("\n4. 测试内联属性扩展")
    val number = 42
    val expandedNumber = number.expanded
    println("expandedNumber: $expandedNumber")

    println("\n=== 内联测试完成 ===")
}
