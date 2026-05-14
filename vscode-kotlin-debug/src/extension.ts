import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';
import * as os from 'os';
import * as net from 'net';

// 全局日志输出通道
let logChannel: vscode.OutputChannel;
let logFileWatcher: fs.StatWatcher | undefined;
let watchedLogFile: string | undefined;

// 用于跟踪通过launch模式启动的应用终端
let activeTerminal: vscode.Terminal | undefined;

// 状态栏项
let statusBarItem: vscode.StatusBarItem;

// 代码透镜提供者
let codeLensProvider: KotlinDebugCodeLensProvider;

// 当前调试状态
let isDebugging = false;

export function activate(context: vscode.ExtensionContext) {
    console.log('Kotlin Debug extension is now active');

    // 创建全局输出通道用于显示日志
    logChannel = vscode.window.createOutputChannel('Kotlin Debugger Logs');
    context.subscriptions.push(logChannel);

    // 创建状态栏项
    statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    statusBarItem.command = 'kotlin-debug.showDebugMenu';
    updateStatusBar('ready');
    statusBarItem.show();
    context.subscriptions.push(statusBarItem);

    // 注册代码透镜提供者
    codeLensProvider = new KotlinDebugCodeLensProvider();
    context.subscriptions.push(
        vscode.languages.registerCodeLensProvider(
            { language: 'kotlin', scheme: 'file' },
            codeLensProvider
        )
    );

    // 注册调试适配器描述符工厂
    const factory = new KotlinDebugAdapterDescriptorFactory(context);
    context.subscriptions.push(
        vscode.debug.registerDebugAdapterDescriptorFactory('kotlin', factory)
    );

    // 注册调试配置提供者
    const configProvider = new KotlinDebugConfigurationProvider();
    context.subscriptions.push(
        vscode.debug.registerDebugConfigurationProvider('kotlin', configProvider)
    );

    // 注册生成配置命令
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.generateLaunchConfig', generateLaunchConfiguration)
    );

    // 注册调试主函数命令
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.debugMain', (args: { file: string, line: number }) => {
            debugMainFunction(args);
        })
    );

    // 注册显示调试菜单命令
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.showDebugMenu', showDebugMenu)
    );

    // 注册协程视图
    const coroutineViewProvider = new CoroutineViewProvider();
    context.subscriptions.push(
        vscode.window.registerTreeDataProvider('kotlin-debug.coroutinesView', coroutineViewProvider)
    );

    // 注册协程视图刷新命令
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.refreshCoroutines', () => {
            coroutineViewProvider.fetchAndRefresh();
        })
    );

    // 注册协程栈帧跳转命令
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.coroutine.openFrame',
            async (frame: { file?: string; line?: number }) => {
                if (!frame.file || !frame.line) { return; }
                const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
                if (!workspaceFolder) { return; }
                // 在源码路径中查找文件
                const uris = await vscode.workspace.findFiles(`**/${frame.file}`, '**/build/**', 1);
                if (uris.length > 0) {
                    const doc = await vscode.workspace.openTextDocument(uris[0]);
                    const editor = await vscode.window.showTextDocument(doc);
                    const pos = new vscode.Position(frame.line - 1, 0);
                    editor.selection = new vscode.Selection(pos, pos);
                    editor.revealRange(new vscode.Range(pos, pos));
                }
            })
    );

    // 注册 Hot Code Replace 命令
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.hotCodeReplace', () => {
            triggerHotCodeReplace();
        })
    );

    // 注册悬停提供者用于调试时显示变量值
    context.subscriptions.push(
        vscode.languages.registerHoverProvider(
            { language: 'kotlin', scheme: 'file' },
            new KotlinDebugHoverProvider()
        )
    );

    // 注册内联值提供者（调试暂停时在编辑器行内显示变量值）
    context.subscriptions.push(
        vscode.languages.registerInlineValuesProvider(
            { language: 'kotlin', scheme: 'file' },
            new KotlinInlineValuesProvider()
        )
    );

    // 注册 @Test CodeLens 命令：运行测试
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.runTest',
            (args: { file: string; className: string; methodName: string }) => {
                runKotlinTest(args, false);
            })
    );

    // 注册 @Test CodeLens 命令：调试测试
    context.subscriptions.push(
        vscode.commands.registerCommand('kotlin-debug.debugTest',
            (args: { file: string; className: string; methodName: string }) => {
                runKotlinTest(args, true);
            })
    );

    // 监听调试会话开始事件
    context.subscriptions.push(
        vscode.debug.onDidStartDebugSession((session) => {
            if (session.type === 'kotlin') {
                isDebugging = true;
                updateStatusBar('debugging');
                logChannel.show(true);
                logChannel.appendLine('=== Debug Session Started ===');
                logChannel.appendLine('Waiting for log file to be created...\n');

                // 开始监控日志文件
                startLogFileWatcher();
            }
        })
    );

    // 监听调试停止事件（暂停时刷新协程视图）
    context.subscriptions.push(
        vscode.debug.onDidChangeActiveDebugSession(() => {
            coroutineViewProvider.fetchAndRefresh();
        })
    );

    // 监听调试会话结束事件
    context.subscriptions.push(
        vscode.debug.onDidTerminateDebugSession((session) => {
            if (session.type === 'kotlin') {
                isDebugging = false;
                updateStatusBar('ready');
                logChannel.appendLine('\n=== Debug Session Ended ===');
                stopLogFileWatcher();
                stopLaunchedApp();
                // 清空协程视图
                coroutineViewProvider.fetchAndRefresh();
            }
        })
    );
}

export function deactivate() {
    stopLogFileWatcher();
    stopLaunchedApp();
    if (statusBarItem) {
        statusBarItem.dispose();
    }
}

/**
 * 更新状态栏
 */
function updateStatusBar(state: 'ready' | 'debugging' | 'error') {
    switch (state) {
        case 'ready':
            statusBarItem.text = '$(debug) Kotlin Debug';
            statusBarItem.tooltip = 'Kotlin Debugger - Click to show debug menu';
            statusBarItem.backgroundColor = undefined;
            break;
        case 'debugging':
            statusBarItem.text = '$(debug-alt) Debugging Kotlin';
            statusBarItem.tooltip = 'Kotlin Debugger - Debugging session active';
            statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
            break;
        case 'error':
            statusBarItem.text = '$(error) Kotlin Debug Error';
            statusBarItem.tooltip = 'Kotlin Debugger - Error occurred';
            statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
            break;
    }
}

/**
 * 显示调试菜单
 */
async function showDebugMenu() {
    const items: vscode.QuickPickItem[] = [
        {
            label: '$(add) Generate Debug Configuration',
            description: 'Create a new launch.json configuration',
            detail: 'Interactively generate a launch or attach configuration'
        },
        {
            label: '$(play) Start Debugging',
            description: 'Start debugging with existing configuration',
            detail: 'Launch a debug session using an existing launch.json configuration'
        },
        {
            label: '$(sync) Hot Code Replace',
            description: 'Reload modified classes into running JVM',
            detail: 'Apply code changes without restarting the debug session'
        },
        {
            label: '$(output) Show Debug Logs',
            description: 'Open the debug output panel',
            detail: 'View detailed debugger logs'
        },
        {
            label: '$(book) View Documentation',
            description: 'Open Kotlin Debug documentation',
            detail: 'Learn how to use Kotlin Debugger for VSCode'
        }
    ];

    const selected = await vscode.window.showQuickPick(items, {
        placeHolder: 'Kotlin Debug Actions'
    });

    if (!selected) {
        return;
    }

    switch (selected.label) {
        case '$(add) Generate Debug Configuration':
            await vscode.commands.executeCommand('kotlin-debug.generateLaunchConfig');
            break;
        case '$(play) Start Debugging':
            await vscode.commands.executeCommand('workbench.action.debug.start');
            break;
        case '$(sync) Hot Code Replace':
            await vscode.commands.executeCommand('kotlin-debug.hotCodeReplace');
            break;
        case '$(output) Show Debug Logs':
            logChannel.show(true);
            break;
        case '$(book) View Documentation':
            vscode.env.openExternal(vscode.Uri.parse('https://github.com/schizobulia/kt-debugger#readme'));
            break;
    }
}

/**
 * 生成调试配置
 */
async function generateLaunchConfiguration() {
    const configType = await vscode.window.showQuickPick([
        { label: 'Launch', description: 'Start application and attach debugger', detail: 'Recommended for most projects' },
        { label: 'Attach', description: 'Attach to a running JVM process', detail: 'Use when application is already running' },
        { label: 'Gradle Launch', description: 'Start Gradle project with debug', detail: 'For Gradle-based Kotlin projects' }
    ], {
        placeHolder: 'Select configuration type'
    });

    if (!configType) {
        return;
    }

    let config: vscode.DebugConfiguration;
    const port = await vscode.window.showInputBox({
        prompt: 'Enter debug port',
        value: '5005',
        validateInput: (value) => {
            const num = parseInt(value);
            if (isNaN(num) || num < 1 || num > 65535) {
                return 'Please enter a valid port number (1-65535)';
            }
            return undefined;
        }
    });

    if (!port) {
        return;
    }

    const portNum = parseInt(port);

    switch (configType.label) {
        case 'Launch':
            const jarPath = await vscode.window.showInputBox({
                prompt: 'Enter the path to your JAR file (relative to workspace)',
                value: 'build/libs/your-app.jar'
            });
            if (!jarPath) {
                return;
            }
            config = {
                type: 'kotlin',
                request: 'launch',
                name: 'Kotlin: Launch and Debug',
                command: `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=${portNum} -jar \${workspaceFolder}/${jarPath}`,
                port: portNum,
                cwd: '${workspaceFolder}',
                sourcePaths: ['${workspaceFolder}/src/main/kotlin']
            };
            break;
        case 'Attach':
            const host = await vscode.window.showInputBox({
                prompt: 'Enter host address',
                value: 'localhost'
            });
            if (!host) {
                return;
            }
            config = {
                type: 'kotlin',
                request: 'attach',
                name: 'Kotlin: Attach to JVM',
                host: host,
                port: portNum,
                sourcePaths: ['${workspaceFolder}/src/main/kotlin']
            };
            break;
        case 'Gradle Launch':
            config = {
                type: 'kotlin',
                request: 'launch',
                name: 'Kotlin: Launch Gradle',
                command: `./gradlew run -Dorg.gradle.jvmargs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=${portNum}"`,
                port: portNum,
                cwd: '${workspaceFolder}',
                sourcePaths: ['${workspaceFolder}/src/main/kotlin']
            };
            break;
        default:
            return;
    }

    // 添加或更新 launch.json
    await addOrUpdateLaunchConfiguration(config);
}

/**
 * 添加或更新 launch.json 配置
 */
async function addOrUpdateLaunchConfiguration(config: vscode.DebugConfiguration) {
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        vscode.window.showErrorMessage('No workspace folder open');
        return;
    }

    const vscodePath = path.join(workspaceFolder.uri.fsPath, '.vscode');
    const launchJsonPath = path.join(vscodePath, 'launch.json');

    // 确保 .vscode 目录存在
    if (!fs.existsSync(vscodePath)) {
        fs.mkdirSync(vscodePath, { recursive: true });
    }

    let launchJson: { version: string; configurations: vscode.DebugConfiguration[] };

    if (fs.existsSync(launchJsonPath)) {
        try {
            const content = fs.readFileSync(launchJsonPath, 'utf-8');
            // Try to parse JSON (VSCode launch.json supports comments via JSON with Comments)
            // Use a simple approach - try parsing directly first
            launchJson = JSON.parse(content);
        } catch (e) {
            // If direct parsing fails, try removing simple comments
            try {
                const content = fs.readFileSync(launchJsonPath, 'utf-8');
                // Remove single-line comments and multi-line comments more carefully
                const lines = content.split('\n');
                const cleanedLines = lines.map(line => {
                    // Only remove comments that start at the beginning of a line (after whitespace)
                    const trimmed = line.trim();
                    if (trimmed.startsWith('//')) {
                        return '';
                    }
                    return line;
                });
                const cleanedContent = cleanedLines.join('\n').replace(/\/\*[\s\S]*?\*\//g, '');
                launchJson = JSON.parse(cleanedContent);
            } catch {
                logChannel.appendLine(`[Extension] Warning: Could not parse existing launch.json. Creating new configuration.`);
                vscode.window.showWarningMessage('Could not parse existing launch.json. A new configuration will be added.');
                launchJson = { version: '0.2.0', configurations: [] };
            }
        }
    } else {
        launchJson = { version: '0.2.0', configurations: [] };
    }

    // 检查是否存在同名配置
    const existingIndex = launchJson.configurations.findIndex(c => c.name === config.name);
    if (existingIndex !== -1) {
        const action = await vscode.window.showQuickPick(['Replace', 'Add as new'], {
            placeHolder: `Configuration "${config.name}" already exists. What would you like to do?`
        });
        if (!action) {
            return;
        }
        if (action === 'Replace') {
            launchJson.configurations[existingIndex] = config;
        } else {
            config.name = `${config.name} (${launchJson.configurations.length + 1})`;
            launchJson.configurations.push(config);
        }
    } else {
        launchJson.configurations.push(config);
    }

    fs.writeFileSync(launchJsonPath, JSON.stringify(launchJson, null, 2));
    
    const doc = await vscode.workspace.openTextDocument(launchJsonPath);
    await vscode.window.showTextDocument(doc);
    
    vscode.window.showInformationMessage(`Debug configuration "${config.name}" has been added to launch.json`);
}

/**
 * 调试主函数
 */
async function debugMainFunction(args: { file: string, line: number }) {
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        vscode.window.showErrorMessage('No workspace folder open');
        return;
    }

    // 尝试检测项目类型并创建适当的配置
    const hasGradle = fs.existsSync(path.join(workspaceFolder.uri.fsPath, 'build.gradle.kts')) ||
                      fs.existsSync(path.join(workspaceFolder.uri.fsPath, 'build.gradle'));
    
    const config: vscode.DebugConfiguration = hasGradle ? {
        type: 'kotlin',
        request: 'launch',
        name: 'Debug Main Function',
        command: './gradlew run -Dorg.gradle.jvmargs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"',
        port: 5005,
        cwd: '${workspaceFolder}',
        sourcePaths: ['${workspaceFolder}/src/main/kotlin']
    } : {
        type: 'kotlin',
        request: 'launch',
        name: 'Debug Main Function',
        command: 'java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar ${workspaceFolder}/build/libs/app.jar',
        port: 5005,
        cwd: '${workspaceFolder}',
        sourcePaths: ['${workspaceFolder}/src/main/kotlin']
    };

    // 启动调试会话
    await vscode.debug.startDebugging(workspaceFolder, config);
}

/**
 * 停止通过launch模式启动的应用终端
 */
function stopLaunchedApp() {
    if (activeTerminal) {
        logChannel.appendLine('[Extension] Disposing debug terminal...');
        activeTerminal.dispose();
        activeTerminal = undefined;
    }
}

/**
 * 开始监控日志文件
 */
function startLogFileWatcher() {
    // 停止之前的watcher
    stopLogFileWatcher();

    // 查找最新的日志文件
    setTimeout(() => {
        const logFiles = findLogFiles();
        if (logFiles.length > 0) {
            const latestLogFile = logFiles[0];
            logChannel.appendLine(`Found log file: ${latestLogFile}`);
            logChannel.appendLine('---\n');

            // 保存当前监控的文件路径
            watchedLogFile = latestLogFile;

            // 监控文件变化
            logFileWatcher = fs.watchFile(latestLogFile, { interval: 100 }, () => {
                try {
                    const content = fs.readFileSync(latestLogFile, 'utf-8');
                    logChannel.append(content);
                } catch (error) {
                    // 文件可能被删除
                }
            });

            // 读取初始内容
            try {
                const initialContent = fs.readFileSync(latestLogFile, 'utf-8');
                if (initialContent) {
                    logChannel.append(initialContent);
                }
            } catch (error) {
                // 文件可能还没有创建
            }
        }
    }, 1000); // 延迟1秒等待日志文件创建
}

/**
 * 停止监控日志文件
 */
function stopLogFileWatcher() {
    if (logFileWatcher && watchedLogFile) {
        fs.unwatchFile(watchedLogFile);
        logFileWatcher = undefined;
        watchedLogFile = undefined;
    }
}

/**
 * 查找最新的调试日志文件
 */
function findLogFiles(): string[] {
    const tempDir = os.tmpdir();
    const files: string[] = [];

    try {
        const entries = fs.readdirSync(tempDir);
        for (const entry of entries) {
            if (entry.startsWith('kotlin-debugger-') && entry.endsWith('.log')) {
                const fullPath = path.join(tempDir, entry);
                files.push(fullPath);
            }
        }
    } catch (error) {
        // 目录不存在或无法读取
    }

    // 按修改时间排序，最新的在前
    files.sort((a, b) => {
        const statA = fs.statSync(a);
        const statB = fs.statSync(b);
        return statB.mtimeMs - statA.mtimeMs;
    });

    return files;
}

/**
 * 调试配置提供者
 */
class KotlinDebugConfigurationProvider implements vscode.DebugConfigurationProvider {

    async resolveDebugConfiguration(
        folder: vscode.WorkspaceFolder | undefined,
        config: vscode.DebugConfiguration,
        token?: vscode.CancellationToken
    ): Promise<vscode.DebugConfiguration | undefined> {

        // 如果没有配置，提供默认配置
        if (!config.type && !config.request && !config.name) {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.languageId === 'kotlin') {
                config.type = 'kotlin';
                config.name = 'Kotlin: Attach';
                config.request = 'attach';
                config.host = 'localhost';
                config.port = 5005;
                config.sourcePaths = ['${workspaceFolder}/src/main/kotlin'];
            }
        }

        // 自动检测源码路径
        if (!config.sourcePaths || config.sourcePaths.length === 0) {
            config.sourcePaths = await this.detectSourcePaths(folder);
        }

        // 调试前构建（若启用）
        const debugConfig = vscode.workspace.getConfiguration('kotlin-debug');
        if (debugConfig.get<boolean>('buildBeforeDebug', false)) {
            const built = await this.runPreDebugBuild(folder);
            if (!built) {
                return undefined;  // 构建失败时中止调试
            }
        }

        // 验证必需的配置
        if (config.request === 'launch') {
            if (!config.command) {
                vscode.window.showErrorMessage('Command is required for launch mode. Provide the command to start your application with JDWP debug options.');
                return undefined;
            }
            if (!config.port) {
                vscode.window.showErrorMessage('Debug port is required for launch mode. This should match the port in your JDWP debug options.');
                return undefined;
            }

            // 启动用户的应用程序
            const launched = await this.launchApplication(config, folder);
            if (!launched) {
                return undefined;
            }

            // 将 launch 转换为 attach 请求
            config.request = 'attach';
            config.host = config.host || 'localhost';
        } else if (config.request === 'attach') {
            if (!config.port) {
                vscode.window.showErrorMessage('Debug port is required for attach');
                return undefined;
            }
        }

        return config;
    }

    /**
     * 自动检测项目的源码路径
     */
    private async detectSourcePaths(folder: vscode.WorkspaceFolder | undefined): Promise<string[]> {
        const sourcePaths: string[] = [];
        const workspacePath = folder?.uri.fsPath || vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
        
        if (!workspacePath) {
            return ['${workspaceFolder}/src/main/kotlin'];
        }

        // 常见的 Kotlin 源码路径
        const commonPaths = [
            'src/main/kotlin',
            'src/main/java',
            'src/kotlin',
            'src',
            'app/src/main/kotlin',
            'app/src/main/java'
        ];

        for (const relativePath of commonPaths) {
            const fullPath = path.join(workspacePath, relativePath);
            if (fs.existsSync(fullPath)) {
                sourcePaths.push(`\${workspaceFolder}/${relativePath}`);
            }
        }

        // 如果没有找到任何路径，使用默认路径
        if (sourcePaths.length === 0) {
            // 使用配置中的默认路径
            const config = vscode.workspace.getConfiguration('kotlin-debug');
            const defaultPaths = config.get<string[]>('defaultSourcePaths', ['${workspaceFolder}/src/main/kotlin']);
            return defaultPaths;
        }

        logChannel.appendLine(`[Extension] Auto-detected source paths: ${sourcePaths.join(', ')}`);
        return sourcePaths;
    }

    /**
     * 启动用户的应用程序
     */
    private async launchApplication(config: vscode.DebugConfiguration, folder: vscode.WorkspaceFolder | undefined): Promise<boolean> {
        const command = this.resolvePath(config.command as string, folder);
        const cwd = this.resolvePath(config.cwd || '${workspaceFolder}', folder);
        const env = config.env || {};
        const preLaunchWait = config.preLaunchWait || 10000;

        logChannel.appendLine(`[Extension] Launching application in terminal with command: ${command}`);
        logChannel.appendLine(`[Extension] Working directory: ${cwd}`);
        logChannel.appendLine(`[Extension] Pre-launch wait: ${preLaunchWait}ms`);

        try {
            // 停止之前的终端
            if (activeTerminal) {
                activeTerminal.dispose();
            }

            // 创建新终端
            const terminalOptions: vscode.TerminalOptions = {
                name: 'Kotlin Debug Target',
                cwd: cwd,
                env: env
            };

            activeTerminal = vscode.window.createTerminal(terminalOptions);
            activeTerminal.show(true);

            // 发送命令
            activeTerminal.sendText(command);

            // 等待应用程序启动并开始监听调试端口
            logChannel.appendLine(`[Extension] Waiting for application to start and open debug port ${config.port}...`);

            const port = config.port as number;
            const host = config.host || 'localhost';

            const checkInterval = 500;
            const maxAttempts = Math.ceil(preLaunchWait / checkInterval);

            let portReady = false;
            for (let attempt = 0; attempt < maxAttempts; attempt++) {
                // 尝试连接到调试端口
                const isPortOpen = await this.checkPort(host, port);
                if (isPortOpen) {
                    portReady = true;
                    logChannel.appendLine(`[Extension] Debug port ${port} is now open`);
                    break;
                }

                await new Promise(resolve => setTimeout(resolve, checkInterval));
            }

            if (!portReady) {
                logChannel.appendLine(`[Extension] Warning: Could not verify debug port ${port} is open, attempting to attach anyway...`);
            }

            logChannel.appendLine('[Extension] Application started, attaching debugger...');
            return true;
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            logChannel.appendLine(`[Extension] Failed to launch application: ${errorMessage}`);
            vscode.window.showErrorMessage(`Failed to launch application: ${errorMessage}`);
            return false;
        }
    }

    /**
     * 解析路径中的变量
     */
    private resolvePath(p: string, folder: vscode.WorkspaceFolder | undefined): string {
        if (folder && p.includes('${workspaceFolder}')) {
            return p.replace(/\$\{workspaceFolder\}/g, folder.uri.fsPath);
        }
        if (vscode.workspace.workspaceFolders && p.includes('${workspaceFolder}')) {
            return p.replace(/\$\{workspaceFolder\}/g, vscode.workspace.workspaceFolders[0].uri.fsPath);
        }
        return p;
    }

    /**
     * 检查端口是否可连接
     */
    private checkPort(host: string, port: number): Promise<boolean> {
        return new Promise((resolve) => {
            const socket = new net.Socket();
            socket.setTimeout(1000);
            
            socket.on('connect', () => {
                socket.destroy();
                resolve(true);
            });
            
            socket.on('timeout', () => {
                socket.destroy();
                resolve(false);
            });
            
            socket.on('error', () => {
                socket.destroy();
                resolve(false);
            });
            
            socket.connect(port, host);
        });
    }

    /**
     * 调试前构建：对 Gradle/Maven 项目执行编译，确保 .class 文件是最新的。
     * 返回 true 表示构建成功（或跳过），false 表示构建失败。
     */
    private async runPreDebugBuild(folder: vscode.WorkspaceFolder | undefined): Promise<boolean> {
        const workspacePath = folder?.uri.fsPath || vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
        if (!workspacePath) { return true; }

        // 判断项目类型
        const hasGradle = fs.existsSync(path.join(workspacePath, 'build.gradle.kts'))
            || fs.existsSync(path.join(workspacePath, 'build.gradle'));
        const hasMaven = fs.existsSync(path.join(workspacePath, 'pom.xml'));

        let buildCmd: string;
        if (hasGradle) {
            // gradle classes 比 build 更快（只编译，不打包/测试）
            buildCmd = process.platform === 'win32' ? 'gradlew.bat classes' : './gradlew classes';
        } else if (hasMaven) {
            buildCmd = 'mvn compile -q';
        } else {
            // 无法识别的项目类型，跳过
            return true;
        }

        logChannel.appendLine(`[Extension] Pre-debug build: ${buildCmd}`);

        return vscode.window.withProgress(
            { location: vscode.ProgressLocation.Notification, title: 'Building before debug...', cancellable: false },
            () => new Promise<boolean>((resolve) => {
                cp.exec(buildCmd, { cwd: workspacePath }, (error, stdout, stderr) => {
                    if (error) {
                        const msg = stderr || error.message;
                        logChannel.appendLine(`[Extension] Pre-debug build failed:\n${msg}`);
                        vscode.window.showErrorMessage(
                            `Pre-debug build failed. Check "Kotlin Debugger Logs" for details.\n${msg.substring(0, 200)}`
                        );
                        resolve(false);
                    } else {
                        logChannel.appendLine(`[Extension] Pre-debug build succeeded.`);
                        resolve(true);
                    }
                });
            })
        );
    }

    provideDebugConfigurations(
        folder: vscode.WorkspaceFolder | undefined,
        token?: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.DebugConfiguration[]> {
        return [
            {
                type: 'kotlin',
                request: 'launch',
                name: 'Kotlin: Launch and Debug',
                command: 'java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar ${workspaceFolder}/build/libs/your-app.jar',
                port: 5005,
                cwd: '${workspaceFolder}',
                sourcePaths: ['${workspaceFolder}/src/main/kotlin']
            },
            {
                type: 'kotlin',
                request: 'attach',
                name: 'Kotlin: Attach to JVM',
                host: 'localhost',
                port: 5005,
                sourcePaths: ['${workspaceFolder}/src/main/kotlin']
            }
        ];
    }
}

/**
 * 调试适配器描述符工厂
 * 负责启动 kotlin-debugger 进程并与之通信
 */
class KotlinDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {

    private context: vscode.ExtensionContext;
    private debuggerProcess: cp.ChildProcess | undefined;

    constructor(context: vscode.ExtensionContext) {
        this.context = context;
    }

    createDebugAdapterDescriptor(
        session: vscode.DebugSession,
        executable: vscode.DebugAdapterExecutable | undefined
    ): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {

        // 查找 kotlin-debugger JAR 文件
        const jarPath = this.findDebuggerJar();

        if (!jarPath) {
            vscode.window.showErrorMessage(
                'Cannot find kotlin-debugger JAR file. Please build the project first with ./gradlew build'
            );
            return undefined;
        }

        console.log(`Using kotlin-debugger JAR: ${jarPath}`);
        logChannel.appendLine(`[Extension] Using kotlin-debugger JAR: ${jarPath}`);

        // 解析 java 可执行文件路径（支持 javaHome 配置）
        const javaExecutable = this.resolveJavaExecutable();
        logChannel.appendLine(`[Extension] Using Java executable: ${javaExecutable}`);

        // 使用 java 启动 DAP 服务器，启用调试模式
        const args = [
            '-jar',
            jarPath,
            '--dap',
            '--debug'
        ];

        // 手动启动进程以便捕获 stderr 和崩溃信息
        this.debuggerProcess = cp.spawn(javaExecutable, args, {
            stdio: ['pipe', 'pipe', 'pipe']
        });

        const proc = this.debuggerProcess;

        // 捕获 stderr 输出（错误日志和崩溃信息）
        proc.stderr?.on('data', (data: Buffer) => {
            const message = data.toString();
            logChannel.appendLine(`[Debugger STDERR] ${message}`);
        });

        // 监听进程退出事件
        proc.on('exit', (code, signal) => {
            if (code !== 0 && code !== null) {
                logChannel.appendLine(`\n[Extension] ⚠️ Debugger process exited with code: ${code}`);
                vscode.window.showErrorMessage(`Kotlin Debugger crashed with exit code: ${code}. Check 'Kotlin Debugger Logs' for details.`);
            } else if (signal) {
                logChannel.appendLine(`\n[Extension] ⚠️ Debugger process was killed by signal: ${signal}`);
                vscode.window.showErrorMessage(`Kotlin Debugger was killed by signal: ${signal}. Check 'Kotlin Debugger Logs' for details.`);
            } else {
                logChannel.appendLine(`\n[Extension] Debugger process exited normally.`);
            }
        });

        // 监听进程错误事件
        proc.on('error', (err) => {
            logChannel.appendLine(`\n[Extension] ❌ Failed to start debugger process: ${err.message}`);
            logChannel.appendLine(`Stack: ${err.stack}`);
            vscode.window.showErrorMessage(`Failed to start Kotlin Debugger: ${err.message}`);
        });

        // 使用 DebugAdapterInlineImplementation 包装进程的 stdin/stdout
        return new vscode.DebugAdapterInlineImplementation(
            new DebugAdapterStreamWrapper(proc)
        );
    }

    /**
     * 查找调试器 JAR 文件
     */
    private findDebuggerJar(): string | undefined {
        // 搜索路径
        const searchPaths: string[] = [];

        // 1. 工作区路径
        if (vscode.workspace.workspaceFolders) {
            for (const folder of vscode.workspace.workspaceFolders) {
                searchPaths.push(
                    path.join(folder.uri.fsPath, 'release', 'kotlin-debugger-1.0-SNAPSHOT-all.jar'),
                    path.join(folder.uri.fsPath, 'build', 'libs', 'kotlin-debugger-1.0-SNAPSHOT-all.jar'),
                    path.join(folder.uri.fsPath, 'kotlin-debugger.jar')
                );
            }
        }

        // 2. 扩展目录
        const extensionPath = this.context.extensionPath;
        searchPaths.push(
            path.join(extensionPath, 'kotlin-debugger.jar'),
            path.join(extensionPath, 'kotlin-debugger-1.0-SNAPSHOT-all.jar'),
            path.join(extensionPath, '..', '..', 'release', 'kotlin-debugger-1.0-SNAPSHOT-all.jar'),
            path.join(extensionPath, '..', 'build', 'libs', 'kotlin-debugger-1.0-SNAPSHOT-all.jar')
        );

        // 3. 扩展配置的路径
        const configuredPath = vscode.workspace.getConfiguration('kotlin-debug').get<string>('debuggerJarPath');
        if (configuredPath) {
            searchPaths.unshift(configuredPath);
        }

        // 查找存在的文件
        for (const p of searchPaths) {
            const resolvedPath = this.resolvePath(p);
            if (fs.existsSync(resolvedPath)) {
                return resolvedPath;
            }
        }

        return undefined;
    }

    /**
     * 解析 java 可执行文件路径。
     * 优先使用 kotlin-debug.javaHome 配置，其次 JAVA_HOME 环境变量，最后回退到 PATH 中的 java。
     */
    private resolveJavaExecutable(): string {
        const config = vscode.workspace.getConfiguration('kotlin-debug');
        const javaHome = config.get<string>('javaHome', '').trim();
        if (javaHome) {
            const javaExe = path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
            if (fs.existsSync(javaExe)) {
                return javaExe;
            }
            logChannel.appendLine(`[Extension] Warning: javaHome set to "${javaHome}" but java not found there, falling back.`);
        }
        // 尝试 JAVA_HOME 环境变量
        const envJavaHome = process.env['JAVA_HOME'];
        if (envJavaHome) {
            const javaExe = path.join(envJavaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
            if (fs.existsSync(javaExe)) {
                return javaExe;
            }
        }
        // 最终回退：PATH 中的 java
        return 'java';
    }

    private resolvePath(p: string): string {
        if (vscode.workspace.workspaceFolders && p.includes('${workspaceFolder}')) {
            return p.replace('${workspaceFolder}', vscode.workspace.workspaceFolders[0].uri.fsPath);
        }
        return p;
    }
}

/**
 * 包装子进程的 stdin/stdout 以实现 DebugAdapter 接口
 * 这样可以让 VS Code 通过 DAP 协议与调试器通信，同时我们可以捕获 stderr
 */
class DebugAdapterStreamWrapper implements vscode.DebugAdapter {

    private process: cp.ChildProcess;
    private _onDidSendMessage = new vscode.EventEmitter<vscode.DebugProtocolMessage>();
    readonly onDidSendMessage: vscode.Event<vscode.DebugProtocolMessage> = this._onDidSendMessage.event;

    private buffer: string = '';
    private contentLength: number = -1;

    constructor(process: cp.ChildProcess) {
        this.process = process;

        // 解析来自调试器的 DAP 消息
        this.process.stdout?.on('data', (data: Buffer) => {
            this.handleData(data.toString());
        });

        this.process.stdout?.on('error', (err) => {
            logChannel.appendLine(`[Extension] stdout error: ${err.message}`);
        });
    }

    /**
     * 处理来自调试器的数据，解析 DAP 消息
     */
    private handleData(data: string): void {
        this.buffer += data;

        while (true) {
            if (this.contentLength === -1) {
                // 查找 Content-Length 头
                const headerEnd = this.buffer.indexOf('\r\n\r\n');
                if (headerEnd === -1) {
                    break;
                }

                const header = this.buffer.substring(0, headerEnd);
                const match = header.match(/Content-Length:\s*(\d+)/i);
                if (match) {
                    this.contentLength = parseInt(match[1], 10);
                    this.buffer = this.buffer.substring(headerEnd + 4);
                } else {
                    // 无效的头，跳过
                    logChannel.appendLine(`[Extension] Invalid DAP header: ${header}`);
                    this.buffer = this.buffer.substring(headerEnd + 4);
                    continue;
                }
            }

            if (this.contentLength !== -1 && this.buffer.length >= this.contentLength) {
                const body = this.buffer.substring(0, this.contentLength);
                this.buffer = this.buffer.substring(this.contentLength);
                this.contentLength = -1;

                try {
                    const message = JSON.parse(body);
                    this._onDidSendMessage.fire(message);
                } catch (e) {
                    logChannel.appendLine(`[Extension] Failed to parse DAP message: ${body}`);
                }
            } else {
                break;
            }
        }
    }

    /**
     * 发送消息到调试器
     */
    handleMessage(message: vscode.DebugProtocolMessage): void {
        const json = JSON.stringify(message);
        const header = `Content-Length: ${Buffer.byteLength(json, 'utf8')}\r\n\r\n`;

        try {
            this.process.stdin?.write(header + json, 'utf8');
        } catch (e) {
            logChannel.appendLine(`[Extension] Failed to send message to debugger: ${e}`);
        }
    }

    dispose(): void {
        this._onDidSendMessage.dispose();
        if (this.process && !this.process.killed) {
            this.process.kill();
        }
    }
}

/**
 * 代码透镜提供者 - 在 main 函数上显示 "Debug" 按钮，在 @Test 函数上显示测试按钮
 */
class KotlinDebugCodeLensProvider implements vscode.CodeLensProvider {
    private _onDidChangeCodeLenses = new vscode.EventEmitter<void>();
    public readonly onDidChangeCodeLenses = this._onDidChangeCodeLenses.event;

    // main 函数匹配：支持 fun main( 和 @JvmStatic fun main( 同行写法
    private readonly mainFunctionPattern = /^\s*(?:@\S+\s+)*fun\s+main\s*\(/;
    // @Test 注解匹配（JUnit4/5）
    private readonly testAnnotationPattern = /^\s*@(?:Test|org\.junit\.(?:jupiter\.api\.)?Test)\b/;
    // fun 声明匹配（用于 @Test 后检查下一有效行）
    private readonly funDeclPattern = /^\s*(?:@\S+\s+)*fun\s+(\w+)\s*\(/;

    provideCodeLenses(document: vscode.TextDocument): vscode.CodeLens[] {
        // 检查配置是否启用代码透镜
        const config = vscode.workspace.getConfiguration('kotlin-debug');
        if (!config.get<boolean>('enableCodeLens', true)) {
            return [];
        }

        const codeLenses: vscode.CodeLens[] = [];
        const lines = document.getText().split('\n');

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];

            // 跳过注释行
            const trimmed = line.trim();
            if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
                continue;
            }

            // 检测 main 函数（包括 @JvmStatic fun main 同行写法）
            if (this.mainFunctionPattern.test(line)) {
                const range = new vscode.Range(i, 0, i, line.length);
                codeLenses.push(new vscode.CodeLens(range, {
                    title: '$(debug-start) Debug',
                    command: 'kotlin-debug.debugMain',
                    arguments: [{ file: document.uri.fsPath, line: i + 1 }],
                    tooltip: 'Debug this Kotlin main function'
                }));
                codeLenses.push(new vscode.CodeLens(range, {
                    title: '$(play) Run',
                    command: 'workbench.action.debug.run',
                    tooltip: 'Run without debugging'
                }));
                continue;
            }

            // 检测 @Test 注解：在此行找到注解后，向下找最近的 fun 声明
            if (this.testAnnotationPattern.test(line)) {
                const funLine = this.findNextFunLine(lines, i + 1);
                if (funLine !== -1) {
                    const funMatch = this.funDeclPattern.exec(lines[funLine]);
                    if (funMatch) {
                        const methodName = funMatch[1];
                        const range = new vscode.Range(funLine, 0, funLine, lines[funLine].length);
                        // 获取类名（向上查找 class 声明）
                        const className = this.findEnclosingClassName(lines, funLine) || '';
                        codeLenses.push(new vscode.CodeLens(range, {
                            title: '$(beaker) Run Test',
                            command: 'kotlin-debug.runTest',
                            arguments: [{ file: document.uri.fsPath, className, methodName }],
                            tooltip: `Run test: ${methodName}`
                        }));
                        codeLenses.push(new vscode.CodeLens(range, {
                            title: '$(debug-start) Debug Test',
                            command: 'kotlin-debug.debugTest',
                            arguments: [{ file: document.uri.fsPath, className, methodName }],
                            tooltip: `Debug test: ${methodName}`
                        }));
                    }
                }
            }
        }

        return codeLenses;
    }

    /** 从指定行向下查找最近的 fun 声明行（跳过空行和注解行，最多向下 5 行） */
    private findNextFunLine(lines: string[], startLine: number): number {
        for (let i = startLine; i < Math.min(startLine + 5, lines.length); i++) {
            const t = lines[i].trim();
            if (t.startsWith('fun ') || /^(?:@\S+\s+)*fun\s/.test(t)) {
                return i;
            }
            // 遇到非空、非注解的行就停止
            if (t && !t.startsWith('@') && !t.startsWith('//') && !t.startsWith('*')) {
                break;
            }
        }
        return -1;
    }

    /** 向上查找包含此函数的类名 */
    private findEnclosingClassName(lines: string[], lineIndex: number): string | undefined {
        const classPattern = /^\s*(?:(?:open|abstract|data|sealed|inner|private|public|protected|internal)\s+)*class\s+(\w+)/;
        for (let i = lineIndex; i >= 0; i--) {
            const match = classPattern.exec(lines[i]);
            if (match) {
                return match[1];
            }
        }
        return undefined;
    }

    resolveCodeLens(codeLens: vscode.CodeLens): vscode.CodeLens {
        return codeLens;
    }

    refresh(): void {
        this._onDidChangeCodeLenses.fire();
    }
}

/**
 * 悬停提供者 - 调试时显示变量值
 */
class KotlinDebugHoverProvider implements vscode.HoverProvider {
    async provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        _token: vscode.CancellationToken
    ): Promise<vscode.Hover | undefined> {
        // 检查配置是否启用悬停求值
        const config = vscode.workspace.getConfiguration('kotlin-debug');
        if (!config.get<boolean>('enableHoverEvaluation', true)) {
            return undefined;
        }

        // 只在调试时提供悬停信息
        if (!isDebugging || !vscode.debug.activeDebugSession) {
            return undefined;
        }

        // 获取光标位置的单词
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        
        // 尝试求值表达式
        try {
            const response = await vscode.debug.activeDebugSession.customRequest('evaluate', {
                expression: word,
                context: 'hover'
            });

            if (response && response.result !== undefined) {
                const markdown = new vscode.MarkdownString();
                markdown.appendCodeblock(`${word} = ${response.result}`, 'kotlin');
                if (response.type) {
                    markdown.appendText(`\n**Type:** ${response.type}`);
                }
                return new vscode.Hover(markdown, wordRange);
            }
        } catch (e) {
            // Expression evaluation failed - this is normal for non-evaluable expressions
            // Only log if it's an unexpected error type
            if (e instanceof Error && !e.message.includes('not available') && !e.message.includes('not found')) {
                logChannel.appendLine(`[Extension] Hover evaluation failed for '${word}': ${e.message}`);
            }
        }

        return undefined;
    }
}

// ==================== 内联值提供者 ====================

/**
 * Kotlin 调试内联值提供者
 * 调试暂停时，在编辑器行内显示变量的当前值。
 * 使用 InlineValueVariableLookup，让 VS Code 自动通过 DAP 协议查询变量值，
 * 无需后端额外实现，对运行时性能无影响。
 */
class KotlinInlineValuesProvider implements vscode.InlineValuesProvider {
    provideInlineValues(
        document: vscode.TextDocument,
        viewPort: vscode.Range,
        context: vscode.InlineValueContext
    ): vscode.ProviderResult<vscode.InlineValue[]> {
        const inlineValues: vscode.InlineValue[] = [];
        const stoppedLine = context.stoppedLocation.end.line;

        // 只在暂停行附近（±10 行内）展示内联值，避免噪音过多
        const startLine = Math.max(viewPort.start.line, stoppedLine - 10);
        const endLine = Math.min(viewPort.end.line, stoppedLine + 2);

        for (let i = startLine; i <= endLine; i++) {
            const line = document.lineAt(i);
            const text = line.text;

            // 跳过注释行和空行
            const trimmed = text.trim();
            if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
                continue;
            }

            // 提取行内标识符，交给 VS Code 去查询其当前值
            const identifierRegex = /\b([a-zA-Z_$][a-zA-Z0-9_$]*)\b/g;
            let match: RegExpExecArray | null;
            while ((match = identifierRegex.exec(text)) !== null) {
                const name = match[1];
                // 过滤 Kotlin 关键字和常见类型名，减少无效查询
                if (!KOTLIN_KEYWORDS.has(name) && !COMMON_TYPE_NAMES.has(name)) {
                    const range = new vscode.Range(i, match.index, i, match.index + name.length);
                    // caseSensitiveLookup=false，兼容 Kotlin 编译后变量名大小写变化
                    inlineValues.push(new vscode.InlineValueVariableLookup(range, name, false));
                }
            }
        }

        return inlineValues;
    }
}

/** Kotlin 关键字集合（用于过滤内联值） */
const KOTLIN_KEYWORDS = new Set([
    'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun',
    'if', 'in', 'interface', 'is', 'null', 'object', 'package', 'return',
    'super', 'this', 'throw', 'true', 'try', 'typealias', 'typeof', 'val',
    'var', 'when', 'while', 'by', 'catch', 'constructor', 'delegate', 'dynamic',
    'field', 'file', 'finally', 'get', 'import', 'init', 'param', 'property',
    'receiver', 'set', 'setparam', 'where', 'actual', 'abstract', 'annotation',
    'companion', 'crossinline', 'data', 'enum', 'expect', 'external', 'final',
    'infix', 'inline', 'inner', 'internal', 'lateinit', 'noinline', 'open',
    'operator', 'out', 'override', 'private', 'protected', 'public', 'reified',
    'sealed', 'suspend', 'tailrec', 'vararg', 'it'
]);

/** 常见类型名集合（减少无效变量查询） */
const COMMON_TYPE_NAMES = new Set([
    'String', 'Int', 'Long', 'Double', 'Float', 'Boolean', 'Byte', 'Short',
    'Char', 'Unit', 'Any', 'Nothing', 'List', 'Map', 'Set', 'Array',
    'ArrayList', 'HashMap', 'HashSet', 'MutableList', 'MutableMap', 'MutableSet',
    'Pair', 'Triple', 'Result', 'Exception', 'Error', 'Throwable',
    'println', 'print', 'TODO', 'require', 'check', 'assert', 'run', 'let',
    'also', 'apply', 'with', 'repeat', 'lazy', 'listOf', 'mapOf', 'setOf',
    'arrayOf', 'emptyList', 'emptyMap', 'emptySet'
]);

// ==================== 协程视图 ====================

/**
 * 协程信息数据结构（与后端 CoroutineHandler 对应）
 */
interface CoroutineData {
    id: number;
    name: string;
    state: string;
    dispatcher: string;
    description: string;
    isSuspended: boolean;
    isRunning: boolean;
    stackFrames: Array<{
        className: string;
        methodName: string;
        isCreationFrame: boolean;
        file?: string;
        line?: number;
    }>;
}

/**
 * 协程树节点
 */
class CoroutineTreeItem extends vscode.TreeItem {
    constructor(
        public readonly label: string,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly coroutine?: CoroutineData,
        public readonly stackFrame?: CoroutineData['stackFrames'][0]
    ) {
        super(label, collapsibleState);
    }
}

/**
 * 协程视图数据提供者
 * 通过 customRequest('getCoroutines') 获取协程数据
 */
class CoroutineViewProvider implements vscode.TreeDataProvider<CoroutineTreeItem> {
    private _onDidChangeTreeData = new vscode.EventEmitter<CoroutineTreeItem | undefined | void>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    private coroutines: CoroutineData[] = [];
    private statusMessage: string = '';
    private probesInstalled: boolean = false;

    refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    /**
     * 从调试会话获取协程数据并刷新视图
     */
    async fetchAndRefresh(): Promise<void> {
        const session = vscode.debug.activeDebugSession;
        if (!session || session.type !== 'kotlin') {
            this.coroutines = [];
            this.statusMessage = '';
            this.probesInstalled = false;
            this.refresh();
            return;
        }

        try {
            const response = await session.customRequest('getCoroutines', {});
            this.coroutines = response.coroutines || [];
            this.probesInstalled = response.probesInstalled ?? false;
            this.statusMessage = response.statusMessage || '';
        } catch (e) {
            this.coroutines = [];
            this.statusMessage = 'Failed to fetch coroutines';
        }
        this.refresh();
    }

    getTreeItem(element: CoroutineTreeItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: CoroutineTreeItem): CoroutineTreeItem[] {
        if (!isDebugging) {
            return [new CoroutineTreeItem('Not debugging', vscode.TreeItemCollapsibleState.None)];
        }

        if (!element) {
            // 根节点：显示所有协程
            if (this.coroutines.length === 0) {
                const msg = this.probesInstalled
                    ? 'No coroutines found'
                    : (this.statusMessage || 'kotlinx-coroutines-debug not on classpath');
                return [new CoroutineTreeItem(msg, vscode.TreeItemCollapsibleState.None)];
            }
            return this.coroutines.map(c => {
                const hasFrames = c.stackFrames && c.stackFrames.length > 0;
                const item = new CoroutineTreeItem(
                    c.description,
                    hasFrames ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None,
                    c
                );
                // 根据状态设置图标
                item.iconPath = new vscode.ThemeIcon(
                    c.isRunning ? 'debug-alt' : c.isSuspended ? 'debug-pause' : 'circle-outline'
                );
                item.tooltip = `ID: ${c.id}\nState: ${c.state}\nDispatcher: ${c.dispatcher || 'N/A'}`;
                item.contextValue = 'coroutine';
                return item;
            });
        }

        // 子节点：协程的调用栈帧
        if (element.coroutine) {
            return (element.coroutine.stackFrames || []).map(frame => {
                const label = `${lastPart(frame.className)}.${frame.methodName}()`;
                const item = new CoroutineTreeItem(
                    label,
                    vscode.TreeItemCollapsibleState.None,
                    undefined,
                    frame
                );
                item.iconPath = new vscode.ThemeIcon('symbol-method');
                if (frame.file && frame.line) {
                    item.description = `${frame.file}:${frame.line}`;
                    item.command = {
                        command: 'kotlin-debug.coroutine.openFrame',
                        title: 'Open File',
                        arguments: [frame]
                    };
                }
                return item;
            });
        }

        return [];
    }
}

/** 取类名最后一段（去掉包名前缀） */
function lastPart(className: string): string {
    const idx = className.lastIndexOf('.');
    return idx >= 0 ? className.substring(idx + 1) : className;
}

// ==================== Hot Code Replace ====================

/**
 * 触发 Hot Code Replace
 * 扫描工作区的 .class 文件并发送 redefineClasses 请求
 */
async function triggerHotCodeReplace(outputFiles?: string[]): Promise<void> {
    const session = vscode.debug.activeDebugSession;
    if (!session || session.type !== 'kotlin') {
        vscode.window.showWarningMessage('No active Kotlin debug session');
        return;
    }

    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        return;
    }

    // 查找 .class 文件（如果没有指定，则扫描 build/classes）
    let classFiles: string[];
    if (outputFiles && outputFiles.length > 0) {
        classFiles = outputFiles;
    } else {
        const buildDirs = [
            path.join(workspaceFolder.uri.fsPath, 'build', 'classes'),
            path.join(workspaceFolder.uri.fsPath, 'out'),
            path.join(workspaceFolder.uri.fsPath, 'target', 'classes')
        ];
        classFiles = [];
        for (const dir of buildDirs) {
            if (fs.existsSync(dir)) {
                collectClassFiles(dir, classFiles);
            }
        }
    }

    if (classFiles.length === 0) {
        vscode.window.showWarningMessage('No .class files found. Please build the project first.');
        return;
    }

    // 构建 redefineClasses 请求：将 .class 文件路径转换为类名
    const classes = classFiles.map(filePath => {
        // 推断类名：从 build/classes/kotlin/main/ 或 out/ 后的路径
        const className = inferClassName(filePath, workspaceFolder.uri.fsPath);
        return { className, classFile: filePath };
    }).filter(c => c.className);

    if (classes.length === 0) {
        vscode.window.showWarningMessage('Could not infer class names from .class files');
        return;
    }

    logChannel.appendLine(`[HCR] Triggering hot code replace for ${classes.length} class(es)...`);

    try {
        const result = await session.customRequest('redefineClasses', { classes });
        if (result.success) {
            const count = result.reloadedClasses?.length || 0;
            vscode.window.setStatusBarMessage(`$(check) Hot Replaced ${count} class(es)`, 3000);
            logChannel.appendLine(`[HCR] Success: ${result.message}`);
        } else {
            vscode.window.showWarningMessage(`Hot Code Replace failed: ${result.message}`);
            logChannel.appendLine(`[HCR] Failed: ${result.message}`);
        }
    } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        logChannel.appendLine(`[HCR] Error: ${msg}`);
        vscode.window.showErrorMessage(`Hot Code Replace error: ${msg}`);
    }
}

/**
 * 递归收集目录下的所有 .class 文件
 */
function collectClassFiles(dir: string, result: string[]): void {
    try {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
            const fullPath = path.join(dir, entry.name);
            if (entry.isDirectory()) {
                collectClassFiles(fullPath, result);
            } else if (entry.isFile() && entry.name.endsWith('.class')) {
                result.push(fullPath);
            }
        }
    } catch {
        // 忽略无法访问的目录
    }
}

/**
 * 从 .class 文件路径推断全限定类名
 */
function inferClassName(classFile: string, workspaceRoot: string): string {
    // 标准化路径分隔符
    const normalized = classFile.replace(/\\/g, '/');
    const wsNorm = workspaceRoot.replace(/\\/g, '/');

    // 尝试从常见目录结构中提取包路径
    const markers = [
        '/build/classes/kotlin/main/',
        '/build/classes/kotlin/test/',
        '/build/classes/java/main/',
        '/out/production/',
        '/target/classes/'
    ];

    for (const marker of markers) {
        const idx = normalized.indexOf(marker);
        if (idx >= 0) {
            const relative = normalized.substring(idx + marker.length);
            return relative.replace(/\.class$/, '').replace(/\//g, '.').replace(/\$/g, '$');
        }
    }

    // 尝试从工作区根路径推断
    if (normalized.startsWith(wsNorm)) {
        const parts = normalized.substring(wsNorm.length + 1).split('/');
        // 找到 classes 或 out 目录后的部分
        const classesIdx = parts.findIndex(p => p === 'classes' || p === 'out' || p === 'target');
        if (classesIdx >= 0) {
            return parts.slice(classesIdx + 1).join('.').replace(/\.class$/, '');
        }
    }

    return '';
}

// ==================== @Test CodeLens 测试运行 ====================

/**
 * 运行或调试单个 Kotlin 测试方法。
 * 对 Gradle 项目：使用 `./gradlew test --tests "ClassName.methodName"`
 * 调试模式：先启动带 JDWP 参数的 Gradle 测试，再 attach 调试器。
 *
 * @param args 测试参数（文件路径、类名、方法名）
 * @param debug 是否以调试模式运行
 */
async function runKotlinTest(
    args: { file: string; className: string; methodName: string },
    debug: boolean
): Promise<void> {
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        vscode.window.showErrorMessage('No workspace folder open');
        return;
    }

    const workspacePath = workspaceFolder.uri.fsPath;
    const hasGradle = fs.existsSync(path.join(workspacePath, 'build.gradle.kts'))
        || fs.existsSync(path.join(workspacePath, 'build.gradle'));

    if (!hasGradle) {
        vscode.window.showWarningMessage(
            'Test runner currently only supports Gradle projects. Please run tests manually.'
        );
        return;
    }

    // 拼接测试过滤器：若有类名则 "ClassName.methodName"，否则只用方法名
    const testFilter = args.className
        ? `${args.className}.${args.methodName}`
        : args.methodName;

    const gradlewCmd = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

    if (debug) {
        // 调试模式：注入 JDWP 参数，然后 attach 调试器
        const debugPort = 5006;
        const jvmArgs = `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=${debugPort}`;
        const cmd = `${gradlewCmd} test --tests "${testFilter}" --info -Dorg.gradle.jvmargs="${jvmArgs}"`;

        const terminal = vscode.window.createTerminal({
            name: `Debug Test: ${args.methodName}`,
            cwd: workspacePath
        });
        terminal.show(true);
        terminal.sendText(cmd);
        logChannel.appendLine(`[Test] Launching debug test: ${cmd}`);

        // 等待调试端口就绪后自动 attach
        const debugConfig: vscode.DebugConfiguration = {
            type: 'kotlin',
            request: 'attach',
            name: `Debug Test: ${args.methodName}`,
            host: 'localhost',
            port: debugPort,
            sourcePaths: ['${workspaceFolder}/src/main/kotlin', '${workspaceFolder}/src/test/kotlin']
        };
        // 延迟 3 秒等待 JVM 启动（Gradle 启动时间较长）
        setTimeout(async () => {
            await vscode.debug.startDebugging(workspaceFolder, debugConfig);
        }, 3000);
    } else {
        // 普通运行模式：在集成终端中执行
        const cmd = `${gradlewCmd} test --tests "${testFilter}"`;
        const terminal = vscode.window.createTerminal({
            name: `Run Test: ${args.methodName}`,
            cwd: workspacePath
        });
        terminal.show(true);
        terminal.sendText(cmd);
        logChannel.appendLine(`[Test] Running test: ${cmd}`);
    }
}

