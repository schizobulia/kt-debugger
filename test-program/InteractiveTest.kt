import java.util.Scanner
import kotlin.random.Random
import kotlinx.coroutines.*

/**
 * 交互式调试测试程序
 * 等待用户输入，方便设置断点和调试
 */
fun main() {
    println("=== Kotlin Debug Test Program ===")
    println("Commands: calc, list, random, inline, nested, reified, lambda, loop, eval, cond, coroutines, logpoint, quit")
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
            input == "suspend-demo" -> {
                testSuspendOnAttach()
            }
            input == "coroutines" -> {
                testCoroutinesView()
            }
            input == "logpoint" -> {
                testLogpoint()
            }
            input == "hcr" -> {
                testHotCodeReplace()
            }
            input == "exception" -> {
                testExceptionDetails()
            }
            input == "inline-values" -> {
                testInlineValues()
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
          calc         - Test calculation with variables
          list         - Test list operations
          random       - Generate random numbers
          inline       - Test simple inline function
          nested       - Test nested inline functions
          reified      - Test inline functions with reified types
          complex      - Test complex inline scenarios
          lambda       - Test lambda expressions
          loop         - Test loop with counter
          eval         - Test expression evaluation (debug console/watch)
          cond         - Test conditional breakpoints
          coroutines   - Test coroutine view panel (multiple coroutines)
          logpoint     - Test logpoints (set logpoints in VS Code)
          hcr          - Test hot code replace (modify & reload classes)
          exception    - Test enhanced exception details (enable Caught Exceptions breakpoint)
          inline-values - Test inline values provider (set breakpoints inside the function)
          suspend-demo - Demo for suspend-on-attach: run early init code (set breakpoints before this)
          add X Y      - Add two numbers
          help         - Show this help
          quit         - Exit program
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

/**
 * Suspend-on-Attach 演示
 *
 * 用法 (演示如何在程序启动时捕获断点):
 *   1. 启动此程序并开启 JDWP 且 suspend=y:
 *      java -agentlib:jdwp:transport=dt_socket,server=y,suspend=y,address=5005 -cp InteractiveTest.jar InteractiveTestKt
 *   2. 在另一个终端运行 kdb:
 *      kdb attach localhost:5005
 *   3. VM 会暂停。在 kdb 中设置断点:
 *      (kdb) break InteractiveTest.kt:648
 *   4. 继续执行:
 *      (kdb) continue
 *   程序将从第一行开始运行并在断点处停止，让你检查早期初始化逻辑。
 */
fun testSuspendOnAttach() {
    println("=== Suspend-On-Attach Demo ===")
    println("This function simulates early initialization code that you want to debug.")
    println()

    // 早期初始化阶段 - 这些代码在 main() 之后立刻执行，用户需要在这里设置断点
    val config = mutableMapOf<String, String>()  // 断点: 在 attach 后暂停，用户可在此设置断点
    config["version"] = "1.0"
    config["mode"] = "debug"
    config["maxRetries"] = "3"

    println("Phase 1: Configuration loaded")
    config.forEach { (k, v) -> println("  $k = $v") }
    println()

    // 第二阶段 - 数据初始化
    val dataStore = mutableListOf<Int>()
    for (i in 1..5) {
        dataStore.add(i * i)  // 断点: 检查每次循环时 dataStore 的状态
    }

    println("Phase 2: Data store initialized: $dataStore")
    println()

    // 第三阶段 - 处理
    val sum = dataStore.sum()
    val average = sum.toDouble() / dataStore.size  // 断点: 检查 sum 和 average

    println("Phase 3: Processing complete")
    println("  Sum = $sum, Average = $average")
    println()

    println("=== Suspend-On-Attach Demo Complete ===")
    println("Tip: Start this program with suspend=y and attach kdb to debug from the very first line.")
}

/**
 * 协程视图测试
 * 测试方法：
 * 1. 在调试模式下运行程序（suspend=n），attach 调试器
 * 2. 输入 "coroutines" 命令启动多个协程
 * 3. 在 VSCode 调试侧边栏的 "Kotlin Coroutines" 面板中查看协程列表
 * 4. 点击刷新按钮可以获取最新协程状态
 *
 * 注意：需要在 classpath 中包含 kotlinx-coroutines-debug 才能看到完整协程信息
 */
fun testCoroutinesView() {
    println("=== Coroutine View Test ===")
    println("Starting coroutines... Check the 'Kotlin Coroutines' view in VS Code debug panel")
    println()

    runBlocking {
        // 启动多个不同状态的协程
        val jobs = mutableListOf<Job>()

        // 协程1: 循环计算（RUNNING 状态）
        jobs += launch(Dispatchers.Default + CoroutineName("worker-1")) {
            var count = 0
            while (count < 10) {
                delay(500)
                count++
                println("  [worker-1] count=$count")
            }
        }

        // 协程2: 等待 IO（SUSPENDED 状态）
        jobs += launch(Dispatchers.IO + CoroutineName("io-task")) {
            println("  [io-task] Starting IO simulation...")
            delay(3000) // 模拟 IO 等待（SUSPENDED 状态）
            println("  [io-task] IO complete")
        }

        // 协程3: 数据处理
        jobs += launch(CoroutineName("data-processor")) {
            val data = listOf(1, 2, 3, 4, 5)
            for (item in data) {
                delay(400)
                println("  [data-processor] Processing item $item")
            }
        }

        println("  Launched 3 coroutines. Refresh the Coroutines view to see their states.")
        println("  Program will wait for all coroutines to complete...")
        jobs.joinAll()
    }

    println("=== Coroutine View Test Complete ===")
}

/**
 * Logpoint 测试
 * 测试方法：
 * 1. 在调试模式下运行程序
 * 2. 输入 "logpoint" 命令
 * 3. 在 VSCode 中，右键断点 -> 选择 "Add Logpoint"
 * 4. 在 logMessage 中输入类似 "i = {i}, squared = {squared}" 的模板
 * 5. 程序运行时会在调试控制台输出日志，但不会暂停执行
 */
fun testLogpoint() {
    println("=== Logpoint Test ===")
    println("Set a LOGPOINT (not a regular breakpoint) on the line inside the loop")
    println("Use logMessage template like: 'Loop iteration: i={i}, result={result}'")
    println()

    for (i in 1..10) {
        val result = i * i        // <-- 在这里设置 Logpoint
        val message = "item_$i"   // <-- 变量供 logpoint 模板使用
        println("  Processing $message -> $result")
        Thread.sleep(200)
    }

    println("=== Logpoint Test Complete ===")
    println("Check the Debug Console for logpoint output (no breakpoints should have paused execution)")
}

/**
 * Hot Code Replace 测试
 * 测试方法：
 * 1. 启动调试会话（suspend=n 模式）
 * 2. 输入 "hcr" 开始循环
 * 3. 修改代码并重新编译 (.class 文件更新)
 * 4. 使用 VSCode 命令面板: "Kotlin Debug: Hot Code Replace"
 * 5. 或者在编辑器右键菜单选择 "Kotlin Debug: Hot Code Replace"
 * 6. 观察输出变化（无需重启程序）
 */
fun testHotCodeReplace() {
    println("=== Hot Code Replace Test ===")
    println("Modify this function's output and recompile, then use 'Kotlin Debug: Hot Code Replace'")
    println()

    // 这个循环将持续运行，方便测试 HCR
    // 修改 greetMessage 函数后重新编译，然后触发 HCR 观察变化
    var iteration = 0
    while (iteration < 30) {
        iteration++
        println("  [HCR Test] Iteration $iteration: ${greetMessage(iteration)}")
        Thread.sleep(1000) // 每秒输出一次，给用户修改代码的时间
    }

    println("=== Hot Code Replace Test Complete ===")
}

/**
 * HCR 测试用函数 - 修改这个函数的实现后，通过 HCR 验证修改生效
 */
fun greetMessage(count: Int): String {
    // 尝试修改这里的返回值，然后触发 Hot Code Replace
    return "Hello #$count (original)"
}

// ==================== 新功能示例 ====================

/**
 * 测试增强的异常详情（Features: 异常详情增强）
 * 在调试器中：
 *   1. 启用"Caught Exceptions"断点
 *   2. 运行此函数，调试器将在异常处暂停
 *   3. 查看"EXCEPTION" 面板，应显示完整的堆栈跟踪和 cause 链
 */
fun testExceptionDetails() {
    println("=== Exception Details Enhancement Test ===")
    println("Enable 'Caught Exceptions' breakpoint, then run this test.")
    println("You should see full stack trace and cause chain in the debug panel.")
    println()

    try {
        throwWithCause()
    } catch (e: RuntimeException) {
        println("Caught: ${e.message}")
        println("Cause: ${e.cause?.message}")
    }

    println("=== Exception Details Test Complete ===")
}

/** 抛出带 cause 链的异常，用于验证增强的异常详情 */
fun throwWithCause() {
    try {
        val list = listOf(1, 2, 3)
        val value = list[10]  // IndexOutOfBoundsException
        println(value)
    } catch (e: IndexOutOfBoundsException) {
        // 包装成带 cause 的 RuntimeException
        throw RuntimeException("Failed to process list element", e)
    }
}

/**
 * 测试内联值提供者（Features: InlineValues Provider）
 * 在调试器中暂停后，观察编辑器中的变量值显示在代码旁边。
 * 需要 VS Code 1.80+ 且调试器暂停状态下生效。
 */
fun testInlineValues() {
    println("=== Inline Values Provider Test ===")
    println("Set a breakpoint inside this function to see inline values in the editor.")
    println()

    val name = "Kotlin"          // 在这里暂停，应该看到 name = "Kotlin"
    val version = 2.0            // version = 2.0
    val isDebug = true           // isDebug = true
    val items = listOf(1, 2, 3)  // items 应该显示 list 信息

    println("name=$name, version=$version, isDebug=$isDebug, items=$items")
    println("=== Inline Values Test Complete ===")
}

/**
 * @JvmStatic main 函数示例（用于验证 @JvmStatic CodeLens 修复）
 * 在 VS Code 中打开此文件，应该在这个函数旁看到 "Debug" CodeLens。
 */
class JvmStaticExample {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("JvmStatic main function works!")
        }
    }
}

/**
 * 内联 @JvmStatic（同行写法，之前可能没有 CodeLens）
 */
class JvmStaticInlineExample {
    companion object {
        @JvmStatic fun runExample() {
            println("Inline @JvmStatic example")
        }
    }
}
