import java.util.Scanner
import kotlin.random.Random

/**
 * 交互式调试测试程序
 * 等待用户输入，方便设置断点和调试
 */
fun main() {
    println("=== Kotlin Debug Test Program ===")
    println("Commands: calc, list, random, inline, nested, reified, lambda, loop, eval, cond, quit")
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
            input == "eval" -> {
                testEvaluateExpression()
            }
            input == "cond" -> {
                testConditionalBreakpoint()
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
          eval     - Test expression evaluation (debug console/watch)
          cond     - Test conditional breakpoints
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

/**
 * 测试表达式求值 - 调试控制台和监视器功能测试
 * 
 * 在调试时可以测试以下表达式求值场景:
 * 1. 简单变量: x, name, person
 * 2. 成员访问: person.name, person.age
 * 3. 数组访问: numbers[0], matrix[1][2]
 * 4. 方法调用: person.toString(), numbers.size
 * 5. 算术表达式: x + y, a * b
 * 6. 字面量: 42, "hello", true
 */
fun testEvaluateExpression() {
    println("=== Testing Expression Evaluation ===")
    println("Set breakpoints and test various expressions in debug console/watch window")
    println()

    // 基础类型变量
    val intVar = 42
    val longVar = 9999999999L
    val doubleVar = 3.14159
    val boolVar = true
    val stringVar = "Hello, Kotlin Debugger!"
    val charVar = 'K'

    // 断点1: 测试基础类型表达式
    // 监视器中尝试: intVar, stringVar, boolVar, doubleVar
    println("1. Basic types ready - set breakpoint here")
    println("   intVar=$intVar, doubleVar=$doubleVar, boolVar=$boolVar")

    // 数组测试
    val intArray = intArrayOf(10, 20, 30, 40, 50)
    val stringArray = arrayOf("apple", "banana", "cherry")
    val matrix = arrayOf(
        intArrayOf(1, 2, 3),
        intArrayOf(4, 5, 6),
        intArrayOf(7, 8, 9)
    )

    // 断点2: 测试数组表达式
    // 监视器中尝试: intArray[0], stringArray[1], matrix[1][1]
    println("2. Arrays ready - set breakpoint here")
    println("   intArray.size=${intArray.size}, stringArray.size=${stringArray.size}")

    // 对象测试
    val person = Person("Alice", 30)
    val student = Student("Bob", 20, "Computer Science")
    val company = Company("TechCorp", listOf(person, Person("Charlie", 25)))

    // 断点3: 测试对象字段访问
    // 监视器中尝试: person.name, person.age, student.major, company.employees[0].name
    println("3. Objects ready - set breakpoint here")
    println("   person=$person")
    println("   student=$student")

    // 集合测试
    val numberList = mutableListOf(1, 2, 3, 4, 5)
    val nameMap = mapOf("a" to "Alice", "b" to "Bob", "c" to "Charlie")
    val nameSet = setOf("Alice", "Bob", "Charlie")

    // 断点4: 测试集合表达式
    // 监视器中尝试: numberList[0], numberList.size, nameMap.size
    println("4. Collections ready - set breakpoint here")
    println("   numberList.size=${numberList.size}")

    // 方法调用测试
    val calculator = Calculator()
    val result1 = calculator.add(10, 20)
    val result2 = calculator.multiply(5, 6)

    // 断点5: 测试方法调用表达式
    // 监视器中尝试: calculator.add(3, 4), person.toString(), numberList.isEmpty()
    println("5. Method calls ready - set breakpoint here")
    println("   calculator.add(10, 20)=$result1")
    println("   calculator.multiply(5, 6)=$result2")

    // 复杂表达式测试
    val a = 10
    val b = 20
    val c = 30

    // 断点6: 测试复杂表达式
    // 监视器中尝试: a + b, a * b + c, person.age > 25, stringVar.length
    println("6. Complex expressions ready - set breakpoint here")
    println("   a=$a, b=$b, c=$c")
    println("   a + b + c = ${a + b + c}")

    // 嵌套对象测试
    val nested = NestedContainer(
        inner = InnerData(
            value = 100,
            name = "nested-value",
            items = listOf("item1", "item2", "item3")
        )
    )

    // 断点7: 测试嵌套对象访问
    // 监视器中尝试: nested.inner.value, nested.inner.name, nested.inner.items[0]
    println("7. Nested objects ready - set breakpoint here")
    println("   nested.inner.value=${nested.inner.value}")
    println("   nested.inner.items.size=${nested.inner.items.size}")

    println()
    println("=== Expression Evaluation Test Complete ===")
}

// 测试用数据类
data class Person(val name: String, val age: Int)

data class Student(val name: String, val age: Int, val major: String)

data class Company(val name: String, val employees: List<Person>)

data class InnerData(val value: Int, val name: String, val items: List<String>)

data class NestedContainer(val inner: InnerData)

class Calculator {
    fun add(x: Int, y: Int): Int = x + y
    fun multiply(x: Int, y: Int): Int = x * y
    fun subtract(x: Int, y: Int): Int = x - y
    fun divide(x: Int, y: Int): Int = if (y != 0) x / y else 0
}

/**
 * 条件断点测试 - 测试条件断点功能
 * 
 * 使用方法:
 * 1. 在 VSCode 中设置断点，并添加条件
 * 2. 例如在循环中设置断点，条件为 "i == 5" 或 "i > 7"
 * 3. 程序只会在条件满足时停止
 */
fun testConditionalBreakpoint() {
    println("=== Conditional Breakpoint Test ===")
    println()
    
    // 测试1: 简单循环条件断点
    // 建议在下面的行设置断点，条件: i == 5 或 i > 8
    println("Test 1: Loop with counter (set breakpoint with condition: i == 5)")
    for (i in 1..10) {
        val squared = i * i  // 断点行 - 条件: i == 5
        println("  i=$i, squared=$squared")
    }
    println()
    
    // 测试2: 基于变量值的条件断点
    // 建议在下面的行设置断点，条件: value > 50
    println("Test 2: Value-based condition (set breakpoint with condition: value > 50)")
    val values = listOf(10, 30, 55, 72, 45, 88, 15)
    for (value in values) {
        val doubled = value * 2  // 断点行 - 条件: value > 50
        println("  value=$value, doubled=$doubled")
    }
    println()
    
    // 测试3: 字符串条件断点
    // 建议在下面的行设置断点，条件: name == "Charlie"
    println("Test 3: String condition (set breakpoint with condition: name == \"Charlie\")")
    val names = listOf("Alice", "Bob", "Charlie", "David", "Eve")
    for (name in names) {
        val greeting = "Hello, $name!"  // 断点行 - 条件: name == "Charlie"
        println("  $greeting")
    }
    println()
    
    // 测试4: 布尔条件断点
    // 建议在下面的行设置断点，条件: isEven
    println("Test 4: Boolean condition (set breakpoint with condition: isEven)")
    for (num in 1..6) {
        val isEven = num % 2 == 0
        val label = if (isEven) "even" else "odd"  // 断点行 - 条件: isEven
        println("  num=$num is $label")
    }
    println()
    
    // 测试5: 对象属性条件断点
    // 建议在下面的行设置断点，条件: person.age >= 30
    println("Test 5: Object property condition (set breakpoint with condition: person.age >= 30)")
    val people = listOf(
        Person("Alice", 25),
        Person("Bob", 35),
        Person("Charlie", 28),
        Person("David", 42)
    )
    for (person in people) {
        val description = "${person.name} is ${person.age} years old"  // 断点行 - 条件: person.age >= 30
        println("  $description")
    }
    println()
    
    // 测试6: 复合条件断点
    // 建议在下面的行设置断点，条件: x > 3 && y < 8
    println("Test 6: Compound condition (set breakpoint with condition: x > 3 && y < 8)")
    for (x in 1..5) {
        for (y in 1..10) {
            val product = x * y  // 断点行 - 条件: x > 3 && y < 8
            if (x == 3 && y == 5) {
                println("  x=$x, y=$y, product=$product")
            }
        }
    }
    println()
    
    // 测试7: 方法调用条件断点
    // 建议在下面的行设置断点，条件: items.size > 2
    println("Test 7: Method call condition (set breakpoint with condition: items.isEmpty())")
    val itemLists = listOf(
        listOf("a", "b", "c"),
        emptyList(),
        listOf("x"),
        emptyList(),
        listOf("m", "n")
    )
    for ((index, items) in itemLists.withIndex()) {
        val status = if (items.isEmpty()) "empty" else "has ${items.size} items"  // 断点行 - 条件: items.isEmpty()
        println("  List $index: $status")
    }
    println()
    
    println("=== Conditional Breakpoint Test Complete ===")
}
