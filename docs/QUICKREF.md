# Kotlin Debugger 快速参考

## 启动调试

```bash
# 方式1: Attach 模式（默认 suspend-on-attach，附加后 VM 立即暂停）
# 终端1 - 启动目标程序（suspend=y：等待调试器连接后再执行）
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -cp app.jar MainKt

# 终端2 - 连接调试器（VM 保持暂停，可先设置断点）
java -jar kotlin-debugger-1.0-SNAPSHOT-all.jar
(kdb) attach localhost:5005
# 输出: Attached to localhost:5005 (suspended)
# 输出: VM is suspended. Set breakpoints with 'break <file>:<line>', then use 'continue' to start execution.

# 也可附加到 suspend=n 启动的程序（kdb 会主动挂起 VM）
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -cp app.jar MainKt
(kdb) attach localhost:5005   # 仍会挂起 VM，等待用户操作

# 不挂起直接附加（原有行为，适合调试已运行的程序）
(kdb) attach localhost:5005 --no-suspend

# 方式2: Launch 模式
(kdb) run MainKt -cp app.jar
```

## 常用命令

| 命令 | 别名 | 说明 |
|------|------|------|
| **会话管理** | | |
| `run MainKt -cp path` | `r` | 启动调试目标 |
| `attach host:port` | - | 连接远程 JVM（默认挂起 VM） |
| `attach host:port --no-suspend` | - | 连接远程 JVM（不挂起，直接运行） |
| `status` | - | 显示调试状态 |
| `quit` | `q` | 退出调试器 |
| **断点管理** | | |
| `break file:line` | `b` | 设置断点 |
| `break file:line -c "expr"` | `b` | 设置条件断点 |
| `delete id` | `d` | 删除断点 |
| `list` | `l` | 列出断点 |
| `enable id` | - | 启用断点 |
| `disable id` | - | 禁用断点 |
| **执行控制** | | |
| `continue` | `c` | 继续执行 |
| `interrupt` | - | 中断执行 |
| `step` | `s` | 单步进入 |
| `next` | `n` | 单步跳过 |
| `finish` | `f` | 单步退出 |
| **栈帧查看** | | |
| `backtrace` | `bt`, `where` | 显示调用栈 |
| `frame n` | `fr` | 切换到第n帧 |
| `up` | - | 向上一帧 |
| `down` | - | 向下一帧 |
| **变量查看** | | |
| `locals` | `info locals` | 显示局部变量 |
| `print var` | `p` | 打印变量值 |
| **线程管理** | | |
| `threads` | - | 列出所有线程 |
| `thread id` | `t` | 切换到指定线程 |
| **源代码查看** | | |
| `source` | `src` | 显示当前源代码 |
| `source file.kt` | `src file.kt` | 显示指定文件 |
| `list n` | `l n` | 显示第n行上下文 |
| **帮助** | | |
| `help` | `h`, `?` | 显示帮助信息 |

## 典型调试流程

```
(kdb) attach localhost:5005     # 1. 连接（VM 立即暂停）
(kdb) b Main.kt:10              # 2. 在程序启动前设断点
(kdb) b Main.kt:25              # 3. 可设置多个断点
(kdb) c                         # 4. 开始执行，命中断点时停止
# ... 触发断点 ...
(kdb) bt                        # 5. 查看调用栈
(kdb) locals                    # 6. 查看变量
(kdb) p myVar                   # 7. 打印特定变量
(kdb) c                         # 8. 继续或退出
```

## Suspend-on-Attach 调试早期初始化代码

适用场景：调试 `main()` 起始位置、静态初始化块、框架启动逻辑等在进程启动初期执行的代码。

```bash
# 步骤 1：以 suspend=y 启动目标 JVM（JVM 启动后等待调试器）
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 \
     -cp build/libs/app.jar MainKt

# 步骤 2：连接 kdb（VM 自动保持暂停）
kdb attach localhost:5005
# Attached to localhost:5005 (suspended)
# VM is suspended. Set breakpoints with 'break <file>:<line>', then use 'continue' to start execution.

# 步骤 3：设置断点（此时 main() 还未执行任何代码）
(kdb) break Main.kt:5
(kdb) break Main.kt:20

# 步骤 4：开始执行
(kdb) continue
# Starting program execution...
# 触发断点后停止...
```

> 提示：若要跳过 suspend-on-attach，使用 `attach host:port --no-suspend`。

## 测试程序

```bash
# 启动测试程序 (调试模式)
cd test-program && ./run-debug.sh

# 测试命令: calc, list, random, inline, lambda, loop, eval, quit
```

## VSCode 调试控制台和监视器

### 表达式求值功能

在 VSCode 调试时，可以在**调试控制台**和**监视器**中输入表达式进行求值：

#### 支持的表达式类型

| 类型 | 示例 | 说明 |
|------|------|------|
| 简单变量 | `x`, `name`, `count` | 访问局部变量或字段 |
| 成员访问 | `person.name`, `obj.field` | 访问对象的字段 |
| 嵌套访问 | `a.b.c`, `person.address.city` | 多级成员访问 |
| 数组访问 | `arr[0]`, `matrix[1][2]` | 访问数组元素 |
| 方法调用 | `obj.toString()`, `list.size()` | 调用无参或有参方法 |
| 字面量 | `42`, `"hello"`, `true` | 整数、字符串、布尔值 |

#### 使用示例

1. **调试控制台 (Debug Console)**
   ```
   > person.name
   "Alice"
   > numbers[0] + numbers[1]
   30
   > calculator.add(10, 20)
   30
   ```

2. **监视器 (Watch)**
   添加监视表达式如：
   - `user.email`
   - `list.size()`
   - `array[index]`

#### 注意事项

- 方法调用会在目标 JVM 中执行，可能有副作用
- 复杂表达式（如算术运算）需要使用方法调用形式
- 访问已回收的对象会返回错误

## 条件断点

### VSCode 中使用条件断点

1. **设置条件断点**
   - 在行号左侧右键点击
   - 选择 "Add Conditional Breakpoint..."
   - 输入条件表达式

2. **支持的条件表达式**

| 类型 | 示例 | 说明 |
|------|------|------|
| 数值比较 | `i == 5`, `count > 10` | 相等、大于、小于等 |
| 字符串比较 | `name == "test"` | 字符串相等判断 |
| 布尔变量 | `isEnabled`, `!isDone` | 直接使用布尔变量 |
| 成员访问 | `person.age >= 30` | 访问对象属性 |
| 复合条件 | `x > 5 && y < 10` | 逻辑与/或 |
| 方法调用 | `list.isEmpty()` | 调用布尔返回方法 |
| null 检查 | `obj != null` | 判断是否为空 |

3. **示例场景**
   ```
   # 只在 i 等于 5 时停止
   i == 5
   
   # 只在 name 为 "Alice" 时停止  
   name == "Alice"
   
   # 只在年龄大于等于 30 时停止
   person.age >= 30
   
   # 复合条件
   x > 3 && y < 8
   
   # 列表为空时停止
   items.isEmpty()
   ```

4. **测试条件断点**
   运行测试程序，输入 `cond` 命令，然后在相应位置设置条件断点进行测试。
