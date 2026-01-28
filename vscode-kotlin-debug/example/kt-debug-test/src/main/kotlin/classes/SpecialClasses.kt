package classes

/**
 * 密封类、枚举类和对象示例
 * 用于演示调试器在特殊类类型中的行为
 *
 * 调试建议:
 * - 观察 when 表达式如何处理密封类
 * - 查看枚举值和属性
 * - 跟踪单例对象的状态
 */

// ==================== 枚举类 ====================

/**
 * 简单枚举
 */
enum class Direction {
    NORTH, SOUTH, EAST, WEST
}

/**
 * 带属性的枚举
 */
enum class Color(val rgb: Int, val displayName: String) {
    RED(0xFF0000, "红色"),
    GREEN(0x00FF00, "绿色"),
    BLUE(0x0000FF, "蓝色"),
    YELLOW(0xFFFF00, "黄色");

    fun toHex(): String = "#${rgb.toString(16).padStart(6, '0').uppercase()}"
}

/**
 * 带方法的枚举
 */
enum class Operation {
    ADD {
        override fun apply(a: Int, b: Int) = a + b
    },
    SUBTRACT {
        override fun apply(a: Int, b: Int) = a - b
    },
    MULTIPLY {
        override fun apply(a: Int, b: Int) = a * b
    },
    DIVIDE {
        override fun apply(a: Int, b: Int) = if (b != 0) a / b else 0
    };

    abstract fun apply(a: Int, b: Int): Int
}

// ==================== 密封类 ====================

/**
 * 密封类 - 限制继承层次
 */
sealed class ApiResponse<out T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResponse<Nothing>()
    object Loading : ApiResponse<Nothing>()
}

/**
 * 密封类 - 表示 UI 状态
 */
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Error(val exception: Throwable) : UiState()
}

/**
 * 密封类 - 表示表达式树
 */
sealed class Expr {
    data class Num(val value: Double) : Expr()
    data class Sum(val left: Expr, val right: Expr) : Expr()
    data class Mul(val left: Expr, val right: Expr) : Expr()
    object NotANumber : Expr()
}

// ==================== 对象声明和伴生对象 ====================

/**
 * 对象声明 (单例)
 */
object AppConfig {
    var appName: String = "Kotlin Debug Example"
    var version: String = "1.0.0"
    var debugMode: Boolean = true

    fun printConfig() {
        println("  App: $appName v$version (debug=$debugMode)")
    }
}

/**
 * 带伴生对象的类
 */
class User3 private constructor(val id: Int, val name: String) {
    
    companion object Factory {                 // 命名伴生对象
        private var nextId = 1

        fun create(name: String): User3 {
            return User3(nextId++, name)       // 断点: 工厂方法
        }

        fun fromId(id: Int): User3? {
            // 模拟从数据库查找
            return if (id > 0) User3(id, "User$id") else null
        }

        const val MAX_NAME_LENGTH = 50
    }

    override fun toString() = "User3(id=$id, name=$name)"
}

/**
 * 匿名对象演示类
 */
interface ClickListener {
    fun onClick()
    fun onLongClick(): Boolean
}

// ==================== 特殊类类型演示对象 ====================

object SpecialClasses {

    /**
     * 枚举类演示
     */
    fun enums() {
        println("=== 枚举类 ===")

        // 简单枚举
        val direction = Direction.NORTH        // 断点: 枚举值
        println("direction: $direction")
        println("direction.name: ${direction.name}")
        println("direction.ordinal: ${direction.ordinal}")

        // 遍历枚举
        println("All directions:")
        for (d in Direction.values()) {        // 断点: 遍历枚举
            println("  ${d.ordinal}: ${d.name}")
        }

        // 带属性的枚举
        println("\nColors with properties:")
        for (color in Color.values()) {
            println("  ${color.name}: ${color.displayName} (${color.toHex()})")
        }

        // 带方法的枚举
        println("\nOperations:")
        val a = 10
        val b = 3
        for (op in Operation.values()) {
            val result = op.apply(a, b)        // 断点: 枚举方法调用
            println("  $a ${op.name} $b = $result")
        }
    }

    /**
     * 密封类演示
     */
    fun sealedClasses() {
        println("\n=== 密封类 ===")

        // 创建不同的结果类型
        val results: List<ApiResponse<String>> = listOf(
            ApiResponse.Success("Data loaded successfully"),
            ApiResponse.Error("Network error", 404),
            ApiResponse.Loading
        )

        for (result in results) {
            handleResult(result)               // 断点: 密封类处理
        }

        // UI 状态
        println("\nUI States:")
        val states: List<UiState> = listOf(
            UiState.Idle,
            UiState.Loading,
            UiState.Success("Operation completed"),
            UiState.Error(RuntimeException("Something went wrong"))
        )

        for (state in states) {
            println("  ${describeState(state)}")  // 断点: 状态描述
        }
    }

    private fun handleResult(result: ApiResponse<String>) {
        val message = when (result) {          // 断点: when 穷尽检查
            is ApiResponse.Success -> "✓ Success: ${result.data}"
            is ApiResponse.Error -> "✗ Error [${result.code}]: ${result.message}"
            is ApiResponse.Loading -> "⟳ Loading..."
        }
        println("  $message")
    }

    private fun describeState(state: UiState): String {
        return when (state) {
            is UiState.Idle -> "Idle - waiting for action"
            is UiState.Loading -> "Loading - please wait"
            is UiState.Success -> "Success: ${state.message}"
            is UiState.Error -> "Error: ${state.exception.message}"
        }
    }

    /**
     * 表达式树演示
     */
    fun expressionTree() {
        println("\n=== 表达式树 (密封类) ===")

        // 构建表达式: (1 + 2) * 3
        val expr: Expr = Expr.Mul(
            Expr.Sum(Expr.Num(1.0), Expr.Num(2.0)),
            Expr.Num(3.0)
        )

        val result = evaluate(expr)            // 断点: 表达式求值
        println("(1 + 2) * 3 = $result")

        // 更复杂的表达式: ((2 + 3) * (4 + 5))
        val complexExpr = Expr.Mul(
            Expr.Sum(Expr.Num(2.0), Expr.Num(3.0)),
            Expr.Sum(Expr.Num(4.0), Expr.Num(5.0))
        )
        println("(2 + 3) * (4 + 5) = ${evaluate(complexExpr)}")
    }

    private fun evaluate(expr: Expr): Double {
        return when (expr) {                   // 断点: 递归求值
            is Expr.Num -> expr.value
            is Expr.Sum -> evaluate(expr.left) + evaluate(expr.right)
            is Expr.Mul -> evaluate(expr.left) * evaluate(expr.right)
            Expr.NotANumber -> Double.NaN
        }
    }

    /**
     * 对象声明演示
     */
    fun objectDeclarations() {
        println("\n=== 对象声明 (单例) ===")

        // 使用单例对象
        AppConfig.printConfig()                // 断点: 访问单例

        // 修改单例状态
        AppConfig.debugMode = false            // 断点: 修改单例状态
        AppConfig.version = "1.0.1"
        AppConfig.printConfig()

        // 单例引用相等
        val config1 = AppConfig
        val config2 = AppConfig
        println("config1 === config2: ${config1 === config2}")  // true
    }

    /**
     * 伴生对象演示
     */
    fun companionObjects() {
        println("\n=== 伴生对象 ===")

        // 使用工厂方法
        val user1 = User3.create("Alice")      // 断点: 调用伴生对象方法
        val user2 = User3.create("Bob")
        val user3 = User3.Factory.create("Carol")  // 显式使用伴生对象名

        println("user1: $user1")
        println("user2: $user2")
        println("user3: $user3")

        // 访问伴生对象常量
        println("MAX_NAME_LENGTH: ${User3.MAX_NAME_LENGTH}")

        // fromId 方法
        val found = User3.fromId(100)
        println("Found user: $found")
    }

    /**
     * 匿名对象演示
     */
    fun anonymousObjects() {
        println("\n=== 匿名对象 ===")

        // 创建匿名对象实现接口
        val listener = object : ClickListener {  // 断点: 匿名对象创建
            override fun onClick() {
                println("  Anonymous onClick called")
            }

            override fun onLongClick(): Boolean {
                println("  Anonymous onLongClick called")
                return true
            }
        }

        listener.onClick()                     // 断点: 调用匿名对象方法
        listener.onLongClick()

        // 带额外属性的匿名对象
        val counter = object {
            var count = 0
            fun increment() = count++
            fun get() = count
        }

        counter.increment()                    // 断点: 匿名对象状态变化
        counter.increment()
        println("Counter: ${counter.get()}")
    }

    /**
     * 运行所有特殊类示例
     */
    fun runAll() {
        enums()
        sealedClasses()
        expressionTree()
        objectDeclarations()
        companionObjects()
        anonymousObjects()
    }
}
