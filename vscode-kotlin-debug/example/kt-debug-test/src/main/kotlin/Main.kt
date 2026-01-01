import java.util.Scanner

fun main() {
    // 创建 Scanner 实例，关联标准输入流
    val scanner = Scanner(System.`in`)

    // 1. 获取字符串输入
    print("请输入你的姓名：")
    val name = scanner.nextLine()  // 读取整行（包含空格）

    // 2. 获取整数输入
    print("请输入你的年龄：")
    val age = scanner.nextInt()    // 读取整数（注意：nextInt() 不会消费换行符）
    scanner.nextLine()  // 消费掉 nextInt() 遗留的换行符，避免影响后续 nextLine()

    // 3. 获取浮点数输入
    print("请输入你的身高（米）：")
    val height = scanner.nextDouble()

    // 输出结果
    println("\n===== 输入信息汇总 =====")
    println("姓名：$name")
    println("年龄：$age 岁")
    println("身高：$height 米")

    // 关闭 Scanner（避免资源泄漏）
    scanner.close()
}