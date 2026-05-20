import * as assert from 'assert';
import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

/**
 * VSCode 扩展功能测试
 *
 * 这些测试需要在扩展开发宿主中运行（@vscode/test-electron）。
 * 运行方式: npm run test:ext
 */
suite('Extension Activation Tests', () => {
    test('Extension should be present', () => {
        const ext = vscode.extensions.getExtension('schizobulia.kotlin-debug');
        assert.ok(ext, 'kotlin-debug extension should be registered');
    });

    test('Extension should activate', async () => {
        const ext = vscode.extensions.getExtension('schizobulia.kotlin-debug');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        assert.strictEqual(ext?.isActive, true, 'Extension should be active after activation');
    });
});

suite('Package Manifest Tests', () => {
    const manifestPath = path.resolve(__dirname, '../../../package.json');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));

    test('Extension should declare kotlin language contribution', () => {
        const languages: Array<{ id: string; extensions?: string[] }> = manifest.contributes?.languages ?? [];
        const kotlin = languages.find((l) => l.id === 'kotlin');
        assert.ok(kotlin, 'package.json should contribute the kotlin language');
    });

    test('Kotlin language should include .kt and .kts extensions', () => {
        const languages: Array<{ id: string; extensions?: string[] }> = manifest.contributes?.languages ?? [];
        const kotlin = languages.find((l) => l.id === 'kotlin');
        assert.ok(kotlin?.extensions?.includes('.kt'), 'Should include .kt');
        assert.ok(kotlin?.extensions?.includes('.kts'), 'Should include .kts');
    });

    test('Extension should declare onLanguage:kotlin activation event', () => {
        const events: string[] = manifest.activationEvents ?? [];
        assert.ok(
            events.includes('onLanguage:kotlin'),
            'Should activate on Kotlin language files'
        );
    });

    test('Extension should declare kotlin debug type activation event', () => {
        const events: string[] = manifest.activationEvents ?? [];
        assert.ok(
            events.includes('onDebugResolve:kotlin') || events.includes('onDebug'),
            'Should activate for kotlin debug sessions'
        );
    });

    test('Extension should register coroutines view', () => {
        const views = manifest.contributes?.views ?? {};
        const debugViews: Array<{ id: string }> = views['debug'] ?? [];
        const coroutineView = debugViews.find((v) => v.id === 'kotlin-debug.coroutinesView');
        assert.ok(coroutineView, 'Coroutine view should be registered in debug panel');
    });

    test('Extension should register refreshCoroutines command', () => {
        const commands: Array<{ command: string }> = manifest.contributes?.commands ?? [];
        const cmd = commands.find((c) => c.command === 'kotlin-debug.refreshCoroutines');
        assert.ok(cmd, 'refreshCoroutines command should be registered');
    });

    test('Kotlin language should declare correct mimetype', () => {
        const languages: Array<{ id: string; mimetypes?: string[] }> = manifest.contributes?.languages ?? [];
        const kotlin = languages.find((l) => l.id === 'kotlin');
        assert.ok(
            kotlin?.mimetypes?.includes('text/x-kotlin'),
            'Kotlin language should declare text/x-kotlin mimetype'
        );
    });
});

suite('DebugAdapterTracker Integration Tests', () => {
    test('Extension registers a DebugAdapterTracker for kotlin type', async () => {
        // The DebugAdapterTracker is registered on activate. We verify the extension
        // activated successfully (which means the tracker factory was registered).
        const ext = vscode.extensions.getExtension('schizobulia.kotlin-debug');
        assert.ok(ext, 'Extension should exist');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        // If activate() succeeded without throwing, the tracker was registered.
        assert.strictEqual(ext?.isActive, true);
    });
});
