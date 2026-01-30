package basics

/**
 * 控制流示例
 * 用于演示调试器在各种控制流语句中的行为
 *
 * 调试建议:
 * - 设置断点观察条件分支的执行路径
 * - 使用单步执行(Step Over/Step Into)跟踪循环
 * - 观察循环变量的变化
 */
object ControlFlow {

    /**
     * if 表达式演示
     */
    fun ifExpression() {
        println("=== if 表达式 ===")

        val number = 15

        // 传统 if-else
        if (number > 10) {                   // 断点: 观察条件判断
            println("$number 大于 10")
        } else {
            println("$number 不大于 10")
        }

        // if 作为表达式
        val description = if (number % 2 == 0) {  // 断点: if 表达式求值
            "偶数"
        } else {
            "奇数"
        }
        println("$number 是 $description")

        // 多条件 if-else-if
        val grade = 85
        val level = if (grade >= 90) {       // 断点: 多条件分支
            "优秀"
        } else if (grade >= 80) {
            "良好"
        } else if (grade >= 60) {
            "及格"
        } else {
            "不及格"
        }
        println("成绩 $grade 分，等级: $level")
    }

    /**
     * when 表达式演示 (类似 switch)
     */
    fun whenExpression() {
        println("\n=== when 表达式 ===")

        // 基本 when 表达式
        val dayOfWeek = 3
        val dayName = when (dayOfWeek) {     // 断点: when 表达式
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> "无效"
        }
        println("今天是 $dayName")

        // when 带范围
        val score = 75
        val result = when (score) {          // 断点: 范围匹配
            in 90..100 -> "A"
            in 80..89 -> "B"
            in 70..79 -> "C"
            in 60..69 -> "D"
            else -> "F"
        }
        println("分数 $score 对应等级: $result")

        // when 带类型检查
        val obj: Any = "Hello"
        val typeDesc = when (obj) {          // 断点: 类型检查
            is String -> "字符串，长度 ${obj.length}"
            is Int -> "整数，值为 $obj"
            is Boolean -> "布尔值"
            else -> "未知类型"
        }
        println("类型描述: $typeDesc")

        // when 不带参数 (作为 if-else 链)
        val temperature = 25
        val weather = when {                  // 断点: 无参数 when
            temperature < 0 -> "寒冷"
            temperature < 10 -> "较冷"
            temperature < 20 -> "凉爽"
            temperature < 30 -> "温暖"
            else -> "炎热"
        }
        println("温度 $temperature°C，天气: $weather")
    }

    /**
     * for 循环演示
     */
    fun forLoop() {
        println("\n=== for 循环 ===")

        // 范围循环
        println("正向遍历 1..5:")
        for (i in 1..5) {                    // 断点: 观察 i 变化
            println("  i = $i")
        }

        // 反向遍历
        println("反向遍历 5 downTo 1:")
        for (i in 5 downTo 1) {              // 断点: 反向遍历
            println("  i = $i")
        }

        // 带步长
        println("步长为 2 的遍历 1..10:")
        for (i in 1..10 step 2) {            // 断点: 步长遍历
            println("  i = $i")
        }

        // 排除结束值
        println("使用 until (不包含结束值) 0 until 5:")
        for (i in 0 until 5) {               // 断点: until 不包含结束
            println("  i = $i")
        }

        // 遍历集合
        val fruits = listOf("苹果", "香蕉", "橙子")
        println("遍历集合:")
        for (fruit in fruits) {              // 断点: 集合遍历
            println("  $fruit")
        }

        // 带索引遍历
        println("带索引遍历:")
        for ((index, fruit) in fruits.withIndex()) {  // 断点: 解构遍历
            println("  [$index] $fruit")
        }
    }

    /**
     * while 循环演示
     */
    fun whileLoop() {
        println("\n=== while 循环 ===")

        // while 循环
        var counter = 0
        println("while 循环:")
        while (counter < 5) {                // 断点: 条件检查
            println("  counter = $counter")
            counter++                        // 断点: 变量递增
        }

        // do-while 循环 (至少执行一次)
        var value = 10
        println("do-while 循环:")
        do {
            println("  value = $value")      // 断点: 循环体
            value++
        } while (value < 5)  // 条件为 false，但循环体已执行一次
        println("循环结束后 value = $value")
    }

    /**
     * 循环控制语句演示
     */
    fun loopControl() {
        println("\n=== 循环控制 (break/continue) ===")

        // break 语句
        println("break 示例:")
        for (i in 1..10) {
            if (i == 5) {
                println("  遇到 $i，退出循环")
                break                         // 断点: break 跳出
            }
            println("  i = $i")
        }

        // continue 语句
        println("continue 示例 (跳过偶数):")
        for (i in 1..10) {
            if (i % 2 == 0) {
                continue                      // 断点: continue 跳过
            }
            println("  i = $i")
        }

        // 带标签的 break
        println("带标签的 break:")
        outer@ for (i in 1..3) {
            for (j in 1..3) {
                if (i == 2 && j == 2) {
                    println("  在 i=$i, j=$j 处跳出外层循环")
                    break@outer               // 断点: 标签跳出
                }
                println("  i=$i, j=$j")
            }
        }
    }

    /**
     * 范围和进度演示
     */
    fun rangesAndProgressions() {
        println("\n=== 范围和进度 ===")

        // 整数范围
        val intRange = 1..10
        println("intRange: $intRange, 包含 5: ${5 in intRange}")

        // 字符范围
        val charRange = 'a'..'z'
        println("charRange: $charRange, 包含 'k': ${'k' in charRange}")

        // 进度 (Progression)
        val progression = 10 downTo 1 step 2
        println("progression: ${progression.toList()}")

        // 空范围检查
        val emptyRange = 10..1  // 这是一个空范围
        println("emptyRange.isEmpty(): ${emptyRange.isEmpty()}")
    }

    /**
     * 运行所有控制流示例
     */
    fun runAll() {
        ifExpression()
        whenExpression()
        forLoop()
        whileLoop()
        loopControl()
        rangesAndProgressions()
    }
}
