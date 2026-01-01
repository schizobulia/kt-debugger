import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';
import * as os from 'os';

// 全局日志输出通道
let logChannel: vscode.OutputChannel;
let logFileWatcher: fs.StatWatcher | undefined;
let watchedLogFile: string | undefined;

export function activate(context: vscode.ExtensionContext) {
    console.log('Kotlin Debug extension is now active');

    // 创建全局输出通道用于显示日志
    logChannel = vscode.window.createOutputChannel('Kotlin Debugger Logs');
    context.subscriptions.push(logChannel);

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

    // 监听调试会话开始事件
    context.subscriptions.push(
        vscode.debug.onDidStartDebugSession((session) => {
            if (session.type === 'kotlin') {
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
                logChannel.appendLine('\n=== Debug Session Ended ===');
                stopLogFileWatcher();
            }
        })
    );
}

export function deactivate() {
    stopLogFileWatcher();
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

    resolveDebugConfiguration(
        folder: vscode.WorkspaceFolder | undefined,
        config: vscode.DebugConfiguration,
        token?: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.DebugConfiguration> {

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

        // 验证必需的配置
        if (config.request === 'attach') {
            if (!config.port) {
                return vscode.window.showErrorMessage('Debug port is required for attach').then(_ => {
                    return undefined;
                });
            }
        }

        return config;
    }

    provideDebugConfigurations(
        folder: vscode.WorkspaceFolder | undefined,
        token?: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.DebugConfiguration[]> {
        return [
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

        // 使用 java 启动 DAP 服务器，启用调试模式
        const args = [
            '-jar',
            jarPath,
            '--dap',
            '--debug'
        ];

        return new vscode.DebugAdapterExecutable('java', args);
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
