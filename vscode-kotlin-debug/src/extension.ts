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

// 用于跟踪通过launch模式启动的应用进程
let launchedAppProcess: cp.ChildProcess | undefined;

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

    // 注册悬停提供者用于调试时显示变量值
    context.subscriptions.push(
        vscode.languages.registerHoverProvider(
            { language: 'kotlin', scheme: 'file' },
            new KotlinDebugHoverProvider()
        )
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

    // 监听调试会话结束事件
    context.subscriptions.push(
        vscode.debug.onDidTerminateDebugSession((session) => {
            if (session.type === 'kotlin') {
                isDebugging = false;
                updateStatusBar('ready');
                logChannel.appendLine('\n=== Debug Session Ended ===');
                stopLogFileWatcher();
                stopLaunchedApp();
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
            // 移除可能的注释
            const jsonContent = content.replace(/\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '');
            launchJson = JSON.parse(jsonContent);
        } catch {
            launchJson = { version: '0.2.0', configurations: [] };
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
 * 停止通过launch模式启动的应用进程
 */
function stopLaunchedApp() {
    if (launchedAppProcess && !launchedAppProcess.killed) {
        logChannel.appendLine('[Extension] Stopping launched application process...');
        launchedAppProcess.kill();
        launchedAppProcess = undefined;
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
                const stats = fs.statSync(fullPath);
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
        const command = config.command as string;
        const cwd = this.resolvePath(config.cwd || '${workspaceFolder}', folder);
        const env = config.env || {};
        const preLaunchWait = config.preLaunchWait || 2000;

        logChannel.appendLine(`[Extension] Launching application with command: ${command}`);
        logChannel.appendLine(`[Extension] Working directory: ${cwd}`);
        logChannel.appendLine(`[Extension] Pre-launch wait: ${preLaunchWait}ms`);

        try {
            // 停止之前启动的进程
            if (launchedAppProcess && !launchedAppProcess.killed) {
                launchedAppProcess.kill();
            }

            // 启动用户的应用程序
            launchedAppProcess = cp.spawn(command, [], {
                cwd: cwd,
                shell: true,
                env: { ...process.env, ...env },
                stdio: ['ignore', 'pipe', 'pipe']
            });

            // 捕获应用程序的输出
            launchedAppProcess.stdout?.on('data', (data: Buffer) => {
                const message = data.toString().trim();
                if (message) {
                    logChannel.appendLine(`[App STDOUT] ${message}`);
                }
            });

            launchedAppProcess.stderr?.on('data', (data: Buffer) => {
                const message = data.toString().trim();
                if (message) {
                    logChannel.appendLine(`[App STDERR] ${message}`);
                }
            });

            launchedAppProcess.on('exit', (code, signal) => {
                if (code !== null && code !== 0) {
                    logChannel.appendLine(`[Extension] Application exited with code: ${code}`);
                } else if (signal) {
                    logChannel.appendLine(`[Extension] Application was killed by signal: ${signal}`);
                } else {
                    logChannel.appendLine('[Extension] Application exited normally');
                }
                launchedAppProcess = undefined;
            });

            launchedAppProcess.on('error', (err) => {
                logChannel.appendLine(`[Extension] Failed to start application: ${err.message}`);
                vscode.window.showErrorMessage(`Failed to start application: ${err.message}`);
            });

            // 等待应用程序启动并开始监听调试端口
            logChannel.appendLine(`[Extension] Waiting for application to start and open debug port ${config.port}...`);
            
            const port = config.port as number;
            const host = config.host || 'localhost';
            const maxWaitTime = preLaunchWait;
            const checkInterval = 500;
            const maxAttempts = Math.ceil(maxWaitTime / checkInterval);
            
            let portReady = false;
            for (let attempt = 0; attempt < maxAttempts; attempt++) {
                // 检查进程是否仍在运行
                if (!launchedAppProcess || launchedAppProcess.killed || launchedAppProcess.exitCode !== null) {
                    const exitCode = launchedAppProcess?.exitCode ?? 'unknown';
                    vscode.window.showErrorMessage(`Application exited prematurely with code: ${exitCode}`);
                    return false;
                }
                
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

        // 使用 java 启动 DAP 服务器，启用调试模式
        const args = [
            '-jar',
            jarPath,
            '--dap',
            '--debug'
        ];

        // 手动启动进程以便捕获 stderr 和崩溃信息
        this.debuggerProcess = cp.spawn('java', args, {
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
 * 代码透镜提供者 - 在 main 函数上显示 "Debug" 按钮
 */
class KotlinDebugCodeLensProvider implements vscode.CodeLensProvider {
    private _onDidChangeCodeLenses = new vscode.EventEmitter<void>();
    public readonly onDidChangeCodeLenses = this._onDidChangeCodeLenses.event;

    // main 函数的正则表达式模式
    private readonly mainFunctionPattern = /^\s*fun\s+main\s*\(/;

    provideCodeLenses(document: vscode.TextDocument): vscode.CodeLens[] {
        // 检查配置是否启用代码透镜
        const config = vscode.workspace.getConfiguration('kotlin-debug');
        if (!config.get<boolean>('enableCodeLens', true)) {
            return [];
        }

        const codeLenses: vscode.CodeLens[] = [];
        const text = document.getText();
        const lines = text.split('\n');

        for (let i = 0; i < lines.length; i++) {
            if (this.mainFunctionPattern.test(lines[i])) {
                const range = new vscode.Range(i, 0, i, lines[i].length);
                
                // Debug 按钮
                codeLenses.push(new vscode.CodeLens(range, {
                    title: '$(debug-start) Debug',
                    command: 'kotlin-debug.debugMain',
                    arguments: [{ file: document.uri.fsPath, line: i + 1 }],
                    tooltip: 'Debug this Kotlin main function'
                }));

                // Run 按钮（不带调试）
                codeLenses.push(new vscode.CodeLens(range, {
                    title: '$(play) Run',
                    command: 'workbench.action.debug.run',
                    tooltip: 'Run without debugging'
                }));
            }
        }

        return codeLenses;
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
        } catch {
            // 表达式求值失败，返回 undefined
        }

        return undefined;
    }
}
