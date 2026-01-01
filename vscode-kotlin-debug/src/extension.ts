import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

export function activate(context: vscode.ExtensionContext) {
    console.log('Kotlin Debug extension is now active');

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
}

export function deactivate() {
    // 清理资源
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
                'Cannot find kotlin-debugger JAR file. Please build the project first with ./gradlew fatJar'
            );
            return undefined;
        }

        console.log(`Using kotlin-debugger JAR: ${jarPath}`);

        // 使用 java 启动 DAP 服务器
        const args = [
            '-jar',
            jarPath,
            '--dap'
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
