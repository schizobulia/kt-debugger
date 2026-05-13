# Kotlin Debugger CLI User Guide

## Overview

`kotlin-debugger` is a standalone Kotlin/JVM command-line debugger that runs without IntelliJ IDEA. It communicates with the target JVM via the JDI (Java Debug Interface) protocol and supports breakpoints, stepping, variable inspection, coroutine debugging, hot code replace, and more.

---

## Installation & Launch

### Running from Build Artifacts

```bash
# Build the project
bash scripts/build.sh

# Option 1: Use the launch script (recommended)
./build/scripts/kotlin-debugger

# Option 2: Run the fat JAR directly
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar
```

### Command-Line Options

```
Usage: kotlin-debugger [options] [command]

Options:
  -h, --help         Show help message
  -v, --version      Show version
  --dap              Start in DAP server mode (used by the VS Code extension)
  --debug, --log     Enable debug logging (for DAP mode)
  --log-file <path>  Write logs to the specified file

Commands (executed immediately at startup):
  run <class> [-cp path]    Launch and debug a program
  attach <host>:<port>      Attach to a remote JVM
```

Examples:

```bash
# Enter interactive mode
kotlin-debugger

# Attach immediately at startup
kotlin-debugger attach localhost:5005

# Launch a program at startup
kotlin-debugger run MainKt -cp ./build/classes

# DAP mode (invoked by the VS Code extension; rarely run manually)
kotlin-debugger --dap --debug
```

---

## Interactive REPL

After starting, the debugger enters an interactive REPL with the `(kdb)` prompt. Press **Tab** to auto-complete commands.

```
╔═══════════════════════════════════════════╗
║         Kotlin Debugger v1.0.0            ║
║     Type 'help' for available commands    ║
╚═══════════════════════════════════════════╝

(kdb) _
```

---

## Commands & Features

### 1. Session Management

#### `run` — Launch a debug target

```
(kdb) run <mainClass> [-cp <classpath>] [args...]
Alias: r
```

Launches a new JVM process locally and immediately attaches the debugger.

```bash
(kdb) run MainKt -cp ./build/classes/kotlin/main
(kdb) run com.example.App -cp app.jar:lib.jar arg1 arg2
```

#### `attach` — Attach to a remote JVM

```
(kdb) attach <host>:<port> [--no-suspend]
```

Attaches to a JVM that has JDWP debugging enabled. **The target VM is suspended by default**, giving you time to set breakpoints before execution begins.

```bash
# Attach and suspend VM (default; recommended for debugging startup logic)
(kdb) attach localhost:5005

# Attach without suspending (target continues running)
(kdb) attach localhost:5005 --no-suspend

# Attach to a remote machine
(kdb) attach 192.168.1.100:5005
```

The target program must be started with JDWP arguments:

```bash
# suspend=y: program waits for debugger before executing
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -jar app.jar

# suspend=n: program runs immediately; debugger can attach at any time
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar app.jar
```

#### `status` — Show session status

```
(kdb) status
```

Displays current debug session info: connection state, suspended/running, active thread, etc.

#### `quit` — Exit the debugger

```
(kdb) quit
Alias: q, exit
```

---

### 2. Breakpoints

#### `break` — Set a breakpoint

```
(kdb) break <file>:<line>
Alias: b
```

Three breakpoint types are supported:

**Line breakpoint** — suspends the program when the specified line is reached.

```bash
(kdb) b Main.kt:42
(kdb) break com/example/Foo.kt:100
```

**Conditional breakpoint** — suspends only when the condition expression evaluates to `true`.

```bash
(kdb) b Main.kt:42 --cond "i == 5"
(kdb) b Main.kt:42 --cond "name == \"Alice\""
(kdb) b Main.kt:42 --cond "person.age >= 30"
(kdb) b Main.kt:42 --cond "x > 3 && y < 8"
(kdb) b Main.kt:42 --cond "list.isEmpty()"
(kdb) b Main.kt:42 --cond "obj != null"
```

**Logpoint** — prints a log message when hit, **does not pause** the program.

```bash
(kdb) b Main.kt:42 --log "value of i = {i}"
(kdb) b Main.kt:42 --log "entering loop iteration"
```

#### `delete` — Delete a breakpoint

```
(kdb) delete <id>
Alias: d
```

```bash
(kdb) d 1
(kdb) delete 3
```

#### `list` — List all breakpoints

```
(kdb) list
Alias: l
```

Displays all breakpoints in a table showing ID, location, status, and condition/log message.

#### `enable` / `disable` — Enable or disable a breakpoint

```
(kdb) enable <id>
(kdb) disable <id>
```

Temporarily disables a breakpoint without deleting it.

```bash
(kdb) disable 2
(kdb) enable 2
```

---

### 3. Execution Control

#### `continue` — Continue execution

```
(kdb) continue
Alias: c
```

Resumes execution from the current breakpoint until the next breakpoint or program exit.

#### `step` — Step into

```
(kdb) step
Alias: s
```

Executes the current line; if a function call is on this line, **steps into** the function.

#### `next` — Step over

```
(kdb) next
Alias: n
```

Executes the current line; if a function call is on this line, **steps over** it and moves to the next line.

#### `finish` — Step out

```
(kdb) finish
Alias: f
```

Runs the remaining code in the current function and pauses when it returns to the caller.

#### `interrupt` — Interrupt execution

```
(kdb) interrupt
```

Force-suspends the running VM (equivalent to pausing at an arbitrary point).

---

### 4. Call Stack

#### `backtrace` — Show the call stack

```
(kdb) backtrace
Alias: bt, where
```

Displays the full call stack of the current thread, including method name, file, and line number for each frame. Kotlin inline function frames are intelligently expanded.

#### `frame` — Switch to a frame

```
(kdb) frame <n>
Alias: fr
```

Switches to the frame at the given index (0 is the topmost frame).

```bash
(kdb) fr 0    # Top frame (current execution point)
(kdb) fr 2    # The second caller up the stack
```

#### `up` / `down` — Move between frames

```
(kdb) up      # Move up one frame (toward the caller)
(kdb) down    # Move down one frame (toward the callee)
```

---

### 5. Variables

#### `locals` — Show local variables

```
(kdb) locals
Alias: info locals
```

Displays all local variables in the current frame: name, type, and value. Values are automatically formatted in Kotlin style (`Int`, `String`, `List<T>`, etc.).

#### `print` — Print a variable

```
(kdb) print <name>
Alias: p
```

Prints the detailed value of the specified variable, with deep object expansion.

```bash
(kdb) p count
(kdb) p myObject
(kdb) print person
```

---

### 6. Threads

#### `threads` — List all threads

```
(kdb) threads
```

Displays all threads in the target JVM: ID, name, and current status (running / suspended / waiting, etc.).

#### `thread` — Switch thread

```
(kdb) thread <id>
Alias: t
```

Switches to the context of the specified thread. Subsequent commands (`backtrace`, `locals`, etc.) operate on this thread.

```bash
(kdb) t 1
(kdb) thread 5
```

---

### 7. Coroutine Debugging

#### `coroutines` — List all coroutines

```
(kdb) coroutines
```

Lists all Kotlin coroutines with their state (`RUNNING` / `SUSPENDED` / `CREATED`), dispatcher, and call stack.

> Requires the target program to use the `kotlinx.coroutines` library.

---

### 8. Hot Code Replace

Reloads a modified `.class` file into the running JVM without restarting.

```
(kdb) hotswap <file.class>
Alias: hcr

(kdb) hotswap --class <ClassName> <file.class>
```

```bash
# Auto-infer class name from the .class file path
(kdb) hcr build/classes/kotlin/main/com/example/MainKt.class

# Explicitly specify the class name (when auto-inference is incorrect)
(kdb) hotswap --class com.example.MainKt build/classes/kotlin/main/com/example/MainKt.class
```

> **Note**: JVM hot code replace only supports changes inside method bodies. Adding/removing fields, changing method signatures, or modifying class hierarchy are not supported.

---

### 9. Help

```
(kdb) help
Alias: h, ?
```

Displays all available commands with usage descriptions.

---

## Typical Debug Workflows

### Scenario 1: Debug program startup logic

Useful for setting breakpoints at `main()`, static initializers, framework bootstrap code, etc.

```bash
# Step 1: Start the target program with suspend=y (JVM waits for debugger)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -cp build/libs/app.jar MainKt

# Step 2: Connect the debugger (VM stays suspended automatically)
kotlin-debugger
(kdb) attach localhost:5005
# Output: Attached to localhost:5005 (suspended)
# Output: VM is suspended. Set breakpoints with 'break <file>:<line>', then use 'continue' to start execution.

# Step 3: Set breakpoints before any code runs
(kdb) b Main.kt:5
(kdb) b Main.kt:20

# Step 4: Start execution
(kdb) c
# Breakpoint hit, program pauses...

# Step 5: Inspect state
(kdb) bt          # View call stack
(kdb) locals      # View local variables
(kdb) p myVar     # Print a specific variable
(kdb) n           # Step to next line
```

### Scenario 2: Debug an already-running program

```bash
# Step 1: Start the target program with suspend=n (program is already running)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar app.jar

# Step 2: Attach without suspending
(kdb) attach localhost:5005 --no-suspend

# Step 3: Set a conditional breakpoint (program keeps running; pauses on hit)
(kdb) b Service.kt:87 --cond "userId == 123"

# Step 4: Wait for the breakpoint to be hit...
```

### Scenario 3: Trace execution flow with Logpoints

```bash
(kdb) attach localhost:5005 --no-suspend
(kdb) b DataProcessor.kt:45 --log "Processing item: {item}"
(kdb) b DataProcessor.kt:67 --log "Result: {result}"
(kdb) c
# Program continues running; logs are printed each time these lines are reached without pausing
```

### Scenario 4: Coroutine debugging

```bash
(kdb) attach localhost:5005
(kdb) b CoroutineHandler.kt:30
(kdb) c
# After the breakpoint hits:
(kdb) coroutines        # View all coroutine states
(kdb) threads           # View thread list
(kdb) t 12              # Switch to the coroutine's thread
(kdb) bt                # View coroutine call stack
```

---

## Command Quick Reference

| Command | Alias | Description |
|---------|-------|-------------|
| `run <class> [-cp path]` | `r` | Launch and debug a program |
| `attach <host>:<port>` | — | Attach to a remote JVM (suspends by default) |
| `attach <host>:<port> --no-suspend` | — | Attach without suspending |
| `status` | — | Show session status |
| `quit` | `q`, `exit` | Exit the debugger |
| `break <file>:<line>` | `b` | Set a breakpoint |
| `break <file>:<line> --cond "expr"` | `b` | Set a conditional breakpoint |
| `break <file>:<line> --log "msg"` | `b` | Set a logpoint |
| `delete <id>` | `d` | Delete a breakpoint |
| `list` | `l` | List all breakpoints |
| `enable <id>` | — | Enable a breakpoint |
| `disable <id>` | — | Disable a breakpoint |
| `continue` | `c` | Continue execution |
| `interrupt` | — | Interrupt a running program |
| `step` | `s` | Step into |
| `next` | `n` | Step over |
| `finish` | `f` | Step out |
| `backtrace` | `bt`, `where` | Show call stack |
| `frame <n>` | `fr` | Switch to frame n |
| `up` | — | Move up one frame |
| `down` | — | Move down one frame |
| `locals` | `info locals` | Show local variables |
| `print <name>` | `p` | Print a variable's value |
| `threads` | — | List all threads |
| `thread <id>` | `t` | Switch to a thread |
| `coroutines` | — | List all coroutines |
| `hotswap <file.class>` | `hcr` | Hot code replace |
| `help` | `h`, `?` | Show help |

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Tab` | Command auto-complete |
| `↑` / `↓` | Command history |
| `Ctrl+C` | Exit the debugger |
| `Ctrl+D` | Exit the debugger |

---

## Condition Expression Syntax

Conditional breakpoints (`--cond`) support the following expression syntax:

| Type | Examples |
|------|----------|
| Numeric comparison | `i == 5`, `count > 10`, `x <= 3` |
| String comparison | `name == "Alice"` |
| Boolean variable | `isEnabled`, `!isDone` |
| Member access | `person.age >= 30` |
| Logical combination | `x > 3 && y < 8`, `a == 1 \|\| b == 2` |
| Method call | `list.isEmpty()`, `str.startsWith("prefix")` |
| Null check | `obj != null`, `value == null` |
