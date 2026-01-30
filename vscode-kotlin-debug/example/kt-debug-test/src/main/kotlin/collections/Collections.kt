package collections

/**
 * 集合操作示例
 * 用于演示调试器如何显示和检查各种集合类型
 *
 * 调试建议:
 * - 在集合操作前后设置断点，观察集合变化
 * - 使用监视器查看集合内容
 * - 观察链式操作的中间结果
 */
object Collections {

    /**
     * List 操作演示
     */
    fun listOperations() {
        println("=== List 操作 ===")

        // 不可变 List
        val immutableList = listOf(1, 2, 3, 4, 5)  // 断点: 不可变列表
        println("immutableList: $immutableList")
        println("immutableList[2]: ${immutableList[2]}")
        println("immutableList.first(): ${immutableList.first()}")
        println("immutableList.last(): ${immutableList.last()}")

        // 可变 List
        val mutableList = mutableListOf("a", "b", "c")  // 断点: 可变列表
        println("\nmutableList 初始: $mutableList")

        mutableList.add("d")                   // 断点: 添加元素
        println("add('d'): $mutableList")

        mutableList.add(0, "z")                // 断点: 指定位置添加
        println("add(0, 'z'): $mutableList")

        mutableList.removeAt(0)                // 断点: 移除元素
        println("removeAt(0): $mutableList")

        mutableList[0] = "A"                   // 断点: 修改元素
        println("set [0] = 'A': $mutableList")

        // ArrayList
        val arrayList = arrayListOf(10, 20, 30)
        println("\narrayList: $arrayList")
    }

    /**
     * Set 操作演示
     */
    fun setOperations() {
        println("\n=== Set 操作 ===")

        // 不可变 Set
        val immutableSet = setOf(1, 2, 3, 3, 2, 1)  // 重复元素被忽略
        println("immutableSet: $immutableSet")      // 断点: Set 去重
        println("size: ${immutableSet.size}")       // 3

        // 可变 Set
        val mutableSet = mutableSetOf("apple", "banana")
        println("\nmutableSet 初始: $mutableSet")

        mutableSet.add("cherry")               // 断点: 添加元素
        println("add('cherry'): $mutableSet")

        mutableSet.add("apple")                // 重复元素
        println("add('apple') again: $mutableSet")  // 不变

        mutableSet.remove("banana")            // 断点: 移除元素
        println("remove('banana'): $mutableSet")

        // Set 操作
        val set1 = setOf(1, 2, 3, 4, 5)
        val set2 = setOf(4, 5, 6, 7, 8)

        println("\n集合运算:")
        println("set1: $set1")
        println("set2: $set2")
        println("交集 (intersect): ${set1.intersect(set2)}")  // 断点: 交集
        println("并集 (union): ${set1.union(set2)}")          // 断点: 并集
        println("差集 (subtract): ${set1.subtract(set2)}")    // 断点: 差集
    }

    /**
     * Map 操作演示
     */
    fun mapOperations() {
        println("\n=== Map 操作 ===")

        // 不可变 Map
        val immutableMap = mapOf(              // 断点: 不可变 Map
            "one" to 1,
            "two" to 2,
            "three" to 3
        )
        println("immutableMap: $immutableMap")
        println("immutableMap['two']: ${immutableMap["two"]}")
        println("keys: ${immutableMap.keys}")
        println("values: ${immutableMap.values}")

        // 可变 Map
        val mutableMap = mutableMapOf(         // 断点: 可变 Map
            "a" to "Alice",
            "b" to "Bob"
        )
        println("\nmutableMap 初始: $mutableMap")

        mutableMap["c"] = "Carol"              // 断点: 添加键值对
        println("添加 'c': $mutableMap")

        mutableMap["a"] = "Adam"               // 断点: 修改值
        println("修改 'a': $mutableMap")

        mutableMap.remove("b")                 // 断点: 移除键值对
        println("移除 'b': $mutableMap")

        // 安全获取
        val value = mutableMap.getOrDefault("x", "Unknown")  // 断点: 默认值
        println("getOrDefault('x'): $value")

        // getOrPut
        val newValue = mutableMap.getOrPut("d") { "David" }  // 断点: getOrPut
        println("getOrPut('d'): $newValue, map: $mutableMap")
    }

    /**
     * 集合转换操作
     */
    fun transformations() {
        println("\n=== 集合转换 ===")

        val numbers = listOf(1, 2, 3, 4, 5)

        // map
        val squared = numbers.map { it * it }  // 断点: map 转换
        println("map (squared): $squared")

        // mapIndexed
        val indexed = numbers.mapIndexed { i, v -> "$i:$v" }  // 断点: mapIndexed
        println("mapIndexed: $indexed")

        // flatMap
        val nested = listOf(listOf(1, 2), listOf(3, 4), listOf(5))
        val flattened = nested.flatMap { it }  // 断点: flatMap
        println("flatMap: $flattened")

        // flatten
        val flattened2 = nested.flatten()
        println("flatten: $flattened2")

        // associate
        val names = listOf("Alice", "Bob", "Carol")
        val nameToLength = names.associateWith { it.length }  // 断点: associateWith
        println("associateWith: $nameToLength")

        val lengthToName = names.associateBy { it.length }    // 断点: associateBy
        println("associateBy: $lengthToName")

        // groupBy
        val words = listOf("apple", "ant", "bee", "banana", "cat")
        val grouped = words.groupBy { it.first() }  // 断点: groupBy
        println("groupBy (first char): $grouped")
    }

    /**
     * 过滤操作
     */
    fun filtering() {
        println("\n=== 过滤操作 ===")

        val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        // filter
        val evens = numbers.filter { it % 2 == 0 }  // 断点: filter
        println("filter (偶数): $evens")

        // filterNot
        val odds = numbers.filterNot { it % 2 == 0 }  // 断点: filterNot
        println("filterNot (奇数): $odds")

        // partition
        val (even, odd) = numbers.partition { it % 2 == 0 }  // 断点: partition
        println("partition: 偶数=$even, 奇数=$odd")

        // take/drop
        val first3 = numbers.take(3)           // 断点: take
        val withoutFirst3 = numbers.drop(3)    // 断点: drop
        println("take(3): $first3")
        println("drop(3): $withoutFirst3")

        // takeWhile/dropWhile
        val takeWhile = numbers.takeWhile { it < 5 }  // 断点: takeWhile
        val dropWhile = numbers.dropWhile { it < 5 }  // 断点: dropWhile
        println("takeWhile (<5): $takeWhile")
        println("dropWhile (<5): $dropWhile")

        // distinct
        val withDuplicates = listOf(1, 2, 2, 3, 3, 3, 4)
        val distinct = withDuplicates.distinct()  // 断点: distinct
        println("distinct: $distinct")

        // filterNotNull
        val withNulls: List<Int?> = listOf(1, null, 2, null, 3)
        val nonNulls = withNulls.filterNotNull()  // 断点: filterNotNull
        println("filterNotNull: $nonNulls")
    }

    /**
     * 聚合操作
     */
    fun aggregations() {
        println("\n=== 聚合操作 ===")

        val numbers = listOf(5, 2, 8, 1, 9, 3, 7, 4, 6)

        // 基本聚合
        println("sum: ${numbers.sum()}")           // 断点: sum
        println("average: ${numbers.average()}")   // 断点: average
        println("max: ${numbers.maxOrNull()}")     // 断点: max
        println("min: ${numbers.minOrNull()}")     // 断点: min
        println("count: ${numbers.count()}")

        // reduce
        val product = numbers.reduce { acc, n -> acc * n }  // 断点: reduce
        println("reduce (product): $product")

        // fold
        val sumWith100 = numbers.fold(100) { acc, n -> acc + n }  // 断点: fold
        println("fold (sum with 100): $sumWith100")

        // runningFold (累积)
        val running = numbers.take(5).runningFold(0) { acc, n -> acc + n }  // 断点: runningFold
        println("runningFold: $running")

        // 条件聚合
        val countEven = numbers.count { it % 2 == 0 }
        println("count (偶数): $countEven")

        val sumOfEvens = numbers.filter { it % 2 == 0 }.sum()
        println("sum (偶数): $sumOfEvens")

        // maxBy/minBy
        val words = listOf("apple", "a", "banana", "kiwi")
        val longest = words.maxByOrNull { it.length }  // 断点: maxBy
        val shortest = words.minByOrNull { it.length }
        println("longest word: $longest")
        println("shortest word: $shortest")
    }

    /**
     * 排序操作
     */
    fun sorting() {
        println("\n=== 排序操作 ===")

        val numbers = listOf(5, 2, 8, 1, 9, 3)

        // 基本排序
        val sorted = numbers.sorted()          // 断点: sorted
        println("sorted: $sorted")

        val sortedDesc = numbers.sortedDescending()  // 断点: sortedDescending
        println("sortedDescending: $sortedDesc")

        // sortedBy
        val words = listOf("banana", "apple", "kiwi", "cherry")
        val byLength = words.sortedBy { it.length }  // 断点: sortedBy
        println("sortedBy (length): $byLength")

        val byLengthDesc = words.sortedByDescending { it.length }
        println("sortedByDescending (length): $byLengthDesc")

        // 自定义比较器
        val custom = words.sortedWith(
            compareBy<String> { it.length }.thenBy { it }
        )  // 断点: sortedWith
        println("sortedWith (length then alpha): $custom")

        // reversed
        val reversed = numbers.reversed()      // 断点: reversed
        println("reversed: $reversed")

        // shuffled
        val shuffled = numbers.shuffled()      // 断点: shuffled (随机)
        println("shuffled: $shuffled")
    }

    /**
     * 查找操作
     */
    fun finding() {
        println("\n=== 查找操作 ===")

        val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)

        // find
        val firstEven = numbers.find { it % 2 == 0 }  // 断点: find
        println("find (first even): $firstEven")

        val firstGreaterThan10 = numbers.find { it > 10 }
        println("find (>10): $firstGreaterThan10")   // null

        // first/last
        val first = numbers.first { it > 3 }   // 断点: first
        println("first (>3): $first")

        val last = numbers.last { it < 7 }     // 断点: last
        println("last (<7): $last")

        // indexOf
        val index = numbers.indexOf(5)         // 断点: indexOf
        println("indexOf(5): $index")

        // any/all/none
        val anyEven = numbers.any { it % 2 == 0 }  // 断点: any
        val allPositive = numbers.all { it > 0 }   // 断点: all
        val noneNegative = numbers.none { it < 0 } // 断点: none
        println("any (even): $anyEven")
        println("all (positive): $allPositive")
        println("none (negative): $noneNegative")

        // contains
        val contains5 = 5 in numbers           // 断点: contains
        println("5 in numbers: $contains5")
    }

    /**
     * 运行所有集合示例
     */
    fun runAll() {
        listOperations()
        setOperations()
        mapOperations()
        transformations()
        filtering()
        aggregations()
        sorting()
        finding()
    }
}
