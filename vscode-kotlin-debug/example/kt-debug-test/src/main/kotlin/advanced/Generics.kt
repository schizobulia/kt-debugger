package advanced

/**
 * 泛型示例
 * 用于演示调试器如何处理泛型类型
 *
 * 调试建议:
 * - 观察泛型参数的实际类型
 * - 跟踪泛型方法的调用
 * - 注意类型擦除的影响
 */

// ==================== 泛型类 ====================

/**
 * 简单泛型类
 */
class Box<T>(val value: T) {
    fun get(): T {
        return value                           // 断点: 泛型方法返回
    }

    override fun toString() = "Box($value)"
}

/**
 * 多类型参数泛型类
 */
class Pair2<A, B>(val first: A, val second: B) {
    fun swap(): Pair2<B, A> {
        return Pair2(second, first)            // 断点: 交换类型
    }

    override fun toString() = "Pair2($first, $second)"
}

/**
 * 泛型约束
 */
class NumberBox<T : Number>(val value: T) {
    fun toDouble(): Double {
        return value.toDouble()                // 断点: 使用约束方法
    }
}

/**
 * 泛型接口
 */
interface Repository<T> {
    fun getById(id: Int): T?
    fun save(item: T): Boolean
    fun getAll(): List<T>
}

/**
 * 实现泛型接口
 */
data class User4(val id: Int, val name: String)

class UserRepository : Repository<User4> {
    private val storage = mutableMapOf<Int, User4>()

    override fun getById(id: Int): User4? {
        return storage[id]                     // 断点: 泛型实现
    }

    override fun save(item: User4): Boolean {
        storage[item.id] = item
        return true
    }

    override fun getAll(): List<User4> {
        return storage.values.toList()
    }
}

// ==================== 型变 (Variance) ====================

/**
 * 协变 (out) - 只能作为输出
 */
interface Producer<out T> {
    fun produce(): T
}

class StringProducer : Producer<String> {
    override fun produce(): String {
        return "Produced String"               // 断点: 协变返回
    }
}

/**
 * 逆变 (in) - 只能作为输入
 */
interface Consumer<in T> {
    fun consume(item: T)
}

class AnyConsumer : Consumer<Any> {
    override fun consume(item: Any) {
        println("  Consumed: $item")           // 断点: 逆变消费
    }
}

/**
 * 不变 - 既可输入也可输出
 */
interface MutableBox<T> {
    fun get(): T
    fun set(value: T)
}

// ==================== 泛型演示对象 ====================

object Generics {

    /**
     * 基本泛型演示
     */
    fun basicGenerics() {
        println("=== 基本泛型 ===")

        // 泛型类实例化
        val intBox = Box(42)                   // 断点: 泛型实例化
        val stringBox = Box("Hello")
        val listBox = Box(listOf(1, 2, 3))

        println("intBox: $intBox, get: ${intBox.get()}")
        println("stringBox: $stringBox, get: ${stringBox.get()}")
        println("listBox: $listBox, get: ${listBox.get()}")

        // 多类型参数
        val pair = Pair2("name", 25)           // 断点: 多类型参数
        println("pair: $pair")
        println("pair.swap(): ${pair.swap()}")
    }

    /**
     * 泛型函数演示
     */
    fun genericFunctions() {
        println("\n=== 泛型函数 ===")

        // 调用泛型函数
        val first = firstOrNull(listOf(1, 2, 3))  // 断点: 泛型函数
        println("firstOrNull([1,2,3]): $first")

        val empty = firstOrNull<String>(emptyList())
        println("firstOrNull([]): $empty")

        // 泛型扩展函数
        val swapped = Pair("a", 1).swap2()     // 断点: 泛型扩展
        println("Pair('a', 1).swap2(): $swapped")

        // 多类型参数函数
        val transformed = transform2("Hello", String::length)  // 断点: 变换函数
        println("transform2('Hello', length): $transformed")
    }

    fun <T> firstOrNull(list: List<T>): T? {
        return if (list.isNotEmpty()) list[0] else null  // 断点: 泛型返回
    }

    fun <A, B> Pair<A, B>.swap2(): Pair<B, A> {
        return Pair(second, first)             // 断点: 泛型扩展实现
    }

    fun <T, R> transform2(value: T, transformer: (T) -> R): R {
        return transformer(value)              // 断点: 泛型变换
    }

    /**
     * 泛型约束演示
     */
    fun genericConstraints() {
        println("\n=== 泛型约束 ===")

        // 单一约束
        val intBox = NumberBox(42)             // 断点: Number 约束
        val doubleBox = NumberBox(3.14)

        println("intBox.toDouble(): ${intBox.toDouble()}")
        println("doubleBox.toDouble(): ${doubleBox.toDouble()}")

        // 多重约束
        val result = compareAndPrint("apple", "banana")  // 断点: 多重约束
        println("compareAndPrint result: $result")
    }

    // 多重约束：T 必须同时满足 Comparable 和 CharSequence
    fun <T> compareAndPrint(a: T, b: T): Int where T : Comparable<T>, T : CharSequence {
        println("  Comparing: '$a' (len=${a.length}) vs '$b' (len=${b.length})")
        return a.compareTo(b)                  // 断点: 使用约束方法
    }

    /**
     * 型变演示
     */
    fun variance() {
        println("\n=== 型变 ===")

        // 协变 (out)
        val stringProducer: Producer<String> = StringProducer()
        val anyProducer: Producer<Any> = stringProducer  // String 是 Any 的子类型
        println("anyProducer.produce(): ${anyProducer.produce()}")  // 断点: 协变

        // 逆变 (in)
        val anyConsumer: Consumer<Any> = AnyConsumer()
        val stringConsumer: Consumer<String> = anyConsumer  // 接受 Any，也能接受 String
        stringConsumer.consume("Hello")        // 断点: 逆变

        // 使用点型变
        val numbers: MutableList<Int> = mutableListOf(1, 2, 3)
        copyData(numbers, mutableListOf())     // 断点: 使用点型变
    }

    fun copyData(source: MutableList<out Any>, dest: MutableList<in Any>) {
        for (item in source) {
            dest.add(item)                     // 断点: 复制数据
        }
    }

    /**
     * 泛型与仓库模式
     */
    fun repositoryPattern() {
        println("\n=== 泛型仓库模式 ===")

        val userRepo = UserRepository()

        // 保存数据
        userRepo.save(User4(1, "Alice"))       // 断点: 保存操作
        userRepo.save(User4(2, "Bob"))
        userRepo.save(User4(3, "Carol"))

        // 查询数据
        val user1 = userRepo.getById(1)        // 断点: 查询操作
        println("getById(1): $user1")

        val user99 = userRepo.getById(99)
        println("getById(99): $user99")

        // 获取所有
        val allUsers = userRepo.getAll()       // 断点: 获取所有
        println("getAll(): $allUsers")
    }

    /**
     * 星投影演示
     */
    fun starProjection() {
        println("\n=== 星投影 ===")

        val boxes: List<Box<*>> = listOf(      // 断点: 星投影列表
            Box(42),
            Box("Hello"),
            Box(3.14)
        )

        for (box in boxes) {
            val value: Any? = box.get()   // 断点: 星投影获取值
            println("  Box value: $value (type: ${value?.javaClass?.simpleName})")
        }
    }

    /**
     * 运行所有泛型示例
     */
    fun runAll() {
        basicGenerics()
        genericFunctions()
        genericConstraints()
        variance()
        repositoryPattern()
        starProjection()
    }
}
