package basics

/**
 * 基础类型和变量示例
 * 用于演示调试器如何显示和检查各种基本类型
 *
 * 调试建议:
 * - 在变量声明行设置断点，查看变量值
 * - 使用监视器(Watch)窗口观察变量变化
 * - 在调试控制台(Debug Console)中输入变量名查看值
 */
object Variables {

    /**
     * 基本数据类型演示
     */
    fun basicTypes() {
        println("=== 基本数据类型 ===")

        // 整数类型
        val byteVal: Byte = 127              // 断点: 查看 Byte 类型
        val shortVal: Short = 32767          // 断点: 查看 Short 类型
        val intVal: Int = 2147483647         // 断点: 查看 Int 类型
        val longVal: Long = 9223372036854775807L  // 断点: 查看 Long 类型

        // 浮点类型
        val floatVal: Float = 3.14159f       // 断点: 查看 Float 类型
        val doubleVal: Double = 2.718281828  // 断点: 查看 Double 类型

        // 字符和布尔类型
        val charVal: Char = 'K'              // 断点: 查看 Char 类型
        val boolVal: Boolean = true          // 断点: 查看 Boolean 类型

        // 字符串类型
        val stringVal: String = "Hello, Kotlin Debugger!"  // 断点: 查看 String 类型
        val multilineString = """
            |这是一个
            |多行字符串
            |用于测试
        """.trimMargin()

        println("byteVal = $byteVal")
        println("shortVal = $shortVal")
        println("intVal = $intVal")
        println("longVal = $longVal")
        println("floatVal = $floatVal")
        println("doubleVal = $doubleVal")
        println("charVal = $charVal")
        println("boolVal = $boolVal")
        println("stringVal = $stringVal")
        println("multilineString = $multilineString")
    }

    /**
     * 变量声明方式演示 (val vs var)
     */
    fun valVsVar() {
        println("\n=== val vs var ===")

        // val - 不可变引用 (类似 Java 的 final)
        val immutableValue = 42              // 断点: 这个值不能被重新赋值
        println("immutableValue = $immutableValue")

        // var - 可变引用
        var mutableValue = 10                // 断点: 初始值
        println("mutableValue (初始) = $mutableValue")

        mutableValue = 20                    // 断点: 值改变后
        println("mutableValue (修改后) = $mutableValue")

        mutableValue += 5                    // 断点: 累加后
        println("mutableValue (累加后) = $mutableValue")
    }

    /**
     * 类型推断演示
     */
    fun typeInference() {
        println("\n=== 类型推断 ===")

        // 编译器自动推断类型
        val inferredInt = 100                // 推断为 Int
        val inferredDouble = 3.14            // 推断为 Double
        val inferredString = "Kotlin"        // 推断为 String
        val inferredBoolean = false          // 推断为 Boolean
        val inferredList = listOf(1, 2, 3)   // 推断为 List<Int>

        // 断点: 在调试器中查看这些变量的推断类型
        println("inferredInt: ${inferredInt::class.simpleName} = $inferredInt")
        println("inferredDouble: ${inferredDouble::class.simpleName} = $inferredDouble")
        println("inferredString: ${inferredString::class.simpleName} = $inferredString")
        println("inferredBoolean: ${inferredBoolean::class.simpleName} = $inferredBoolean")
        println("inferredList: ${inferredList::class.simpleName} = $inferredList")
    }

    /**
     * 常量演示
     */
    const val COMPILE_TIME_CONSTANT = "编译时常量"

    fun constants() {
        println("\n=== 常量 ===")

        // 编译时常量 (const val) - 只能用于顶级或 object 中
        println("COMPILE_TIME_CONSTANT = $COMPILE_TIME_CONSTANT")

        // 运行时常量
        val runtimeConstant = System.currentTimeMillis()
        println("runtimeConstant = $runtimeConstant")
    }

    /**
     * 数组演示
     */
    fun arrays() {
        println("\n=== 数组 ===")

        // 基本类型数组
        val intArray = intArrayOf(1, 2, 3, 4, 5)           // 断点: 查看 IntArray
        val doubleArray = doubleArrayOf(1.1, 2.2, 3.3)     // 断点: 查看 DoubleArray
        val charArray = charArrayOf('a', 'b', 'c')         // 断点: 查看 CharArray

        // 对象数组
        val stringArray = arrayOf("Hello", "World", "!")   // 断点: 查看 Array<String>
        val anyArray = arrayOf(1, "two", 3.0, true)        // 断点: 查看 Array<Any>

        // 二维数组
        val matrix = arrayOf(
            intArrayOf(1, 2, 3),
            intArrayOf(4, 5, 6),
            intArrayOf(7, 8, 9)
        )  // 断点: 查看二维数组结构

        println("intArray = ${intArray.contentToString()}")
        println("doubleArray = ${doubleArray.contentToString()}")
        println("charArray = ${charArray.contentToString()}")
        println("stringArray = ${stringArray.contentToString()}")
        println("anyArray = ${anyArray.contentToString()}")
        println("matrix[1][1] = ${matrix[1][1]}")
    }

    /**
     * 运行所有变量示例
     */
    fun runAll() {
        basicTypes()
        valVsVar()
        typeInference()
        constants()
        arrays()
    }
}
