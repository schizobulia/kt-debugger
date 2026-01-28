import java.util.Scanner
import basics.Variables
import basics.ControlFlow
import classes.DataTypes
import classes.Classes
import classes.SpecialClasses
import collections.Collections
import functions.Functions
import functions.Lambdas
import functions.Extensions
import nullsafety.NullSafety
import advanced.InlineFunctions
import advanced.Generics
import advanced.Exceptions

/**
 * Kotlin 调试示例主程序
 *
 * 这个程序展示了 Kotlin 语言的各种特性，用于测试和演示调试器的功能。
 * 每个模块都包含多个示例函数，可以在其中设置断点进行调试。
 *
 * 调试建议:
 * 1. 在感兴趣的示例函数入口处设置断点
 * 2. 使用 Step Into (F11) 进入函数内部
 * 3. 使用 Step Over (F10) 逐行执行
 * 4. 使用 Variables 面板查看变量值
 * 5. 使用 Watch 面板监视表达式
 * 6. 使用 Debug Console 求值表达式
 */
fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║          Kotlin 调试示例程序 (Kotlin Debug Examples)          ║")
    println("║                                                              ║")
    println("║  此程序用于演示和测试 Kotlin 调试器的各种功能                  ║")
    println("║  在代码中设置断点，然后使用调试器运行程序                      ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    val scanner = Scanner(System.`in`)
    var running = true

    while (running) {
        printMainMenu()
        print("\n请选择 (输入数字或命令): ")

        val input = scanner.nextLine().trim().lowercase()

        when {
            input == "q" || input == "quit" || input == "exit" -> {
                running = false
                println("\n再见! Goodbye!")
            }
            input == "all" -> runAllExamples()
            input == "1" -> runBasicsMenu(scanner)
            input == "2" -> runClassesMenu(scanner)
            input == "3" -> runFunctionsMenu(scanner)
            input == "4" -> runCollectionsMenu(scanner)
            input == "5" -> runNullSafetyExamples()
            input == "6" -> runAdvancedMenu(scanner)
            input == "help" || input == "?" -> printHelp()
            input.isEmpty() -> { /* 忽略空输入 */ }
            else -> println("未知命令: $input，输入 'help' 查看帮助")
        }
    }

    scanner.close()
}

/**
 * 打印主菜单
 */
fun printMainMenu() {
    println("""
        
        ═══════════════════════════════════════════════════════════════
        主菜单 (Main Menu)
        ═══════════════════════════════════════════════════════════════
        
        ┌─ 基础语法 (Basics) ──────────────────────────────────────────┐
        │ 1. 基础语法        - 变量、控制流                            │
        └──────────────────────────────────────────────────────────────┘
        
        ┌─ 面向对象 (OOP) ─────────────────────────────────────────────┐
        │ 2. 类和对象        - 数据类、继承、密封类、枚举              │
        └──────────────────────────────────────────────────────────────┘
        
        ┌─ 函数式编程 (Functional) ────────────────────────────────────┐
        │ 3. 函数            - 普通函数、Lambda、扩展函数              │
        └──────────────────────────────────────────────────────────────┘
        
        ┌─ 集合操作 (Collections) ─────────────────────────────────────┐
        │ 4. 集合            - List、Set、Map 及操作                   │
        └──────────────────────────────────────────────────────────────┘
        
        ┌─ 空安全 (Null Safety) ───────────────────────────────────────┐
        │ 5. 空安全          - 可空类型、安全调用、Elvis 操作符        │
        └──────────────────────────────────────────────────────────────┘
        
        ┌─ 高级特性 (Advanced) ────────────────────────────────────────┐
        │ 6. 高级特性        - 内联函数、泛型、异常处理                │
        └──────────────────────────────────────────────────────────────┘
        
        ═══════════════════════════════════════════════════════════════
        命令: all=运行全部  help=帮助  quit=退出
        ═══════════════════════════════════════════════════════════════
    """.trimIndent())
}

/**
 * 打印帮助信息
 */
fun printHelp() {
    println("""
        
        帮助信息 (Help)
        ═══════════════════════════════════════════════════════════════
        
        这是一个用于测试 Kotlin 调试器的示例程序。
        
        使用方法:
        1. 输入菜单对应的数字进入子菜单
        2. 在子菜单中选择要运行的示例
        3. 在源代码中设置断点后运行程序进行调试
        
        调试技巧:
        - F5:        开始/继续调试
        - F9:        切换断点
        - F10:       单步跳过 (Step Over)
        - F11:       单步进入 (Step Into)
        - Shift+F11: 单步跳出 (Step Out)
        
        命令:
        - all:  运行所有示例
        - help: 显示此帮助
        - quit: 退出程序
        
        ═══════════════════════════════════════════════════════════════
    """.trimIndent())
}

// ==================== 子菜单 ====================

/**
 * 基础语法菜单
 */
fun runBasicsMenu(scanner: Scanner) {
    println("""
        
        基础语法示例 (Basics)
        ─────────────────────────────────────────
        1. 变量和类型  - 基本类型、数组、类型推断
        2. 控制流      - if、when、for、while
        a. 运行全部
        b. 返回主菜单
    """.trimIndent())

    print("请选择: ")
    when (scanner.nextLine().trim().lowercase()) {
        "1" -> {
            println("\n" + "═".repeat(60))
            Variables.runAll()
        }
        "2" -> {
            println("\n" + "═".repeat(60))
            ControlFlow.runAll()
        }
        "a" -> {
            println("\n" + "═".repeat(60))
            Variables.runAll()
            println("\n" + "═".repeat(60))
            ControlFlow.runAll()
        }
        "b" -> { /* 返回 */ }
    }
}

/**
 * 类和对象菜单
 */
fun runClassesMenu(scanner: Scanner) {
    println("""
        
        类和对象示例 (Classes & Objects)
        ─────────────────────────────────────────
        1. 数据类型    - data class、泛型数据类
        2. 类和继承    - 普通类、继承、接口
        3. 特殊类      - 密封类、枚举、对象声明
        a. 运行全部
        b. 返回主菜单
    """.trimIndent())

    print("请选择: ")
    when (scanner.nextLine().trim().lowercase()) {
        "1" -> {
            println("\n" + "═".repeat(60))
            DataTypes.runAll()
        }
        "2" -> {
            println("\n" + "═".repeat(60))
            Classes.runAll()
        }
        "3" -> {
            println("\n" + "═".repeat(60))
            SpecialClasses.runAll()
        }
        "a" -> {
            println("\n" + "═".repeat(60))
            DataTypes.runAll()
            println("\n" + "═".repeat(60))
            Classes.runAll()
            println("\n" + "═".repeat(60))
            SpecialClasses.runAll()
        }
        "b" -> { /* 返回 */ }
    }
}

/**
 * 函数菜单
 */
fun runFunctionsMenu(scanner: Scanner) {
    println("""
        
        函数示例 (Functions)
        ─────────────────────────────────────────
        1. 普通函数    - 基本函数、默认参数、尾递归
        2. Lambda      - Lambda 表达式、高阶函数、闭包
        3. 扩展函数    - 扩展函数、扩展属性
        a. 运行全部
        b. 返回主菜单
    """.trimIndent())

    print("请选择: ")
    when (scanner.nextLine().trim().lowercase()) {
        "1" -> {
            println("\n" + "═".repeat(60))
            Functions.runAll()
        }
        "2" -> {
            println("\n" + "═".repeat(60))
            Lambdas.runAll()
        }
        "3" -> {
            println("\n" + "═".repeat(60))
            Extensions.runAll()
        }
        "a" -> {
            println("\n" + "═".repeat(60))
            Functions.runAll()
            println("\n" + "═".repeat(60))
            Lambdas.runAll()
            println("\n" + "═".repeat(60))
            Extensions.runAll()
        }
        "b" -> { /* 返回 */ }
    }
}

/**
 * 集合菜单
 */
fun runCollectionsMenu(scanner: Scanner) {
    println("""
        
        集合示例 (Collections)
        ─────────────────────────────────────────
        1. 运行全部集合示例
        b. 返回主菜单
    """.trimIndent())

    print("请选择: ")
    when (scanner.nextLine().trim().lowercase()) {
        "1" -> {
            println("\n" + "═".repeat(60))
            Collections.runAll()
        }
        "b" -> { /* 返回 */ }
    }
}

/**
 * 空安全示例
 */
fun runNullSafetyExamples() {
    println("\n" + "═".repeat(60))
    NullSafety.runAll()
}

/**
 * 高级特性菜单
 */
fun runAdvancedMenu(scanner: Scanner) {
    println("""
        
        高级特性示例 (Advanced Features)
        ─────────────────────────────────────────
        1. 内联函数    - inline、reified、noinline
        2. 泛型        - 泛型类、泛型函数、型变
        3. 异常处理    - try-catch、自定义异常、runCatching
        a. 运行全部
        b. 返回主菜单
    """.trimIndent())

    print("请选择: ")
    when (scanner.nextLine().trim().lowercase()) {
        "1" -> {
            println("\n" + "═".repeat(60))
            InlineFunctions.runAll()
        }
        "2" -> {
            println("\n" + "═".repeat(60))
            Generics.runAll()
        }
        "3" -> {
            println("\n" + "═".repeat(60))
            Exceptions.runAll()
        }
        "a" -> {
            println("\n" + "═".repeat(60))
            InlineFunctions.runAll()
            println("\n" + "═".repeat(60))
            Generics.runAll()
            println("\n" + "═".repeat(60))
            Exceptions.runAll()
        }
        "b" -> { /* 返回 */ }
    }
}

/**
 * 运行所有示例
 */
fun runAllExamples() {
    println("\n" + "═".repeat(60))
    println("运行所有示例...")
    println("═".repeat(60))

    println("\n>>> 1. 变量和类型")
    Variables.runAll()

    println("\n>>> 2. 控制流")
    ControlFlow.runAll()

    println("\n>>> 3. 数据类型")
    DataTypes.runAll()

    println("\n>>> 4. 类和继承")
    Classes.runAll()

    println("\n>>> 5. 特殊类")
    SpecialClasses.runAll()

    println("\n>>> 6. 普通函数")
    Functions.runAll()

    println("\n>>> 7. Lambda 表达式")
    Lambdas.runAll()

    println("\n>>> 8. 扩展函数")
    Extensions.runAll()

    println("\n>>> 9. 集合操作")
    Collections.runAll()

    println("\n>>> 10. 空安全")
    NullSafety.runAll()

    println("\n>>> 11. 内联函数")
    InlineFunctions.runAll()

    println("\n>>> 12. 泛型")
    Generics.runAll()

    println("\n>>> 13. 异常处理")
    Exceptions.runAll()

    println("\n" + "═".repeat(60))
    println("所有示例运行完成!")
    println("═".repeat(60))
}