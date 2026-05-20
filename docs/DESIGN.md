# Kotlin CLI Debugger - 技术设计文档

## 项目概述

### 目标
创建一个独立的 Kotlin 命令行调试器，脱离 IntelliJ IDEA 环境运行，提供完整的调试功能。

### 核心特性
- 断点管理（行断点、条件断点）
- 单步执行（Step Over / Step Into / Step Out）
- 变量查看和表达式评估
- 栈帧导航
- 内联函数调试支持
- Lambda 调试支持
- 协程调试支持（可选）
- CLI 调试器
- 支持VSCode 调试适配器协议（DAP）

---

## 一、技术架构

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     CLI 交互层                               │
│  - 命令解析 (JLine3)                                         │
│  - 输出格式化 (Jansi/Picoli)                                 │
│  - 自动补全                                                  │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                  调试器核心层                                 │
│  - BreakpointManager (断点管理)                              │
│  - SteppingController (单步控制)                             │
│  - StackFrameManager (栈帧管理)                              │
│  - VariableInspector (变量查看)                              │
│  - ExpressionEvaluator (表达式评估)                          │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                 Kotlin 适配层                                 │
│  - KotlinPositionManager (位置映射)                          │
│  - SMAPParser (SMAP 解析)                                    │
│  - InlineStackFrameCalculator (内联栈帧)                     │
│  - KotlinClassNameResolver (类名解析)                        │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                    JDI 层                                    │
│  - VirtualMachine (JVM 连接)                                 │
│  - EventHandler (事件处理)                                   │
│  - JDIWrapper (安全封装)                                     │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 VSCode 扩展架构

```
vscode-kotlin-debug/
├── src/
│   └── extension.ts          # 扩展入口（activate / deactivate）
│       ├── KotlinDebugAdapterDescriptorFactory   # 启动 kotlin-debugger JAR
│       ├── KotlinDebugConfigurationProvider      # launch.json 配置提供者
│       ├── CoroutineViewProvider                 # 协程树视图
│       ├── KotlinDebugCodeLensProvider           # main / @Test CodeLens
│       ├── KotlinDebugHoverProvider              # 悬停变量值
│       └── KotlinInlineValuesProvider            # 内联值显示
└── package.json              # 语言贡献 (.kt/.kts)、命令、视图、配置定义
```

**协程视图刷新机制** (v0.3.2+):

扩展通过 `DebugAdapterTracker` 拦截 DAP 消息，在每次调试器暂停（断点命中、单步完成、异常等）时自动刷新协程视图：

```typescript
vscode.debug.registerDebugAdapterTrackerFactory('kotlin', {
    createDebugAdapterTracker(_session): vscode.DebugAdapterTracker {
        return {
            onDidSendMessage(message) {
                if (message.type === 'event' && message.event === 'stopped') {
                    coroutineViewProvider.fetchAndRefresh();
                }
            }
        };
    }
});
```

这比 `onDidChangeActiveDebugSession` 更精确，只在真正暂停时刷新，而非每次会话切换时刷新。

**Kotlin 语言贡献** (v0.3.2+):

`package.json` 中注册了 `.kt` / `.kts` 文件的语言定义，无需单独安装 Kotlin 语言扩展即可获得基本的文件关联：

```json
{
  "languages": [{
    "id": "kotlin",
    "aliases": ["Kotlin", "kotlin"],
    "extensions": [".kt", ".kts"],
    "mimetypes": ["text/x-kotlin"]
  }]
}
```

### 1.3 模块划分

```
kotlin-debugger/
├── debugger-core/           # 调试器核心
│   ├── jdi/                 # JDI 封装
│   ├── breakpoint/          # 断点管理
│   ├── stepping/            # 单步执行
│   ├── stack/               # 栈帧处理
│   └── variable/            # 变量查看
│
├── kotlin-adapter/          # Kotlin 适配
│   ├── position/            # 位置映射
│   ├── smap/                # SMAP 解析
│   ├── inline/              # 内联函数支持
│   └── coroutine/           # 协程支持（可选）
│
├── cli/                     # 命令行界面
│   ├── command/             # 命令实现
│   ├── completion/          # 自动补全
│   └── output/              # 输出格式化
│
└── common/                  # 公共模块
    ├── util/                # 工具类
    └── model/               # 数据模型
```

---

## 二、核心组件设计

### 2.1 JDI 连接管理

```kotlin
// 启动方式：启动新进程或附加到已有进程
sealed class DebugTarget {
    data class Launch(
        val mainClass: String,
        val classpath: List<String>,
        val jvmArgs: List<String> = emptyList(),
        val programArgs: List<String> = emptyList(),
        val workingDir: String? = null,          // 工作目录
        val env: Map<String, String> = emptyMap(), // 额外环境变量
        val suspend: Boolean = true               // 启动后是否先暂停
    ) : DebugTarget()

    data class Attach(
        val host: String = "localhost",
        val port: Int,
        val suspend: Boolean = true  // 附加后是否挂起 VM（suspend-on-attach）
    ) : DebugTarget()
}

class DebugSession(private val target: DebugTarget) {
    lateinit var vm: VirtualMachine
    private val eventHandler = EventHandler()

    fun start() { ... }
    fun stop() { ... }
    fun resume() { ... }
    fun suspend() { ... }
    fun terminate() { ... }                                            // 终止被调试进程
    fun getBreakpointLocationsForFile(file: String, start: Int, end: Int): List<Int>  // 有效断点行
    fun getLoadedSourceNames(): List<String>                           // 已加载的源文件名
    fun addMethodBreakpoint(className: String, methodName: String, condition: String? = null): Breakpoint
    fun clearMethodBreakpoints()
}
```

### 2.2 断点管理

```kotlin
sealed class Breakpoint {
    abstract val id: Int
    abstract val enabled: Boolean
    abstract val condition: String?

    data class LineBreakpoint(
        override val id: Int,
        val file: String,
        val line: Int,
        override val enabled: Boolean = true,
        override val condition: String? = null
    ) : Breakpoint()

    data class MethodBreakpoint(
        override val id: Int,
        val className: String,
        val methodName: String,
        override val enabled: Boolean = true,
        override val condition: String? = null
    ) : Breakpoint()
}

class BreakpointManager(private val session: DebugSession) {
    private val breakpoints = mutableMapOf<Int, Breakpoint>()

    fun addBreakpoint(file: String, line: Int, condition: String? = null): Breakpoint
    fun removeBreakpoint(id: Int): Boolean
    fun enableBreakpoint(id: Int): Boolean
    fun disableBreakpoint(id: Int): Boolean
    fun listBreakpoints(): List<Breakpoint>
}
```

### 2.3 位置管理 (Kotlin 核心)

```kotlin
data class SourcePosition(
    val file: String,
    val line: Int,
    val column: Int = 0
)

class KotlinPositionManager(
    private val smapCache: SMAPCache,
    private val sourceRoot: List<Path>
) {
    // 字节码位置 → 源代码位置
    fun getSourcePosition(location: Location): SourcePosition?

    // 源代码位置 → 字节码位置列表
    fun getLocations(position: SourcePosition): List<Location>

    // 获取内联函数的源位置（通过 SMAP）
    fun getInlinedSourcePosition(location: Location): List<SourcePosition>
}
```

### 2.4 SMAP 解析

```kotlin
// SMAP 结构
data class SMAP(val fileMappings: List<FileMapping>) {
    fun findRange(destLine: Int): RangeMapping?
}

data class FileMapping(
    val name: String,      // 源文件名
    val path: String       // 源文件路径
) {
    val lineMappings = mutableListOf<RangeMapping>()
}

data class RangeMapping(
    val source: Int,       // 源文件行号
    val dest: Int,         // 字节码行号
    val range: Int,        // 映射范围
    val parent: FileMapping
) {
    fun mapDestToSource(destLine: Int): Int
    fun mapSourceToDest(sourceLine: Int): Int
}

class SMAPParser {
    fun parse(smapString: String): SMAP?
}

class SMAPCache {
    private val cache = ConcurrentHashMap<String, SMAP>()

    fun getOrParse(className: String, classBytes: ByteArray): SMAP?
}
```

### 2.5 单步执行

```kotlin
enum class StepType {
    STEP_INTO,
    STEP_OVER,
    STEP_OUT
}

class SteppingController(
    private val session: DebugSession,
    private val positionManager: KotlinPositionManager
) {
    fun stepOver(thread: ThreadReference)
    fun stepInto(thread: ThreadReference)
    fun stepOut(thread: ThreadReference)

    // Kotlin 特有：处理内联函数边界
    private fun createKotlinStepRequest(
        thread: ThreadReference,
        stepType: StepType
    ): StepRequest
}
```

### 2.6 栈帧管理

```kotlin
data class StackFrameInfo(
    val index: Int,
    val location: SourcePosition?,
    val methodName: String,
    val className: String,
    val isInline: Boolean = false,     // 是否为内联函数帧
    val variables: List<VariableInfo>
)

data class VariableInfo(
    val name: String,
    val type: String,
    val value: String
)

class StackFrameManager(
    private val positionManager: KotlinPositionManager,
    private val inlineCalculator: InlineStackFrameCalculator
) {
    // 获取栈帧列表（包含内联函数虚拟帧）
    fun getStackFrames(thread: ThreadReference): List<StackFrameInfo>

    // 获取指定帧的变量
    fun getVariables(frame: StackFrame): List<VariableInfo>
}
```

### 2.7 内联栈帧计算

```kotlin
class InlineStackFrameCalculator(
    private val smapCache: SMAPCache
) {
    // 根据 SMAP 计算内联函数的虚拟栈帧
    fun calculateInlineFrames(
        realFrame: StackFrame,
        location: Location
    ): List<StackFrameInfo>
}
```

---

## 三、CLI 设计

### 3.1 命令列表

| 命令 | 缩写 | 描述 | 示例 |
|------|------|------|------|
| `run` | `r` | 启动调试 | `run MainKt` |
| `attach` | - | 附加到进程（默认挂起 VM） | `attach localhost:5005` |
| `attach --no-suspend` | - | 附加到进程（不挂起） | `attach localhost:5005 --no-suspend` |
| `breakpoint` | `b` | 设置断点 | `b Main.kt:10` |
| `delete` | `d` | 删除断点 | `d 1` |
| `list` | `l` | 列出断点 | `l` |
| `continue` | `c` | 继续执行 | `c` |
| `step` | `s` | Step Into | `s` |
| `next` | `n` | Step Over | `n` |
| `finish` | `f` | Step Out | `f` |
| `backtrace` | `bt` | 显示栈帧 | `bt` |
| `frame` | `fr` | 切换栈帧 | `fr 2` |
| `print` | `p` | 打印变量 | `p myVar` |
| `eval` | `e` | 表达式求值 | `e x + 1` |
| `locals` | - | 显示局部变量 | `locals` |
| `threads` | - | 显示线程 | `threads` |
| `thread` | `t` | 切换线程 | `t 1` |
| `source` | `src` | 显示源码 | `src` |
| `quit` | `q` | 退出 | `q` |
| `help` | `h` | 帮助 | `h` |

### 3.2 交互示例

```bash
$ kotlin-debugger run -cp app.jar MainKt

Kotlin Debugger v1.0.0
Type 'help' for available commands.

(kdb) b Main.kt:15
Breakpoint 1 set at Main.kt:15

(kdb) c
Running...
Hit breakpoint 1 at Main.kt:15: fun main()

(kdb) bt
#0  MainKt.main(Main.kt:15)
#1  [inline] calculate(Utils.kt:10)  <- 内联函数显示
#2  MainKt$main$1.invoke(Main.kt:20)

(kdb) locals
  x: Int = 42
  name: String = "hello"
  list: List<Int> = [1, 2, 3]

(kdb) p x
42

(kdb) e x * 2
84

(kdb) n
Main.kt:16: println(x)

(kdb) c
Program exited normally.

(kdb) q
```

### 3.3 界面增强

使用 ANSI 颜色和格式化提升可读性：

```
┌─ Breakpoint ────────────────────────────────────────────────┐
│ Hit breakpoint 1 at Main.kt:15                              │
└─────────────────────────────────────────────────────────────┘

   13│     val x = 10
   14│     val y = 20
→  15│     val result = calculate(x, y)  ← 当前行高亮
   16│     println(result)
   17│ }

┌─ Variables ─────────────────────────────────────────────────┐
│ x: Int = 10                                                  │
│ y: Int = 20                                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 四、关键难点及解决方案

### 4.1 内联函数调试

**问题**: 内联函数在编译时被展开到调用者中，导致字节码位置与源代码不匹配。

**解决方案**:
1. 解析 `.class` 文件中的 `SourceDebugExtension` 属性获取 SMAP
2. SMAP 包含内联函数原始行号到字节码行号的映射
3. 在断点和单步执行时使用 SMAP 恢复正确的源位置

```kotlin
// 从字节码获取 SMAP
fun extractSMAP(classBytes: ByteArray): String? {
    val reader = ClassReader(classBytes)
    var smap: String? = null
    reader.accept(object : ClassVisitor(ASM9) {
        override fun visitSource(source: String?, debug: String?) {
            smap = debug  // SourceDebugExtension
        }
    }, 0)
    return smap
}
```

### 4.2 Lambda 调试

**问题**: Lambda 编译成匿名类或独立方法，类名包含 `$lambda$` 等后缀。

**解决方案**:
1. 识别 Lambda 类名模式 (`$lambda$N`, `$Function$N`)
2. 通过 SMAP 或方法签名反向查找源代码位置

### 4.3 协程调试 (可选)

**问题**: 协程通过 `Continuation` 对象实现，栈迹不完整。

**解决方案**:
1. 使用 `kotlinx-coroutines-debug` 代理
2. 通过反射访问 `DebugProbesImpl` 获取完整协程栈
3. 合成虚拟栈帧显示给用户

### 4.4 表达式评估

**问题**: 需要在调试上下文中编译和执行 Kotlin 表达式。

**解决方案（简化版）**:
1. 将表达式包装为代码片段
2. 使用 Kotlin 编译器编译为字节码
3. 通过 JDI 加载并执行

**解决方案（简单版）**:
1. 仅支持简单变量查看和基本运算
2. 通过 JDI 直接读取变量值

---

## 五、技术依赖

### 5.1 核心依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDI (jdk.jdi) | JDK 11+ | JVM 调试接口 |
| ASM | 9.x | 字节码解析 (SMAP) |
| Kotlin Stdlib | 2.0+ | 基础库 |
| JLine3 | 3.x | CLI 交互 |
| Picocli | 4.x | 命令行参数解析 |

### 5.2 可选依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| kotlin-compiler | 2.0+ | 表达式编译 |
| kotlinx-coroutines-debug | 1.x | 协程调试 |
| Jansi | 2.x | ANSI 终端颜色 |

### 5.3 Gradle 配置

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.0"
    application
}

dependencies {
    // 核心
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.jline:jline:3.26.1")
    implementation("info.picocli:picocli:4.7.6")

    // 可选：表达式评估
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")

    // 可选：协程调试
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.8.0")

    // 测试
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.example.debugger.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.debugger.MainKt"
    }
    // 打包为 fat jar
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

---

## 六、开发路线图

### Phase 1: 基础框架 

- [ ] 项目结构搭建
- [ ] JDI 连接管理 (Launch/Attach)
- [ ] 事件循环和处理
- [ ] 基本断点 (行断点)
- [ ] 简单 CLI (run, breakpoint, continue, quit)

**交付物**: 能够启动程序、设置断点、触发断点

### Phase 2: 核心调试功能

- [ ] 单步执行 (Step Over/Into/Out)
- [ ] 栈帧查看
- [ ] 变量查看
- [ ] 线程管理

**交付物**: 完整的基础调试体验

### Phase 3: Kotlin 特性支持

- [ ] SMAP 解析和缓存
- [ ] 内联函数调试
- [ ] Lambda 识别和调试
- [ ] 虚拟栈帧计算

**交付物**: 支持 Kotlin 特有语法的调试

### Phase 4: 高级功能

- [x] 条件断点
- [ ] 表达式评估 (简化版)
- [ ] 协程调试支持
- [ ] CLI 增强 (颜色、自动补全)

**交付物**: 功能完整的调试器

**已实现的条件断点功能**:
1. `ConditionEvaluator` - 条件表达式求值器
   - 数值比较 (如: `i == 5`, `count > 10`)
   - 字符串比较 (如: `name == "test"`)
   - 布尔变量 (如: `isEnabled`, `!isDone`)
   - 成员访问 (如: `person.age >= 30`)
   - 复合条件 (如: `x > 5 && y < 10`)
   - 方法调用 (如: `list.isEmpty()`)
   - null 检查 (如: `obj != null`)
2. VSCode 条件断点 UI 集成
3. `supportsConditionalBreakpoints` 能力声明

### Phase 5: 适配器协议（DAP）

- [x] 模块创建和基础设施
- [x] 命令处理器框架
- [x] 测试基础设施
- [x] 支持 VSCode 图形化调试体验
- [x] **调试控制台表达式求值 (evaluate request)**
- [x] **监视器 (watch) 支持**
- [x] **命中次数断点 (hitCondition)**
- [x] **函数断点 (setFunctionBreakpoints)**
- [x] **终止请求 (terminate)**
- [x] **断点位置查询 (breakpointLocations)**
- [x] **已加载源文件列表 (loadedSources)**
- [x] **工作目录和环境变量 (workingDir / env)**

**已实现的DAP功能**:
1. `evaluate` 请求 - 支持调试控制台和监视器的表达式求值
   - 简单变量访问 (如: `x`, `name`)
   - 成员字段访问 (如: `person.name`, `obj.field`)
   - 数组元素访问 (如: `arr[0]`, `matrix[1][2]`)
   - 方法调用 (如: `obj.toString()`, `list.size()`)
   - 字面量 (如: `42`, `"hello"`, `true`)
2. `supportsEvaluateForHovers` - 支持鼠标悬停时显示表达式值
3. `supportsSetVariable` - 支持修改变量值
4. `supportsValueFormattingOptions` - 支持值格式化选项
5. `supportsHitConditionalBreakpoints` - 通过 JDI `addCountFilter()` 实现命中次数过滤
6. `supportsFunctionBreakpoints` - 通过 JDI `MethodEntryRequest` 实现方法断点，支持通配符 `*`
7. `supportsTerminateRequest` - `TerminateHandler` 调用 `vm.exit(0)` 终止被调试进程
8. `supportsBreakpointLocationsRequest` - `BreakpointLocationsHandler` 查询 JDI 有效行位置
9. `supportsLoadedSourcesRequest` - `LoadedSourcesHandler` 枚举 JDI 已加载类的源文件名

**已声明的能力 (InitializeHandler)**:
```kotlin
supportsFunctionBreakpoints = true
supportsHitConditionalBreakpoints = true
supportsBreakpointLocationsRequest = true
supportsLoadedSourcesRequest = true
supportsTerminateRequest = true
```

**交付物**: 支持DAP

### Phase 6: 优化和完善

- [x] Suspend-on-Attach 模式
  - `DebugTarget.Attach` 和 `AttachPid` 新增 `suspend: Boolean`（默认 `true`）
  - `EventHandler.keepVMStartSuspended` 标志位，阻止 `VMStartEvent` 自动 resume
  - `DebugSession` 连接后自动作判断：若 VM 已暂停则直接保持，若未暂停则调用 `vm.suspend()`
  - CLI `attach` 命令支持 `--no-suspend` 选项；挂起后显示提示信息
  - `continue` 命令在首次从 attach 暂停恢复时显示 "Starting program execution..."
- [ ] 性能优化
- [ ] 错误处理增强
- [ ] 文档编写
- [ ] 打包发布

**交付物**: 可发布的 1.0 版本

---

## 七、测试策略

### 7.1 单元测试

```kotlin
class SMAPParserTest {
    @Test
    fun `parse simple SMAP`() {
        val smap = """
            SMAP
            test.kt
            Kotlin
            *S Kotlin
            *F
            + 1 test.kt
            test
            *L
            1#1,5:1
            *E
        """.trimIndent()

        val result = SMAPParser().parse(smap)
        assertNotNull(result)
        assertEquals(1, result.fileMappings.size)
    }
}
```

### 7.2 集成测试

```kotlin
class DebugSessionTest {
    @Test
    fun `can set breakpoint and hit it`() {
        val session = DebugSession(DebugTarget.Launch(
            mainClass = "TestKt",
            classpath = listOf("test-classes")
        ))

        session.start()
        val bp = session.breakpointManager.addBreakpoint("Test.kt", 5)
        session.resume()

        // 等待断点触发
        val event = session.waitForBreakpoint(timeout = 5000)
        assertNotNull(event)
        assertEquals(5, event.location.lineNumber())

        session.stop()
    }
}
```

### 7.3 测试用例

准备一组测试 Kotlin 程序：

```kotlin
// test/basic.kt - 基本断点测试
fun main() {
    val x = 42       // 断点测试
    println(x)
}

// test/inline.kt - 内联函数测试
inline fun calculate(x: Int): Int {
    return x * 2     // 内联函数断点
}

fun main() {
    val result = calculate(21)
}

// test/lambda.kt - Lambda 测试
fun main() {
    listOf(1, 2, 3).forEach {
        println(it)  // Lambda 断点
    }
}

// test/coroutine.kt - 协程测试 (可选)
suspend fun loadData(): String {
    delay(100)       // 协程断点
    return "data"
}

fun main() = runBlocking {
    val data = loadData()
}
```

---

## 八、参考资源

### 8.1 IDEA 源码位置

| 模块 | 路径 |
|------|------|
| Kotlin 调试器核心 | `/Users/gongyanan/soft/intellij-community/plugins/kotlin/jvm-debugger/core/` |
| 位置管理器 | `.../core/src/org/jetbrains/kotlin/idea/debugger/core/KotlinPositionManager.kt` |
| SMAP 缓存 | `.../base/util/src/.../KotlinSourceMapCache.kt` |
| 单步执行 | `.../core/src/.../stepping/` |
| 表达式评估 | `.../evaluation/` |

### 8.2 Kotlin 编译器源码

| 模块 | 路径 |
|------|------|
| SMAP 定义 | `/Users/gongyanan/study/kotlin/compiler/backend.common.jvm/src/org/jetbrains/kotlin/codegen/inline/SMAP.kt` |
| SMAP 解析器 | `.../inline/SMAPParser.kt` |

### 8.3 相关规范

- **JSR-045**: SMAP (Debugging Support for Other Languages)
- **JDWP**: Java Debug Wire Protocol
- **JDI**: Java Debug Interface API (com.sun.jdi)

---

## 九、风险和挑战

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| SMAP 解析复杂 | 内联函数调试不准确 | 参考 IDEA 实现，充分测试 |
| 表达式评估困难 | 功能受限 | 先实现简单版本，逐步增强 |
| 协程调试复杂 | 协程栈不完整 | 作为可选功能，依赖 kotlinx-coroutines-debug |
| JDI 兼容性 | 不同 JDK 行为不一致 | 锁定 JDK 11+ 版本，做好异常处理 |

---

## 十、确认事项

请确认以下设计决策：

1. **目标 JDK 版本**: JDK 11+ (推荐 JDK 17/21)
2. **Kotlin 版本**: 2.0.x
3. **表达式评估**: 简化版（仅支持变量查看和基本运算）还是完整版（支持任意 Kotlin 表达式）？
4. **协程调试**: 是否需要在 Phase 1-3 支持？
5. **输出格式**: 是否需要支持 JSON 输出（便于 IDE 集成）？
6. **项目名称**: `kotlin-debugger` 或其他？

---

如果以上设计方案没有问题，我将开始实现 Phase 1 的基础框架。

---

## 十一、VSCode 扩展功能设计

### 11.1 InlineValues Provider

**目标**: 调试暂停时在编辑器中内联显示变量值。

**实现**: `KotlinInlineValuesProvider implements vscode.InlineValuesProvider`

```
停止行 ±10 行范围内 → 提取标识符 → 过滤 Kotlin 关键字/类型名 → 返回 InlineValueVariableLookup
```

- 通过 `vscode.languages.registerInlineValuesProvider` 注册
- 使用 `InlineValueVariableLookup` 让 VS Code 自动从调试上下文中查找变量值
- `KOTLIN_KEYWORDS` 和 `COMMON_TYPE_NAMES` 集合用于过滤非变量标识符

### 11.2 CodeLens 增强

**CodeLens Provider**: `KotlinDebugCodeLensProvider`

| 场景 | 显示内容 |
|------|----------|
| `fun main(` | `▶ Run` / `Debug` |
| `@JvmStatic fun main(` | `▶ Run` / `Debug` |
| `@Test fun xxx(` | `▶ Run Test` / `Debug Test` |

**关键改进**:
- `mainFunctionPattern` 使用 `/^\s*(?:@\S+\s+)*fun\s+main\s*\(/` 支持注解前缀
- 通过 `testAnnotationPattern` 检测 `@Test`/`@org.junit.Test` 注解
- `findNextFunLine()` 在检测到注解后向后查找 `fun` 声明行
- `findEnclosingClassName()` 向上扫描获取类名，用于 Gradle 测试过滤

### 11.3 Java 路径配置

**设置项**: `kotlin-debug.javaHome`

**查找顺序** (在 `KotlinDebugAdapterDescriptorFactory.resolveJavaExecutable()`):
1. `kotlin-debug.javaHome` 配置
2. `JAVA_HOME` 环境变量
3. 系统 `java` 命令（PATH 中）

### 11.4 调试前构建集成

**设置项**: `kotlin-debug.buildBeforeDebug` (默认: `false`)

**实现** (在 `KotlinDebugConfigurationProvider.resolveDebugConfiguration()`):
- 在解析调试配置后、启动调试会话前调用 `runPreDebugBuild()`
- 检测构建工具：存在 `gradlew` → Gradle (`./gradlew classes`)；存在 `pom.xml` → Maven (`mvn compile -q`)
- 构建失败弹出错误通知并取消调试会话（返回 `undefined`）

### 11.5 异常详情增强

**后端实现** (`ExceptionInfoHandler`):

```
DAP exceptionInfo 请求
  → EventHandler.lastExceptionObject（缓存最近一次异常对象引用）
  → buildExceptionInfo(exception, thread)
    ├── getExceptionMessage()        -- 读取 detailMessage 字段
    ├── buildStackTrace()            -- 优先读 stackTrace 字段，回退到 JDI frames
    └── buildCauseChain()            -- cause 链（1层）
  → 返回: exceptionId, description("Type: message"), details.stackTrace, details.innerException
```

**关键设计**: 使用 JDI 字段访问（非方法调用）避免线程挂起状态下的死锁风险。

