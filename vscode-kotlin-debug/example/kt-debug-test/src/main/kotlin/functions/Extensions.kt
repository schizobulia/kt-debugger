package functions

/**
 * 扩展函数和扩展属性示例
 * 用于演示调试器在扩展成员中的行为
 *
 * 调试建议:
 * - 在扩展函数内部设置断点
 * - 观察 this 关键字的值
 * - 跟踪扩展函数的调用
 */

/**
 * String 的扩展函数
 */
fun String.addExclamation(): String {
    return this + "!"                          // 断点: 查看 this 是接收者
}

fun String.repeatTimes(n: Int): String {
    val builder = StringBuilder()
    repeat(n) {                                // 断点: 扩展函数内部逻辑
        builder.append(this)
    }
    return builder.toString()
}

fun String.wordCount(): Int {
    return this.trim().split("\\s+".toRegex()).size  // 断点: 字符串处理
}

/**
 * Int 的扩展函数
 */
fun Int.isEven(): Boolean = this % 2 == 0

fun Int.factorial(): Long {
    require(this >= 0) { "Factorial is not defined for negative numbers" }
    var result = 1L
    for (i in 2..this) {                       // 断点: 阶乘计算
        result *= i
    }
    return result
}

fun Int.toOrdinal(): String {
    val suffix = when {
        this % 100 in 11..13 -> "th"
        this % 10 == 1 -> "st"
        this % 10 == 2 -> "nd"
        this % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$this$suffix"                      // 断点: 序数转换
}

/**
 * List 的扩展函数
 */
fun <T> List<T>.secondOrNull(): T? {
    return if (this.size >= 2) this[1] else null  // 断点: 泛型扩展
}

fun List<Int>.sumOfSquares(): Int {
    return this.sumOf { it * it }              // 断点: 高阶函数组合
}

/**
 * 可空类型的扩展函数
 */
fun String?.toEmptyIfNull(): String {
    return this ?: ""                          // 断点: 可空扩展
}

fun String?.isNullOrBlankCustom(): Boolean {
    return this == null || this.isBlank()      // 断点: 空值检查
}

/**
 * 扩展属性
 */
val String.lastChar: Char
    get() = this[this.length - 1]              // 断点: 扩展属性 getter

val List<*>.isNotEmpty: Boolean
    get() = this.size > 0

val Int.squared: Int
    get() = this * this                        // 断点: 计算属性

var StringBuilder.lastChar: Char               // 可变扩展属性
    get() = this[this.length - 1]
    set(value) {
        this.setCharAt(this.length - 1, value) // 断点: 扩展属性 setter
    }

/**
 * 扩展函数演示对象
 */
object Extensions {

    /**
     * 基本扩展函数演示
     */
    fun basicExtensions() {
        println("=== 基本扩展函数 ===")

        // String 扩展
        val greeting = "Hello"
        val withExclamation = greeting.addExclamation()  // 断点: 调用扩展
        println("addExclamation: $withExclamation")

        val repeated = "Hi ".repeatTimes(3)   // 断点: 带参数的扩展
        println("repeatTimes(3): $repeated")

        val sentence = "The quick brown fox"
        println("wordCount: ${sentence.wordCount()}")  // 断点: 字符串扩展
    }

    /**
     * Int 扩展函数演示
     */
    fun intExtensions() {
        println("\n=== Int 扩展函数 ===")

        println("4.isEven(): ${4.isEven()}")   // 断点: 布尔扩展
        println("7.isEven(): ${7.isEven()}")

        println("5.factorial(): ${5.factorial()}")  // 断点: 阶乘扩展
        println("10.factorial(): ${10.factorial()}")

        println("1.toOrdinal(): ${1.toOrdinal()}")  // 断点: 序数扩展
        println("2.toOrdinal(): ${2.toOrdinal()}")
        println("3.toOrdinal(): ${3.toOrdinal()}")
        println("11.toOrdinal(): ${11.toOrdinal()}")
        println("21.toOrdinal(): ${21.toOrdinal()}")
    }

    /**
     * 集合扩展函数演示
     */
    fun collectionExtensions() {
        println("\n=== 集合扩展函数 ===")

        val numbers = listOf(1, 2, 3, 4, 5)
        
        println("numbers.secondOrNull(): ${numbers.secondOrNull()}")  // 断点: 泛型扩展
        
        val single = listOf("only")
        println("single.secondOrNull(): ${single.secondOrNull()}")
        
        println("numbers.sumOfSquares(): ${numbers.sumOfSquares()}")  // 断点: 求和扩展

        val strings = listOf("apple", "banana", "cherry")
        println("strings.secondOrNull(): ${strings.secondOrNull()}")
    }

    /**
     * 可空类型扩展演示
     */
    fun nullableExtensions() {
        println("\n=== 可空类型扩展 ===")

        val nullString: String? = null
        val validString: String? = "Hello"
        val blankString: String? = "   "

        println("null.toEmptyIfNull(): '${nullString.toEmptyIfNull()}'")      // 断点: 空值处理
        println("validString.toEmptyIfNull(): '${validString.toEmptyIfNull()}'")

        println("null.isNullOrBlankCustom(): ${nullString.isNullOrBlankCustom()}")
        println("validString.isNullOrBlankCustom(): ${validString.isNullOrBlankCustom()}")
        println("blankString.isNullOrBlankCustom(): ${blankString.isNullOrBlankCustom()}")
    }

    /**
     * 扩展属性演示
     */
    fun extensionProperties() {
        println("\n=== 扩展属性 ===")

        // 只读扩展属性
        val text = "Kotlin"
        println("text.lastChar: ${text.lastChar}")  // 断点: 访问扩展属性

        val list = listOf(1, 2, 3)
        println("list.isNotEmpty: ${list.isNotEmpty}")

        val empty = emptyList<String>()
        println("empty.isNotEmpty: ${empty.isNotEmpty}")

        println("5.squared: ${5.squared}")          // 断点: 计算扩展属性

        // 可变扩展属性
        val sb = StringBuilder("Hello")
        println("sb.lastChar before: ${sb.lastChar}")
        sb.lastChar = '!'                           // 断点: 设置扩展属性
        println("sb.lastChar after: ${sb.lastChar}")
        println("sb: $sb")
    }

    /**
     * 扩展函数解析演示 (静态解析)
     */
    fun extensionResolution() {
        println("\n=== 扩展函数解析 ===")

        open class Base
        class Derived : Base()

        // 为 Base 定义扩展
        fun Base.printType() = println("  Base extension")

        // 为 Derived 定义扩展
        fun Derived.printType() = println("  Derived extension")

        val base: Base = Derived()             // 断点: 静态类型是 Base
        base.printType()  // 输出 "Base extension" - 扩展函数静态解析

        val derived = Derived()
        derived.printType()  // 输出 "Derived extension"
    }

    /**
     * 运行所有扩展示例
     */
    fun runAll() {
        basicExtensions()
        intExtensions()
        collectionExtensions()
        nullableExtensions()
        extensionProperties()
        extensionResolution()
    }
}
