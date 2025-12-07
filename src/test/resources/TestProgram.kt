/**
 * 用于功能测试的简单程序
 * 这个程序会被调试器启动和调试
 */
fun main() {
    val result = testFunction()
    println("Result: $result")

    // Keep the program running for testing
    Thread.sleep(10000) // Sleep for 10 seconds
}

fun testFunction(): Int {
    val a = 10          // line 13
    val b = 20          // line 14
    val sum = a + b     // line 15
    return sum          // line 16
}
