# Kotlin 调试示例项目 (Kotlin Debug Examples)

这个项目包含了全面的 Kotlin 语言示例，用于演示和测试 VSCode Kotlin 调试器的各种功能。

## 项目结构

```
src/main/kotlin/
├── Main.kt                    # 主入口程序（交互式菜单）
├── basics/                    # 基础语法示例
│   ├── Variables.kt           # 变量和类型
│   └── ControlFlow.kt         # 控制流（if、when、for、while）
├── classes/                   # 类和对象示例
│   ├── DataTypes.kt           # 数据类型（data class）
│   ├── Classes.kt             # 类、继承、接口
│   └── SpecialClasses.kt      # 密封类、枚举、对象声明
├── functions/                 # 函数示例
│   ├── Functions.kt           # 普通函数、默认参数、尾递归
│   ├── Lambdas.kt             # Lambda 表达式、高阶函数
│   └── Extensions.kt          # 扩展函数和扩展属性
├── collections/               # 集合示例
│   └── Collections.kt         # List、Set、Map 操作
├── nullsafety/                # 空安全示例
│   └── NullSafety.kt          # 可空类型、安全调用、Elvis
└── advanced/                  # 高级特性示例
    ├── InlineFunctions.kt     # 内联函数、reified
    ├── Generics.kt            # 泛型类、泛型函数、型变
    └── Exceptions.kt          # 异常处理
```

## 调试功能演示

### 1. 基础语法 (basics/)

- **Variables.kt**: 演示基本类型、变量声明（val/var）、类型推断、数组
- **ControlFlow.kt**: 演示 if 表达式、when 表达式、for/while 循环、范围

### 2. 数据类型和类 (classes/)

- **DataTypes.kt**: 数据类、copy()、解构声明、嵌套数据类、泛型数据类
- **Classes.kt**: 普通类、继承、抽象类、接口、多态、类型检查
- **SpecialClasses.kt**: 枚举类、密封类、对象声明、伴生对象、匿名对象

### 3. 函数 (functions/)

- **Functions.kt**: 基本函数、默认参数、命名参数、可变参数、局部函数、尾递归
- **Lambdas.kt**: Lambda 表达式、高阶函数、闭包、集合操作、作用域函数
- **Extensions.kt**: 扩展函数、扩展属性、可空类型扩展

### 4. 集合 (collections/)

- **Collections.kt**: List/Set/Map 操作、转换、过滤、聚合、排序、查找

### 5. 空安全 (nullsafety/)

- **NullSafety.kt**: 可空类型、安全调用 (?.)、Elvis 操作符 (?:)、非空断言 (!!)、let、lateinit

### 6. 高级特性 (advanced/)

- **InlineFunctions.kt**: 内联函数、reified 类型、noinline、crossinline、非局部返回
- **Generics.kt**: 泛型类、泛型函数、泛型约束、协变/逆变、星投影
- **Exceptions.kt**: try-catch-finally、自定义异常、异常链、runCatching

## 使用方法

### 1. 构建项目

```bash
./gradlew build
```

### 2. 运行程序

```bash
./gradlew run
```

### 3. 使用调试器

1. 在 VSCode 中打开此项目
2. 在感兴趣的代码行设置断点（点击行号左侧）
3. 配置 `launch.json`：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "kotlin",
      "request": "attach",
      "name": "Kotlin: Attach to JVM",
      "host": "localhost",
      "port": 5005,
      "sourcePaths": [
        "${workspaceFolder}/src/main/kotlin"
      ]
    }
  ]
}
```

4. 以调试模式启动程序：

```bash
./gradlew run --debug-jvm
```

5. 在 VSCode 中按 F5 连接调试器

## 调试技巧

### 断点设置建议

每个示例文件中都有 `// 断点:` 注释，标记了建议设置断点的位置。这些位置展示了特定的调试场景。

### 常用调试操作

| 快捷键 | 功能 |
|--------|------|
| F5 | 开始/继续调试 |
| F9 | 切换断点 |
| F10 | 单步跳过 (Step Over) |
| F11 | 单步进入 (Step Into) |
| Shift+F11 | 单步跳出 (Step Out) |

### 变量查看

- **Variables 面板**: 查看当前作用域的变量
- **Watch 面板**: 添加表达式进行监视
- **Debug Console**: 输入表达式进行求值

## 示例代码特点

1. **模块化组织**: 代码按功能分类到不同的包中
2. **跨文件依赖**: 展示多文件项目的调试
3. **详细注释**: 每个示例都有中文注释说明
4. **调试标记**: 建议断点位置都有注释标记
5. **全面覆盖**: 涵盖 Kotlin 语言的主要特性
