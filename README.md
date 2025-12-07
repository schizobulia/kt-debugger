# Kotlin Debugger 使用教程

## 目录

1. [简介](#简介)
2. [安装与构建](#安装与构建)
3. [快速开始](#快速开始)
4. [命令参考](#命令参考)
5. [调试示例](#调试示例)
6. [高级功能](#高级功能)
7. [常见问题](#常见问题)

---

## 简介

Kotlin Debugger 是一个独立的命令行调试器，专门用于调试 Kotlin/JVM 程序。它可以脱离 IntelliJ IDEA 运行，提供：

- 断点设置与管理
- 栈帧查看与导航
- 变量查看
- 线程管理
- Kotlin 特性支持（内联函数、Lambda 等）

### 系统要求

- JDK 11 或更高版本
- Gradle 8.x（用于构建）

---

## 安装与构建

### 1. 构建调试器

```bash
cd kt-debug

# 创建 Gradle Wrapper（如果还没有）
gradle wrapper --gradle-version 8.10

# 构建 fat jar
./gradlew fatJar

# 构建产物位置
ls -la build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar
```

### 2. 创建启动脚本（可选）

```bash
# 创建便捷启动脚本
cat > kdb << 'EOF'
#!/bin/bash
java -jar kt-debug/build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar "$@"
EOF
chmod +x kdb
sudo mv kdb /usr/local/bin/
```

现在可以直接使用 `kdb` 命令启动调试器。

---

## 快速开始

### 方式一：Attach 模式（推荐）

这种方式先启动目标程序，再用调试器连接。

**步骤 1：启动目标程序并开启调试端口**

```bash
# 使用 -agentlib 参数启动 Java 程序
java '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005' test-program/InteractiveTest.jar
```

参数说明：
- `transport=dt_socket`：使用 socket 连接
- `server=y`：作为调试服务器
- `suspend=y`：启动后暂停，等待调试器连接
- `address=*:5005`：监听 5005 端口

**步骤 2：启动调试器并连接**

```bash
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar

# 在调试器中连接
(kdb) attach localhost:5005
```

### 方式二：Launch 模式

让调试器直接启动目标程序。

```bash
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar

# 在调试器中启动程序
(kdb) run MainKt -cp /path/to/classes
```

---

## 命令参考

### 会话管理

| 命令 | 别名 | 说明 | 示例 |
|------|------|------|------|
| `run <class> [-cp path]` | `r` | 启动程序调试 | `run MainKt -cp app.jar` |
| `attach <host>:<port>` | - | 连接到远程 JVM | `attach localhost:5005` |
| `quit` | `q` | 退出调试器 | `quit` |
| `help` | `h`, `?` | 显示帮助 | `help` |
| `status` | - | 显示会话状态 | `status` |

### 断点管理

| 命令 | 别名 | 说明 | 示例 |
|------|------|------|------|
| `break <file>:<line>` | `b` | 设置断点 | `b Main.kt:10` |
| `delete <id>` | `d` | 删除断点 | `d 1` |
| `list` | `l` | 列出所有断点 | `list` |
| `enable <id>` | - | 启用断点 | `enable 1` |
| `disable <id>` | - | 禁用断点 | `disable 1` |

### 执行控制

| 命令 | 别名 | 说明 | 示例 |
|------|------|------|------|
| `continue` | `c` | 继续执行 | `c` |
| `step` | `s` | 单步进入 (TODO) | `s` |
| `next` | `n` | 单步跳过 (TODO) | `n` |
| `finish` | `f` | 执行到返回 (TODO) | `f` |

### 栈帧导航

| 命令 | 别名 | 说明 | 示例 |
|------|------|------|------|
| `backtrace` | `bt`, `where` | 显示调用栈 | `bt` |
| `frame <n>` | `fr` | 切换到第 n 帧 | `fr 2` |
| `up` | - | 向上移动一帧 | `up` |
| `down` | - | 向下移动一帧 | `down` |

### 变量查看

| 命令 | 别名 | 说明 | 示例 |
|------|------|------|------|
| `locals` | - | 显示局部变量 | `locals` |
| `print <var>` | `p` | 打印变量值 | `p myVar` |

### 线程管理

| 命令 | 别名 | 说明 | 示例 |
|------|------|------|------|
| `threads` | - | 列出所有线程 | `threads` |
| `thread <id>` | `t` | 切换到指定线程 | `t 1` |

---

## 调试示例

### 示例 1：基本调试流程

我们使用项目自带的测试程序进行演示。

**1. 启动测试程序（开启调试模式）**

打开终端 1：
```bash
cd kt-debug/test-program
./run-debug.sh
```

输出：
```
Starting InteractiveTest with debug enabled on port 5005
Use: attach localhost:5005

Listening for transport dt_socket at address: 5005
```

**2. 启动调试器并连接**

打开终端 2：
```bash
cd kt-debug
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar
```

```
╔═══════════════════════════════════════════╗
║         Kotlin Debugger v1.0.0            ║
║     Type 'help' for available commands    ║
╚═══════════════════════════════════════════╝

(kdb) attach localhost:5005
Attached to localhost:5005
```

**3. 设置断点**

```
(kdb) b InteractiveTest.kt:65
Breakpoint 1 set at InteractiveTest.kt:65

(kdb) b InteractiveTest.kt:67
Breakpoint 2 set at InteractiveTest.kt:67

(kdb) list
ID  Location                   Status   Condition
--  ------------------------   -------  ---------
1   InteractiveTest.kt:65      enabled
2   InteractiveTest.kt:67      enabled
```

**4. 继续执行**

```
(kdb) c
Continuing...
```

此时终端 1 的测试程序开始运行，显示菜单：
```
=== Kotlin Debug Test Program ===
Commands: calc, list, random, inline, lambda, loop, quit

>
```

**5. 触发断点**

在终端 1 输入 `calc`，触发断点：

终端 2 显示：
```
Hit breakpoint 1 at InteractiveTest.kt:65
```

**6. 查看栈帧**

```
(kdb) bt
→ #0  InteractiveTestKt.testCalculation(InteractiveTest.kt:65)
  #1  InteractiveTestKt.main(InteractiveTest.kt:22)
  #2  InteractiveTestKt.main(InteractiveTest.kt)
```

**7. 查看变量**

```
(kdb) locals
Local Variables:
  x: int = 42
  y: int = 10

(kdb) p x
x: int = 42

(kdb) p y
y: int = 10
```

**8. 继续到下一个断点**

```
(kdb) c
Continuing...
Hit breakpoint 2 at InteractiveTest.kt:67

(kdb) locals
Local Variables:
  x: int = 42
  y: int = 10
  sum: int = 52
  product: int = 420
```

**9. 退出调试**

```
(kdb) c
Continuing...

(kdb) quit
Goodbye!
```

---

### 示例 2：调试循环

```
(kdb) attach localhost:5005
Attached to localhost:5005

(kdb) b InteractiveTest.kt:140
Breakpoint 1 set at InteractiveTest.kt:140

(kdb) c
```

在测试程序中输入 `loop`：

```
(kdb)
Hit breakpoint 1 at InteractiveTest.kt:140

(kdb) locals
Local Variables:
  sum: int = 0
  i: int = 1

(kdb) c
Hit breakpoint 1 at InteractiveTest.kt:140

(kdb) locals
Local Variables:
  sum: int = 1
  i: int = 2

(kdb) c
Hit breakpoint 1 at InteractiveTest.kt:140

(kdb) p sum
sum: int = 3
```

---

### 示例 3：查看线程

```
(kdb) threads
ID   Name                  Status    State
--   ----                  ------    -----
*1   main                  running   suspended
2    Reference Handler     waiting   suspended
3    Finalizer             waiting   suspended
4    Signal Dispatcher     running   suspended
```

切换线程：
```
(kdb) thread 1
Switched to thread 1
```

---

## 高级功能

### Kotlin 特性支持

#### 内联函数调试

调试器支持通过 SMAP (Source Map) 正确显示内联函数的源代码位置：

```kotlin
inline fun inlineCalculate(a: Int, b: Int, operation: (Int, Int) -> Int): Int {
    val result = operation(a, b)  // 可以在这里设置断点
    return result
}
```

设置断点时，使用内联函数定义所在的源文件和行号：
```
(kdb) b InteractiveTest.kt:98
```

#### Lambda 调试

Lambda 表达式内部也可以设置断点：

```kotlin
items.forEach { item ->
    val upper = item.uppercase()  // 可以设置断点
    println("  $item -> $upper")
}
```

### 断点位置说明

| 位置类型 | 格式 | 示例 |
|---------|------|------|
| 文件:行号 | `file.kt:line` | `Main.kt:10` |
| 仅文件名 | 自动匹配 | `Main.kt:10` |
| 完整路径 | 支持 | `/path/to/Main.kt:10` |

---

## 常见问题

### Q: 连接失败 "Connection refused"

**原因**: 目标程序未启动或端口不正确

**解决**:
1. 确认目标程序已启动并监听端口
2. 检查端口号是否正确
3. 检查防火墙设置

### Q: 断点不触发

**原因**: 断点位置可能不正确

**解决**:
1. 确认源文件名正确（区分大小写）
2. 确认行号有可执行代码
3. 使用 `list` 命令查看断点状态

### Q: 看不到变量值

**原因**: 可能在优化后的代码中

**解决**:
1. 确保编译时包含调试信息 (`-g` 选项)
2. 检查变量是否在当前作用域

### Q: "No active debug session" 错误

**原因**: 未连接到目标程序

**解决**:
```
(kdb) attach localhost:5005
# 或
(kdb) run MainKt -cp your-app.jar
```

---

## 项目结构

```
kt-debug/
├── build.gradle.kts              # Gradle 构建配置
├── src/
│   ├── main/kotlin/
│   │   └── com/example/kotlindebugger/
│   │       ├── Main.kt           # 入口
│   │       ├── cli/              # CLI 交互
│   │       ├── core/             # 调试器核心
│   │       ├── common/           # 公共模块
│   │       └── kotlin/           # Kotlin 适配
│   └── test/kotlin/              # 测试代码
├── test-program/                 # 测试程序
│   ├── InteractiveTest.kt        # 交互式测试
│   ├── InteractiveTest.jar
│   ├── run.sh                    # 直接运行
│   └── run-debug.sh              # 调试模式运行
└── build/libs/
    └── kotlin-debugger-1.0-SNAPSHOT-all.jar
```

---

## 待实现功能

以下功能正在开发中：

- [ ] 单步执行 (step/next/finish)
- [ ] 表达式求值 (eval)
- [ ] 条件断点
- [ ] 源代码显示
- [ ] 协程调试支持
- [ ] 内联栈帧显示

---

## 反馈与贡献

如有问题或建议，欢迎提交 Issue 或 PR。
