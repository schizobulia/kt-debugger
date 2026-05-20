# Kotlin Debug — VS Code Extension User Guide

## Overview

**Kotlin Debug** is a VS Code debug extension based on the DAP (Debug Adapter Protocol), powered by the `kotlin-debugger` JAR. It lets you debug Kotlin/JVM programs directly inside VS Code without needing IntelliJ IDEA.

---

## Installation

### Option 1: Install from VSIX (Recommended)

1. Download the `.vsix` file from [GitHub Releases](https://github.com/schizobulia/kt-debugger/releases)
2. Open VS Code and press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
3. Search for and select **"Extensions: Install from VSIX..."**
4. Select the downloaded `.vsix` file

### Option 2: Build and install from source

```bash
git clone https://github.com/schizobulia/kt-debugger.git
cd kt-debugger

# Build the extension (also builds the debugger JAR)
bash scripts/vscode-ext.sh build

# Install into VS Code
bash scripts/vscode-ext.sh install
```

### Prerequisites

- VS Code >= 1.80.0
- Java 11+ (must be on `PATH` or configured via `kotlin-debug.javaHome`)
- A built `kotlin-debugger-1.0-SNAPSHOT-all.jar` (included automatically when packaged)

---

## Quick Start

### Step 1: Generate a debug configuration

Open the Command Palette (`Cmd+Shift+P`) and run:

```
Kotlin Debug: Generate Launch Configuration
```

Follow the wizard to pick a configuration type; a `.vscode/launch.json` is created automatically.

### Step 2: Set breakpoints

Click to the left of a line number in any `.kt` file to set a breakpoint (red dot).

### Step 3: Start debugging

Press `F5` or click the **"Run and Debug"** icon in the sidebar, then select the configuration you created.

---

## Debug Modes

### Launch Mode (Recommended)

The extension starts your application automatically and attaches the debugger. No manual program startup needed.

Add the following to `.vscode/launch.json`:

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

**Gradle project example:**

```json
{
  "type": "kotlin",
  "request": "launch",
  "name": "Kotlin: Launch via Gradle",
  "command": "./gradlew run -Dorg.gradle.jvmargs=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005\"",
  "port": 5005,
  "cwd": "${workspaceFolder}",
  "sourcePaths": [
    "${workspaceFolder}/src/main/kotlin"
  ]
}
```

Launch mode parameters:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `command` | ✅ | — | Command to start the app; **must include JDWP debug arguments** |
| `port` | ✅ | — | Debug port; must match the `address` in the command |
| `host` | | `"localhost"` | Debug host address |
| `cwd` | | `"${workspaceFolder}"` | Working directory for the command |
| `env` | | `{}` | Additional environment variables |
| `sourcePaths` | | `[]` | Kotlin source paths (used for breakpoint mapping) |
| `preLaunchWait` | | `kotlin-debug.defaultPreLaunchWait` | Timeout (ms) to wait for the app to start; the extension polls the port automatically |

> **How it works**: The extension runs `command` in a terminal named "Kotlin Debug Target", polls `host:port` until the port is open, then attaches via DAP.

> **Application takes a long time to start?** The default port polling timeout is controlled by the `kotlin-debug.defaultPreLaunchWait` global setting (default: 30 seconds). You can increase it in VS Code settings, or override it per-session by adding `"preLaunchWait": 60000` (e.g. 60 s) to your `launch.json` configuration.

### Attach Mode

Manually start the application, then attach the debugger. Useful when you need precise control over how the program starts.

**Step 1: Start the target program with JDWP arguments**

```bash
# suspend=y: program waits for the debugger before executing (good for debugging startup logic)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar your-app.jar

# suspend=n: program runs immediately; debugger can attach at any time
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar your-app.jar

# Gradle project
./gradlew run --debug-jvm
```

**Step 2: Configure `launch.json`**

```json
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
```

Attach mode parameters:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `host` | | `"localhost"` | Target JVM host address |
| `port` | ✅ | — | Debug port |
| `sourcePaths` | | `[]` | Kotlin source paths |

---

## Debug Features

### Breakpoints

#### Line breakpoints
Click to the left of a line number in a `.kt` file to set a breakpoint (red dot). Press `F9` to toggle the breakpoint on the current line.

#### Conditional breakpoints
Right-click a breakpoint → **"Edit Breakpoint..."** → enter a condition expression. The program only pauses here when the condition evaluates to `true`.

Supported condition syntax:

| Type | Examples |
|------|----------|
| Numeric comparison | `i == 5`, `count > 10` |
| String comparison | `name == "test"` |
| Boolean variable | `isEnabled`, `!isDone` |
| Member access | `person.age >= 30` |
| Compound condition | `x > 3 && y < 8` |
| Method call | `list.isEmpty()` |
| Null check | `obj != null` |

#### Logpoints
Right-click a breakpoint → **"Edit Breakpoint..."** → choose **Log Message**. Prints a log message when hit, **without pausing** the program.

#### Exception breakpoints
In the **"BREAKPOINTS"** section of the **"Run and Debug"** sidebar, configure automatic suspension on exceptions:
- **Caught Exceptions** — pause on all caught exceptions
- **Uncaught Exceptions** — pause on uncaught exceptions (recommended default)

When an exception breakpoint is hit, the **"Exception Info"** panel shows:
- Exception type and message
- Full Java stack trace
- `cause` chain (for nested exceptions)

### Variables

When the debugger is paused, the **"VARIABLES"** panel shows:
- Local variables of the current function
- Fields of the `this` object
- Method parameters

Variables support expanding to inspect object internals. Values are displayed in Kotlin format (`Int`, `String`, `List<T>`, etc.).

### Inline Values

When the debugger is paused, the editor automatically **inlines the current values** of variables at the end of each code line — no hover needed. Covers ±10 lines around the paused line. Works automatically, no configuration required.

### Hover Evaluation

During a debug session, hovering over a variable name shows its current value in a tooltip.

> Can be toggled with the `kotlin-debug.enableHoverEvaluation` setting.

### Expression Evaluation (Debug Console)

Type expressions in the **"DEBUG CONSOLE"** panel at the bottom:

| Type | Examples | Notes |
|------|----------|-------|
| Simple variable | `x`, `name`, `count` | Read local variables or fields |
| Member access | `person.name`, `obj.field` | Access object fields |
| Nested access | `a.b.c`, `person.address.city` | Multi-level field access |
| Array access | `arr[0]`, `matrix[1][2]` | Access array/list elements |
| Method call | `obj.toString()`, `list.size()` | Invoke methods |
| Literal | `42`, `"hello"`, `true` | Integer, string, boolean |

```
> person.name
"Alice"
> numbers[0] + numbers[1]
30
> calculator.add(10, 20)
30
```

### Watch

Add watch expressions in the **"WATCH"** panel; values are updated automatically each time the debugger pauses:

```
user.email
list.size()
array[index]
```

### Call Stack

The **"CALL STACK"** panel shows the full call chain for the current thread. Click any frame to jump to its source location and switch to that frame's local variables view.

Kotlin inline function frames are intelligently expanded — each inline call appears as a distinct frame.

### Stepping

| Shortcut | Action |
|----------|--------|
| `F5` | Continue |
| `F10` | Step Over |
| `F11` | Step Into |
| `Shift+F11` | Step Out |
| `Shift+F5` | Stop Debugging |
| `F9` | Toggle Breakpoint |

---

## Coroutine Debugging

When debugging a Kotlin program, a **"Kotlin Coroutines"** panel appears in the **"Debug"** sidebar (visible only during a Kotlin debug session).

The panel shows all coroutines with:
- Coroutine ID and name
- Current state (`RUNNING` / `SUSPENDED` / `CREATED`)
- Dispatcher information
- Call stack frames (click a frame to jump to the corresponding source)

Click the refresh icon in the panel header, or run **"Kotlin Debug: Refresh Coroutines"** from the Command Palette to manually refresh the list.

> Requires the target program to depend on the `kotlinx.coroutines` library.

---

## Hot Code Replace

Reload modified classes into the running JVM without restarting.

**How to use:**

1. Edit Kotlin source code and recompile (run Gradle's `classes` task)
2. While the debug session is active, trigger hot replace via any of:
   - Command Palette (`Cmd+Shift+P`) → **"Kotlin Debug: Hot Code Replace"**
   - Right-click inside a `.kt` editor → **"Kotlin Debug: Hot Code Replace"**
   - Click **"$(sync) Hot Code Replace"** in the status bar

> **Limitation**: JVM hot replace only supports changes inside method bodies. Adding/removing fields, changing method signatures, or modifying class hierarchy are not supported.

---

## CodeLens Actions

In `.kt` files, CodeLens buttons appear automatically above the following patterns:

| Code pattern | Buttons shown |
|--------------|---------------|
| `fun main(` | `▶ Run` `Debug` |
| `@JvmStatic fun main(` | `▶ Run` `Debug` |
| `@Test fun testXxx(` | `▶ Run Test` `Debug Test` |

- **Debug** — detects project type (Gradle / plain JAR) automatically and starts a debug session
- **Run Test** / **Debug Test** — runs the corresponding unit test via `./gradlew test --tests "ClassName.methodName"`

> Can be toggled with the `kotlin-debug.enableCodeLens` setting.

---

## Status Bar Indicator

The current debugger state is shown in the VS Code status bar (bottom-left):

| State | Display | Meaning |
|-------|---------|----------|
| Ready | `$(debug) Kotlin Debug` | Debugger ready, no active session |
| Debugging | `$(debug-alt) Debugging Kotlin` | Debug session active (orange highlight) |
| Error | `$(error) Kotlin Debug Error` | Debugger encountered an error (red highlight) |

Clicking the status bar item opens the **debug quick menu**:
- Generate Debug Configuration
- Start Debugging
- Hot Code Replace
- Show Debug Logs
- View Documentation

---

## Configuration Generation Wizard

Run **"Kotlin Debug: Generate Launch Configuration"** from the Command Palette (`Cmd+Shift+P`) to interactively create a debug configuration:

1. Select configuration type:
   - **Launch** — start via a JAR file directly
   - **Attach** — attach to an already-running JVM
   - **Gradle Launch** — start via Gradle

2. Enter the debug port (default `5005`)

3. Enter additional parameters depending on type (JAR path / host address, etc.)

4. The configuration is written to `.vscode/launch.json`. If a configuration with the same name already exists, you can choose to replace it or add it as a new entry.

---

## Global Extension Settings

Search `kotlin-debug` in VS Code settings (`Cmd+,`) to configure:

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `kotlin-debug.javaHome` | string | `""` | Custom Java installation path; takes priority over `JAVA_HOME` and system `java` |
| `kotlin-debug.buildBeforeDebug` | boolean | `false` | Automatically build before each debug session (Gradle: `./gradlew classes`; Maven: `mvn compile -q`) |
| `kotlin-debug.debuggerJarPath` | string | `""` | Custom path to the `kotlin-debugger` JAR; auto-detected if not set |
| `kotlin-debug.enableCodeLens` | boolean | `true` | Show CodeLens buttons above `main` functions and `@Test` methods |
| `kotlin-debug.enableHoverEvaluation` | boolean | `true` | Show variable values on hover during debugging |
| `kotlin-debug.defaultSourcePaths` | string[] | `["${workspaceFolder}/src/main/kotlin"]` | Default source paths used when not specified in `launch.json` |
| `kotlin-debug.enableDapLogging` | boolean | `false` | Enable detailed DAP protocol message logging in the output panel. Disabled by default. Enable only when troubleshooting debugger connection issues. |
| `kotlin-debug.defaultPreLaunchWait` | number | `30000` | Default time (ms) to poll the debug port in launch mode before attaching. Increase if your application takes a long time to start. Per-session override: `preLaunchWait` in `launch.json`. |

**Example (`settings.json`):**

```json
{
  "kotlin-debug.javaHome": "/usr/lib/jvm/java-17-openjdk",
  "kotlin-debug.buildBeforeDebug": true,
  "kotlin-debug.enableCodeLens": true,
  "kotlin-debug.enableHoverEvaluation": true,
  "kotlin-debug.defaultSourcePaths": [
    "${workspaceFolder}/src/main/kotlin",
    "${workspaceFolder}/src/test/kotlin"
  ],
  "kotlin-debug.enableDapLogging": false,
  "kotlin-debug.defaultPreLaunchWait": 60000
}
```

Java lookup order: `kotlin-debug.javaHome` → `JAVA_HOME` environment variable → `java` on system `PATH`.

---

## Logs & Diagnostics

After a debug session starts, a **"Kotlin Debugger Logs"** channel appears in the VS Code Output panel (`Cmd+Shift+U`), showing:
- DAP communication logs
- Debugger process stderr output
- Extension internal state (source path detection, JAR search, etc.)

If the debugger crashes, an error notification is shown; details are available in the log channel.

---

## Command Palette Commands

Press `Cmd+Shift+P` to run any of the following extension commands:

| Command | Description |
|---------|-------------|
| `Kotlin Debug: Generate Launch Configuration` | Interactively generate a `launch.json` configuration |
| `Kotlin Debug: Debug Main Function` | Debug the `main` function in the current file |
| `Kotlin Debug: Show Debug Menu` | Open the debug quick menu |
| `Kotlin Debug: Hot Code Replace` | Trigger hot code replace (available during debugging) |
| `Kotlin Debug: Refresh Coroutines` | Refresh the coroutines view |
| `Kotlin Debug: Run Test` | Run the test method at the current cursor position |
| `Kotlin Debug: Debug Test` | Debug the test method at the current cursor position |

---

## Typical Workflow Examples

### Gradle project debugging

**1. Configure `launch.json`:**

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "kotlin",
      "request": "launch",
      "name": "Debug Gradle App",
      "command": "./gradlew run -Dorg.gradle.jvmargs=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005\"",
      "port": 5005,
      "cwd": "${workspaceFolder}",
      "sourcePaths": ["${workspaceFolder}/src/main/kotlin"]
    }
  ]
}
```

**2. Enable automatic pre-debug build (optional):**

```json
{
  "kotlin-debug.buildBeforeDebug": true
}
```

**3. Press `F5` to start debugging.**

### Debugging a remote service

```json
{
  "type": "kotlin",
  "request": "attach",
  "name": "Attach to Remote Service",
  "host": "staging.example.com",
  "port": 5005,
  "sourcePaths": ["${workspaceFolder}/src/main/kotlin"]
}
```

> Ensure the firewall allows port 5005 and the remote program was started with `address=*:5005` (not `address=127.0.0.1:5005`).

### Debugging a Kotlin test

1. Open a test file and find a method annotated with `@Test`
2. `▶ Run Test` and `Debug Test` CodeLens buttons appear above the method
3. Click **Debug Test** to debug that single test case

---

## Frequently Asked Questions

**Q: "Cannot find kotlin-debugger JAR file"**

A: The JAR was not found. Check these locations:
- `release/kotlin-debugger-1.0-SNAPSHOT-all.jar`
- `build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar`
- Or set `kotlin-debug.debuggerJarPath` to a custom path.

Run `bash scripts/build.sh` to rebuild.

**Q: Port connection fails ("Failed to attach")**

A:
1. Confirm the target program was started with JDWP arguments
2. Confirm the port in `launch.json` matches the `address` in the JDWP arguments
3. Check that the firewall/network allows the port
4. If started with `suspend=n`, make sure the program has not exited already

**Q: Breakpoints are never hit**

A:
1. Verify `sourcePaths` points to the actual `.kt` source directory
2. Confirm the program was compiled with debug info (Gradle includes it by default)
3. If `buildBeforeDebug` is enabled, verify the build succeeded

**Q: Hot code replace has no effect**

A:
1. Make sure you recompiled the modified files first
2. JVM hot replace only supports changes inside method bodies; adding/removing fields, changing method signatures, or modifying class hierarchy are not supported

**Q: Coroutines view is empty**

A: Confirm the target program depends on `kotlinx.coroutines` and the debugger is successfully attached. Click the refresh button in the view header to force an update.
