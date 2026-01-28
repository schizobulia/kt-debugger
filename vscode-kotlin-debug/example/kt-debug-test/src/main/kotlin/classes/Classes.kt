package classes

/**
 * 类和继承示例
 * 用于演示调试器在各种类结构中的行为
 *
 * 调试建议:
 * - 在构造函数中设置断点
 * - 观察对象初始化顺序
 * - 查看继承层次中的属性
 */

// ==================== 普通类 ====================

/**
 * 带主构造函数的类
 */
class SimpleClass(val name: String, var age: Int) {
    
    init {
        println("  SimpleClass init: name=$name, age=$age")  // 断点: init 块
    }

    fun describe(): String {
        return "SimpleClass($name, $age)"    // 断点: 成员函数
    }
}

/**
 * 带次构造函数的类
 */
class Person2(val name: String) {
    var age: Int = 0
    var email: String = ""

    // 次构造函数必须委托给主构造函数
    constructor(name: String, age: Int) : this(name) {
        this.age = age                       // 断点: 次构造函数
    }

    constructor(name: String, age: Int, email: String) : this(name, age) {
        this.email = email
    }
}

// ==================== 继承 ====================

/**
 * 开放类 (可被继承)
 */
open class Animal(val name: String) {
    
    open fun makeSound() {
        println("  $name makes a sound")     // 断点: 基类方法
    }

    open val description: String
        get() = "Animal: $name"
}

/**
 * 继承类
 */
class Dog(name: String, val breed: String) : Animal(name) {
    
    override fun makeSound() {
        println("  $name (${breed}) barks: Woof!")  // 断点: 重写方法
    }

    override val description: String
        get() = "Dog: $name, Breed: $breed"  // 断点: 重写属性
}

class Cat(name: String) : Animal(name) {
    
    override fun makeSound() {
        println("  $name meows: Meow!")
    }
}

// ==================== 抽象类 ====================

/**
 * 抽象类
 */
abstract class Shape {
    abstract val area: Double                // 抽象属性
    abstract fun describe(): String          // 抽象方法

    fun printInfo() {                        // 具体方法
        println("  ${describe()}, Area: $area")  // 断点: 具体方法调用抽象成员
    }
}

class Circle(val radius: Double) : Shape() {
    override val area: Double
        get() = Math.PI * radius * radius    // 断点: 计算属性

    override fun describe(): String = "Circle with radius $radius"
}

class Rectangle(val width: Double, val height: Double) : Shape() {
    override val area: Double
        get() = width * height

    override fun describe(): String = "Rectangle ${width}x${height}"
}

// ==================== 接口 ====================

/**
 * 接口定义
 */
interface Drawable {
    fun draw()                               // 抽象方法
    
    val color: String                        // 抽象属性

    fun prepare() {                          // 带默认实现的方法
        println("  Preparing to draw in $color")  // 断点: 接口默认实现
    }
}

interface Resizable {
    fun resize(factor: Double)
}

/**
 * 实现多个接口
 */
class DrawableShape(
    override val color: String,
    private var size: Double
) : Drawable, Resizable {

    override fun draw() {
        println("  Drawing shape in $color, size: $size")  // 断点: 接口实现
    }

    override fun resize(factor: Double) {
        size *= factor
        println("  Resized to $size")
    }
}

// ==================== 类演示对象 ====================

object Classes {

    /**
     * 基本类演示
     */
    fun basicClasses() {
        println("=== 基本类 ===")

        // 创建简单类实例
        val simple = SimpleClass("Alice", 25)  // 断点: 对象创建
        println(simple.describe())

        simple.age = 26                        // 断点: 修改可变属性
        println("After modification: ${simple.describe()}")
    }

    /**
     * 次构造函数演示
     */
    fun secondaryConstructors() {
        println("\n=== 次构造函数 ===")

        val person1 = Person2("Alice")         // 断点: 主构造函数
        val person2 = Person2("Bob", 30)       // 断点: 次构造函数 1
        val person3 = Person2("Carol", 28, "carol@example.com")  // 断点: 次构造函数 2

        println("person1: ${person1.name}, ${person1.age}, ${person1.email}")
        println("person2: ${person2.name}, ${person2.age}, ${person2.email}")
        println("person3: ${person3.name}, ${person3.age}, ${person3.email}")
    }

    /**
     * 继承演示
     */
    fun inheritance() {
        println("\n=== 继承 ===")

        val animal = Animal("Generic Animal")
        val dog = Dog("Buddy", "Golden Retriever")
        val cat = Cat("Whiskers")

        animal.makeSound()                     // 断点: 基类方法
        dog.makeSound()                        // 断点: 重写方法
        cat.makeSound()

        println("animal.description: ${animal.description}")
        println("dog.description: ${dog.description}")  // 断点: 重写属性
    }

    /**
     * 多态演示
     */
    fun polymorphism() {
        println("\n=== 多态 ===")

        val animals: List<Animal> = listOf(
            Animal("Lion"),
            Dog("Rex", "German Shepherd"),
            Cat("Tom")
        )

        for (animal in animals) {
            animal.makeSound()                 // 断点: 多态调用
        }
    }

    /**
     * 抽象类演示
     */
    fun abstractClasses() {
        println("\n=== 抽象类 ===")

        val circle = Circle(5.0)               // 断点: 具体类实例化
        val rectangle = Rectangle(4.0, 6.0)

        circle.printInfo()                     // 断点: 通过具体方法调用抽象成员
        rectangle.printInfo()

        // 作为抽象类型使用
        val shapes: List<Shape> = listOf(circle, rectangle)
        println("总面积: ${shapes.sumOf { it.area }}")
    }

    /**
     * 接口演示
     */
    fun interfaces() {
        println("\n=== 接口 ===")

        val shape = DrawableShape("Red", 10.0)

        shape.prepare()                        // 断点: 接口默认方法
        shape.draw()                           // 断点: 接口实现方法
        shape.resize(1.5)                      // 断点: 另一个接口方法
        shape.draw()
    }

    /**
     * 类型检查和转换
     */
    fun typeCheckingAndCasting() {
        println("\n=== 类型检查和转换 ===")

        val animals: List<Animal> = listOf(
            Dog("Buddy", "Labrador"),
            Cat("Kitty"),
            Dog("Max", "Bulldog")
        )

        for (animal in animals) {
            when (animal) {
                is Dog -> {                    // 断点: 类型检查
                    println("${animal.name} is a ${animal.breed}")  // 智能转换
                }
                is Cat -> {
                    println("${animal.name} is a cat")
                }
            }
        }

        // 安全转换
        val first: Animal = animals[0]
        val dog = first as? Dog               // 断点: 安全转换
        println("Converted dog: ${dog?.breed}")

        val cat = first as? Cat
        println("Converted cat: $cat")         // null
    }

    /**
     * 运行所有类示例
     */
    fun runAll() {
        basicClasses()
        secondaryConstructors()
        inheritance()
        polymorphism()
        abstractClasses()
        interfaces()
        typeCheckingAndCasting()
    }
}
