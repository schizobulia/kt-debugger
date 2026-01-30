# Kotlin Debug Extension for VSCode

<p align="center">
  <img src="images/kotlin-debug-icon.png" alt="Kotlin Debug" width="128">
</p>

基于 kotlin-debugger 项目的 VSCode 调试扩展，支持通过 DAP 协议调试 Kotlin 程序。

## ✨ 功能

- 🚀 支持 launch 模式自动启动应用并调试
- 🔗 支持 attach 模式连接到运行中的 JVM
- 🎯 设置和管理断点（包括条件断点）
- 📚 查看调用堆栈
- 🔍 查看和检查变量
- 💡 支持表达式求值
- 📝 实时日志输出

## 📦 安装

### 方式一：从 VSCode 市场安装（推荐）

1. 打开 VSCode
2. 按 `Ctrl+Shift+X`（Windows/Linux）或 `Cmd+Shift+X`（Mac）打开扩展面板
3. 搜索 "Kotlin Debug"
4. 点击 "Install" 安装

### 方式二：从 VSIX 文件安装

1. 下载 `.vsix` 文件（从 [GitHub Releases](https://github.com/your-username/kt-debug/releases)）
2. 在 VSCode 中按 `Ctrl+Shift+P` 打开命令面板
3. 输入 "Install from VSIX" 并选择
4. 选择下载的 `.vsix` 文件

### 方式三：从源码构建

```bash
# 克隆仓库
git clone https://github.com/your-username/kt-debug.git
cd kt-debug

# 构建扩展（包含 debugger JAR）
bash scripts/vscode-ext.sh build

# 安装到 VSCode
bash scripts/vscode-ext.sh install
```

## 🚀 使用方法

### 方式一：Launch 模式（推荐）

Launch 模式会自动启动您的应用程序并附加调试器，无需手动启动程序。

在项目的 `.vscode/launch.json` 中添加：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "kotlin",
      "request": "launch",
      "name": "Kotlin: Launch and Debug",
      "command": "java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar ${workspaceFolder}/build/libs/your-app.jar",
      "port": 5005,
      "cwd": "${workspaceFolder}",
      "sourcePaths": [
        "${workspaceFolder}/src/main/kotlin"
      ]
    }
  ]
}
```

**Gradle 项目示例：**

```json
{
  "type": "kotlin",
  "request": "launch",
  "name": "Kotlin: Launch Gradle",
  "command": "./gradlew run -Dorg.gradle.jvmargs=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005\"",
  "port": 5005,
  "cwd": "${workspaceFolder}",
  "sourcePaths": [
    "${workspaceFolder}/src/main/kotlin"
  ]
}
```

**配置说明：**
- `command`: 启动应用程序的命令，**必须包含 JDWP 调试参数**，并确保端口与 `port` 配置一致
- `port`: 调试端口，必须与命令中的 `address` 参数一致
- `cwd`: 命令执行的工作目录
- `env`: 环境变量（可选）
- `preLaunchWait`: 启动命令后等待的时间（毫秒），默认 2000ms

### 方式二：Attach 模式

如果您需要手动控制应用程序的启动，可以使用 Attach 模式。

#### 1. 启动目标程序（带调试参数）

```bash
# 方式一：使用 suspend=y（程序会等待调试器连接）
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar your-app.jar

# 方式二：使用 suspend=n（程序立即运行，调试器随时可连接）
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar your-app.jar

# Gradle 项目
./gradlew run --debug-jvm

# Maven 项目
mvn exec:java -Dexec.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
```

#### 2. 配置 launch.json

在项目的 `.vscode/launch.json` 中添加：

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

### 3. 开始调试

1. 在 Kotlin 源文件中设置断点（点击行号左侧）
2. 按 `F5` 或点击侧边栏的 "Run and Debug"
3. 选择对应的调试配置
4. 调试器将连接到目标 JVM

## ⚙️ 配置选项

### Launch 模式配置

| 选项 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| `command` | string | ✅ | - | 启动应用程序的命令，必须包含 JDWP 调试参数 |
| `port` | number | ✅ | - | 调试端口，必须与命令中的 address 参数一致 |
| `host` | string | | "localhost" | 调试主机地址 |
| `cwd` | string | | "${workspaceFolder}" | 命令执行的工作目录 |
| `env` | object | | {} | 环境变量 |
| `sourcePaths` | string[] | | [] | Kotlin 源代码路径 |
| `preLaunchWait` | number | | 2000 | 启动命令后等待的时间（毫秒） |

### Attach 模式配置

| 选项 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| `host` | string | | "localhost" | 目标 JVM 主机地址 |
| `port` | number | ✅ | - | 调试端口 |
| `sourcePaths` | string[] | | [] | Kotlin 源代码路径 |

### 全局配置

| 选项 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `kotlin-debug.debuggerJarPath` | string | "" | 自定义 debugger JAR 路径 |

## 🔍 调试功能

- **断点**: 点击行号左侧设置断点
- **条件断点**: 右键断点 → "Edit Breakpoint" 添加条件
- **变量查看**: 在 "Variables" 面板查看当前作用域变量
- **表达式求值**: 在 "Debug Console" 输入表达式
- **调用堆栈**: 在 "Call Stack" 面板查看调用链
- **单步执行**: 使用 F10 (Step Over)、F11 (Step Into)、Shift+F11 (Step Out)

## 📋 快捷键

| 快捷键 | 功能 |
|--------|------|
| `F5` | 开始/继续调试 |
| `Shift+F5` | 停止调试 |
| `F10` | 单步跳过 |
| `F11` | 单步进入 |
| `Shift+F11` | 单步跳出 |
| `F9` | 切换断点 |

## 🛠️ 开发

### 调试扩展

1. 在 VSCode 中打开此扩展项目
2. 按 F5 启动扩展开发宿主
3. 在新窗口中测试调试功能

### 项目结构

```
vscode-kotlin-debug/
├── package.json        # 扩展配置
├── tsconfig.json       # TypeScript 配置
├── src/
│   └── extension.ts    # 扩展入口
├── out/                # 编译输出
└── kotlin-debugger.jar # 调试器核心（打包时包含）
```

### 构建脚本

```bash
# 完整构建
bash scripts/vscode-ext.sh build

# 跳过 JAR 构建
bash scripts/vscode-ext.sh build --skip-jar

# 更新版本号
bash scripts/vscode-ext.sh version --minor

# 发布到市场
export VSCE_PAT=your-token
bash scripts/vscode-ext.sh publish
```

## ❓ 故障排除

### 找不到 JAR 文件
- 如果从 VSIX 安装，JAR 已包含在扩展中
- 如果从源码构建，确保已运行 `bash scripts/vscode-ext.sh build`
- 可在设置中配置 `kotlin-debug.debuggerJarPath` 指定自定义路径

### 连接失败
- 确保目标程序使用正确的调试参数启动
- 检查端口是否正确且未被占用
- 尝试使用 `netstat -an | grep 5005` 确认端口监听

### 断点不生效
- 确保 `sourcePaths` 配置正确指向源代码目录
- 检查源代码是否与运行的 class 文件匹配
- 对于 Gradle 项目，源码通常在 `src/main/kotlin`

### 查看调试日志
- 打开 VSCode 输出面板（View → Output）
- 选择 "Kotlin Debugger Logs" 查看详细日志

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 🔗 相关链接

- [项目主页](https://github.com/your-username/kt-debug)
- [问题反馈](https://github.com/your-username/kt-debug/issues)
- [更新日志](https://github.com/your-username/kt-debug/releases)

3. **断点不生效**
   - 确保 `sourcePaths` 配置正确指向源代码目录
   - 确保源代码与运行的程序版本一致
