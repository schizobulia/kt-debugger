# Kotlin Debug Extension for VSCode

基于 kotlin-debugger 项目的 VSCode 调试扩展，支持通过 DAP 协议调试 Kotlin 程序。

## 功能

- 支持 attach 模式连接到运行中的 JVM
- 设置和管理断点
- 查看调用堆栈
- 查看变量

## 安装

1. 构建 kotlin-debugger：
   ```bash
   cd /path/to/kt-debug
   ./gradlew fatJar
   ```

2. 安装扩展依赖并编译：
   ```bash
   cd vscode-kotlin-debug
   npm install
   npm run compile
   ```

3. 打包扩展：
   ```bash
   npm run package
   ```

4. 在 VSCode 中安装生成的 `.vsix` 文件

## 使用方法

### 1. 启动目标程序（带调试参数）

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar your-app.jar
```

### 2. 配置 launch.json

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

1. 在 Kotlin 源文件中设置断点
2. 按 F5 或点击 "Run and Debug"
3. 选择 "Kotlin: Attach to JVM" 配置

## 配置选项

| 选项 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `host` | string | "localhost" | 目标 JVM 主机地址 |
| `port` | number | 必填 | 调试端口 |
| `sourcePaths` | string[] | [] | Kotlin 源代码路径 |

## 开发

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
└── out/                # 编译输出
```

## 故障排除

1. **找不到 JAR 文件**
   - 确保已运行 `./gradlew fatJar`
   - JAR 文件位于 `build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar`

2. **连接失败**
   - 确保目标程序使用正确的调试参数启动
   - 检查端口是否正确且未被占用

3. **断点不生效**
   - 确保 `sourcePaths` 配置正确指向源代码目录
   - 确保源代码与运行的程序版本一致
