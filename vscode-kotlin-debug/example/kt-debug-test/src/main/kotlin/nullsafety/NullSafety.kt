package nullsafety

/**
 * 空安全示例
 * 用于演示调试器如何处理可空类型和空安全操作符
 *
 * 调试建议:
 * - 观察可空类型变量的值
 * - 跟踪安全调用链的执行
 * - 设置断点在 Elvis 操作符处观察默认值
 */
object NullSafety {

    /**
     * 可空类型基础
     */
    fun nullableTypes() {
        println("=== 可空类型 ===")

        // 非空类型
        var nonNullString: String = "Hello"    // 断点: 非空类型
        println("nonNullString: $nonNullString")

        // 可空类型
        var nullableString: String? = "World"  // 断点: 可空类型有值
        println("nullableString: $nullableString")

        nullableString = null                  // 断点: 可空类型为 null
        println("nullableString (null): $nullableString")

        // 可空类型不能直接调用方法
        // nullableString.length  // 编译错误!
    }

    /**
     * 安全调用操作符 (?.)
     */
    fun safeCall() {
        println("\n=== 安全调用操作符 (?.) ===")

        val nullableString: String? = "Hello, Kotlin"
        val nullString: String? = null

        // 安全调用 - 有值
        val length1 = nullableString?.length   // 断点: 安全调用有值
        println("nullableString?.length: $length1")

        // 安全调用 - 为 null
        val length2 = nullString?.length       // 断点: 安全调用为 null
        println("nullString?.length: $length2")  // null

        // 链式安全调用
        val text: String? = "  Hello World  "
        val trimmedLength = text?.trim()?.length  // 断点: 链式安全调用
        println("text?.trim()?.length: $trimmedLength")

        val nullText: String? = null
        val nullResult = nullText?.trim()?.length  // 断点: 链式调用遇到 null
        println("nullText?.trim()?.length: $nullResult")
    }

    /**
     * Elvis 操作符 (?:)
     */
    fun elvisOperator() {
        println("\n=== Elvis 操作符 (?:) ===")

        val nullableString: String? = null
        val nonNullString: String? = "Hello"

        // Elvis 提供默认值
        val result1 = nullableString ?: "默认值"  // 断点: 使用默认值
        println("nullableString ?: '默认值': $result1")

        val result2 = nonNullString ?: "默认值"   // 断点: 不使用默认值
        println("nonNullString ?: '默认值': $result2")

        // Elvis 结合安全调用
        val length1 = nullableString?.length ?: 0  // 断点: 组合操作
        println("nullableString?.length ?: 0: $length1")

        val length2 = nonNullString?.length ?: 0
        println("nonNullString?.length ?: 0: $length2")

        // Elvis 抛出异常
        val name: String? = null
        try {
            val result = name ?: throw IllegalArgumentException("Name cannot be null")
        } catch (e: IllegalArgumentException) {
            println("捕获异常: ${e.message}")      // 断点: 异常处理
        }

        // Elvis 用于提前返回 (模拟)
        val user = getUserOrNull(null)
        println("user: $user")
    }

    private fun getUserOrNull(input: String?): String {
        val processedInput = input ?: return "No input provided"  // 断点: Elvis 提前返回
        return "Processed: $processedInput"
    }

    /**
     * 非空断言 (!!)
     */
    fun nonNullAssertion() {
        println("\n=== 非空断言 (!!) ===")

        val nullableString: String? = "Hello"

        // 非空断言 - 有值
        val length = nullableString!!.length   // 断点: 非空断言成功
        println("nullableString!!.length: $length")

        // 非空断言 - 为 null 会抛异常
        val nullString: String? = null
        try {
            val crashLength = nullString!!.length  // 断点: 非空断言失败
        } catch (e: NullPointerException) {
            println("捕获 NullPointerException: ${e.message}")
        }
    }

    /**
     * 安全类型转换 (as?)
     */
    fun safeCast() {
        println("\n=== 安全类型转换 (as?) ===")

        val obj1: Any = "Hello"
        val obj2: Any = 123

        // 安全转换 - 成功
        val str1 = obj1 as? String             // 断点: 安全转换成功
        println("obj1 as? String: $str1")

        // 安全转换 - 失败 (返回 null)
        val str2 = obj2 as? String             // 断点: 安全转换失败
        println("obj2 as? String: $str2")      // null

        // 结合 Elvis
        val str3 = obj2 as? String ?: "默认字符串"
        println("obj2 as? String ?: '默认字符串': $str3")
    }

    /**
     * let 与空安全
     */
    fun letWithNullSafety() {
        println("\n=== let 与空安全 ===")

        val nullableString: String? = "Hello"
        val nullString: String? = null

        // let 只在非空时执行
        nullableString?.let {                  // 断点: let 执行
            println("nullableString.let: 长度是 ${it.length}")
        }

        nullString?.let {                      // 断点: let 不执行
            println("这行不会执行")
        }
        println("nullString?.let 跳过了")

        // let 的返回值
        val result = nullableString?.let {
            it.uppercase()                     // 断点: let 转换
        } ?: "无值"
        println("let 结果: $result")

        // also 与空安全
        val length = nullableString?.also {
            println("also: 正在处理 '$it'")    // 断点: also 副作用
        }?.length
        println("length: $length")
    }

    /**
     * 集合中的空安全
     */
    fun nullSafetyInCollections() {
        println("\n=== 集合中的空安全 ===")

        // 可空元素列表
        val nullableList: List<String?> = listOf("a", null, "b", null, "c")
        println("nullableList: $nullableList")

        // filterNotNull
        val nonNullList = nullableList.filterNotNull()  // 断点: 过滤 null
        println("filterNotNull: $nonNullList")

        // mapNotNull
        val lengths = nullableList.mapNotNull { it?.length }  // 断点: mapNotNull
        println("mapNotNull (lengths): $lengths")

        // 可空集合
        val maybeList: List<String>? = listOf("x", "y", "z")
        val nullList: List<String>? = null

        println("\nmaybeList?.size: ${maybeList?.size}")
        println("nullList?.size: ${nullList?.size}")

        // orEmpty
        val safeList1 = maybeList.orEmpty()    // 断点: orEmpty 有值
        val safeList2 = nullList.orEmpty()     // 断点: orEmpty 为 null
        println("maybeList.orEmpty(): $safeList1")
        println("nullList.orEmpty(): $safeList2")  // 空列表

        // firstOrNull
        val first = nullableList.firstOrNull { it != null }  // 断点: firstOrNull
        println("firstOrNull (非null): $first")
    }

    /**
     * 可空类型与函数
     */
    fun nullableWithFunctions() {
        println("\n=== 可空类型与函数 ===")

        // 返回可空类型的函数
        val user1 = findUser("Alice")          // 断点: 返回非 null
        val user2 = findUser("Unknown")        // 断点: 返回 null

        println("findUser('Alice'): $user1")
        println("findUser('Unknown'): $user2")

        // 接受可空参数的函数
        greetUser(user1)                       // 断点: 非空参数
        greetUser(user2)                       // 断点: 空参数

        // 可空函数类型
        val action: ((String) -> Unit)? = { println("  Action: $it") }
        action?.invoke("test")                 // 断点: 调用可空函数

        val nullAction: ((String) -> Unit)? = null
        nullAction?.invoke("test")             // 断点: 不调用
        println("nullAction?.invoke 跳过了")
    }

    private fun findUser(name: String): String? {
        return if (name == "Alice") "User: Alice" else null
    }

    private fun greetUser(user: String?) {
        user?.let {
            println("  Hello, $it!")
        } ?: println("  No user to greet")
    }

    /**
     * 延迟初始化 (lateinit)
     */
    fun lateinitDemo() {
        println("\n=== 延迟初始化 (lateinit) ===")

        val demo = LateinitDemo()

        // 检查是否已初始化
        println("isInitialized: ${demo.isNameInitialized()}")  // 断点: 检查初始化

        try {
            demo.printName()  // 未初始化，会抛异常
        } catch (e: UninitializedPropertyAccessException) {
            println("捕获异常: lateinit property has not been initialized")
        }

        // 初始化
        demo.initName("Kotlin")                // 断点: 初始化
        println("isInitialized: ${demo.isNameInitialized()}")
        demo.printName()
    }

    /**
     * 运行所有空安全示例
     */
    fun runAll() {
        nullableTypes()
        safeCall()
        elvisOperator()
        nonNullAssertion()
        safeCast()
        letWithNullSafety()
        nullSafetyInCollections()
        nullableWithFunctions()
        lateinitDemo()
    }
}

/**
 * 延迟初始化演示类
 */
class LateinitDemo {
    lateinit var name: String

    fun isNameInitialized() = ::name.isInitialized

    fun initName(value: String) {
        name = value
    }

    fun printName() {
        println("  Name: $name")
    }
}
