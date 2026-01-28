package classes

/**
 * 数据类型演示
 * 用于演示调试器如何显示和检查各种Kotlin数据类型
 *
 * 调试建议:
 * - 在数据类实例化处设置断点，查看对象结构
 * - 使用监视器(Watch)查看对象的属性
 * - 观察自动生成的方法如 toString(), equals(), copy() 等
 */

// ==================== 数据类 (Data Class) ====================

/**
 * 简单数据类
 * 自动生成: equals(), hashCode(), toString(), copy(), componentN()
 */
data class Person(
    val name: String,
    val age: Int
)

/**
 * 带默认值的数据类
 */
data class User(
    val id: Long,
    val username: String,
    val email: String = "",
    val isActive: Boolean = true
)

/**
 * 嵌套数据类
 */
data class Address(
    val street: String,
    val city: String,
    val zipCode: String,
    val country: String = "China"
)

data class Employee(
    val id: Int,
    val name: String,
    val department: String,
    val address: Address,
    val salary: Double
)

/**
 * 带集合的数据类
 */
data class Team(
    val name: String,
    val members: List<Person>,
    val tags: Set<String> = emptySet()
)

/**
 * 泛型数据类
 */
data class ApiResult<T>(
    val success: Boolean,
    val data: T?,
    val errorMessage: String? = null
)

/**
 * 数据类型演示对象
 */
object DataTypes {

    /**
     * 数据类基本用法
     */
    fun dataClassBasics() {
        println("=== 数据类基本用法 ===")

        // 创建数据类实例
        val person1 = Person("Alice", 30)    // 断点: 查看数据类结构
        val person2 = Person("Bob", 25)

        println("person1 = $person1")        // 自动生成的 toString()
        println("person2 = $person2")

        // equals() 比较
        val person3 = Person("Alice", 30)
        println("person1 == person3: ${person1 == person3}")  // 断点: 值比较

        // hashCode()
        println("person1.hashCode() = ${person1.hashCode()}")
        println("person3.hashCode() = ${person3.hashCode()}")
    }

    /**
     * 数据类 copy() 方法
     */
    fun dataClassCopy() {
        println("\n=== 数据类 copy() 方法 ===")

        val original = User(
            id = 1,
            username = "john_doe",
            email = "john@example.com"
        )
        println("original = $original")       // 断点: 查看原始对象

        // 使用 copy() 创建修改后的副本
        val modified = original.copy(         // 断点: 查看 copy() 结果
            email = "john.doe@company.com",
            isActive = false
        )
        println("modified = $modified")

        // 原始对象保持不变
        println("original 未改变 = $original")  // 断点: 验证不可变性
    }

    /**
     * 数据类解构声明
     */
    fun dataClassDestructuring() {
        println("\n=== 数据类解构声明 ===")

        val person = Person("Charlie", 28)

        // 解构到变量 (使用 componentN() 函数)
        val (name, age) = person             // 断点: 解构操作
        println("name = $name, age = $age")

        // 在 for 循环中解构
        val people = listOf(
            Person("Alice", 30),
            Person("Bob", 25),
            Person("Carol", 35)
        )

        println("遍历并解构:")
        for ((n, a) in people) {             // 断点: 循环中的解构
            println("  $n is $a years old")
        }

        // 在 lambda 中解构
        val descriptions = people.map { (n, a) ->  // 断点: lambda 解构
            "$n ($a)"
        }
        println("descriptions = $descriptions")
    }

    /**
     * 嵌套数据类
     */
    fun nestedDataClass() {
        println("\n=== 嵌套数据类 ===")

        val address = Address(
            street = "123 Main St",
            city = "Beijing",
            zipCode = "100000"
        )

        val employee = Employee(              // 断点: 查看嵌套结构
            id = 1001,
            name = "Zhang Wei",
            department = "Engineering",
            address = address,
            salary = 50000.0
        )

        println("employee = $employee")

        // 访问嵌套属性
        println("employee.address.city = ${employee.address.city}")  // 断点: 嵌套访问

        // 使用 copy() 修改嵌套属性
        val relocatedEmployee = employee.copy(
            address = employee.address.copy(city = "Shanghai")
        )
        println("relocatedEmployee = $relocatedEmployee")  // 断点: 嵌套 copy
    }

    /**
     * 带集合的数据类
     */
    fun dataClassWithCollections() {
        println("\n=== 带集合的数据类 ===")

        val team = Team(
            name = "Alpha Team",
            members = listOf(
                Person("Alice", 30),
                Person("Bob", 25),
                Person("Carol", 28)
            ),
            tags = setOf("frontend", "mobile")
        )

        println("team = $team")               // 断点: 查看集合属性
        println("team.members.size = ${team.members.size}")
        println("team.members[0] = ${team.members[0]}")

        // 修改包含集合的数据类
        val expandedTeam = team.copy(
            members = team.members + Person("David", 32)
        )
        println("expandedTeam.members.size = ${expandedTeam.members.size}")
    }

    /**
     * 泛型数据类
     */
    fun genericDataClass() {
        println("\n=== 泛型数据类 ===")

        // 成功结果
        val successResult: ApiResult<String> = ApiResult(
            success = true,
            data = "操作成功!"
        )
        println("successResult = $successResult")  // 断点: 泛型数据类

        // 失败结果
        val errorResult: ApiResult<String> = ApiResult(
            success = false,
            data = null,
            errorMessage = "发生错误"
        )
        println("errorResult = $errorResult")

        // 不同类型的 ApiResult
        val intResult: ApiResult<Int> = ApiResult(
            success = true,
            data = 42
        )
        println("intResult = $intResult")

        val listResult: ApiResult<List<Person>> = ApiResult(
            success = true,
            data = listOf(Person("Alice", 30))
        )
        println("listResult = $listResult")    // 断点: 复杂泛型类型
    }

    /**
     * 数据类与普通类的对比
     */
    fun dataClassVsRegularClass() {
        println("\n=== 数据类 vs 普通类 ===")

        // 数据类
        val dataPerson1 = Person("Alice", 30)
        val dataPerson2 = Person("Alice", 30)

        // 普通类
        val regularPerson1 = RegularPerson("Alice", 30)
        val regularPerson2 = RegularPerson("Alice", 30)

        // equals 比较
        println("数据类 equals: ${dataPerson1 == dataPerson2}")     // true
        println("普通类 equals: ${regularPerson1 == regularPerson2}") // false

        // toString 比较
        println("数据类 toString: $dataPerson1")          // Person(name=Alice, age=30)
        println("普通类 toString: $regularPerson1")       // RegularPerson@hashcode
    }

    /**
     * 运行所有数据类型示例
     */
    fun runAll() {
        dataClassBasics()
        dataClassCopy()
        dataClassDestructuring()
        nestedDataClass()
        dataClassWithCollections()
        genericDataClass()
        dataClassVsRegularClass()
    }
}

/**
 * 普通类 (用于对比)
 */
class RegularPerson(
    val name: String,
    val age: Int
)
