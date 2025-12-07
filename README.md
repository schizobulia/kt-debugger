# Kotlin Debugger User Guide
> The code and documentation for this project are AI generated.

## Table of Contents
1. [Introduction](#introduction)
2. [Installation & Building](#installation--building)
3. [Quick Start](#quick-start)
4. [Command Reference](#command-reference)
5. [Debugging Examples](#debugging-examples)
6. [Advanced Features](#advanced-features)
7. [Frequently Asked Questions](#frequently-asked-questions)

---

## Introduction
The Kotlin Debugger is a standalone command-line debugger specifically designed for debugging Kotlin/JVM programs. It can run independently of IntelliJ IDEA and provides:

- Breakpoint setting and management
- Stack frame inspection and navigation
- Variable viewing
- Thread management
- Kotlin feature support (inline functions, Lambdas, etc.)

### System Requirements
- JDK 11 or higher
- Gradle 8.x (for building)

---

## Installation & Building

### 1. Build the Debugger
```bash
cd kt-debugger

# Create Gradle Wrapper (if not exists)
gradle wrapper --gradle-version 8.10

# Build fat jar
./gradlew fatJar

# Check build output
ls -la build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar
```

### 2. Create Launch Script (Optional)
```bash
# Create a convenient launch script
cat > kdb << 'EOF'
#!/bin/bash
java -jar kt-debugger/build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar "$@"
EOF
chmod +x kdb
sudo mv kdb /usr/local/bin/
```

Now you can start the debugger directly with the `kdb` command.

---

## Quick Start

### Method 1: Attach Mode (Recommended)
Start the target program first, then connect with the debugger.

#### Step 1: Launch Target Program with Debug Port
```bash
# Start Java program with JDWP agent
java '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005' test-program/InteractiveTest.jar
```

Parameter Explanation:
- `transport=dt_socket`: Use socket communication
- `server=y`: Act as a debug server
- `suspend=y`: Pause after startup, wait for debugger connection
- `address=*:5005`: Listen on port 5005 (allow all interfaces)

#### Step 2: Start Debugger and Connect
```bash
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar

# Connect in debugger
(kdb) attach localhost:5005
```

### Method 2: Launch Mode
Let the debugger start the target program directly.
```bash
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar

# Launch program in debugger
(kdb) run MainKt -cp /path/to/classes
```

---

## Command Reference

### Session Management
| Command | Alias | Description | Example |
|---------|-------|-------------|---------|
| `run <class> [-cp path]` | `r` | Start program debugging | `run MainKt -cp app.jar` |
| `attach <host>:<port>` | - | Connect to remote JVM | `attach localhost:5005` |
| `quit` | `q` | Exit debugger | `quit` |
| `help` | `h`, `?` | Show help information | `help` |
| `status` | - | Show session status | `status` |

### Breakpoint Management
| Command | Alias | Description | Example |
|---------|-------|-------------|---------|
| `break <file>:<line>` | `b` | Set breakpoint | `b Main.kt:10` |
| `delete <id>` | `d` | Delete breakpoint | `d 1` |
| `list` | `l` | List all breakpoints | `list` |
| `enable <id>` | - | Enable breakpoint | `enable 1` |
| `disable <id>` | - | Disable breakpoint | `disable 1` |

### Execution Control
| Command | Alias | Description | Example |
|---------|-------|-------------|---------|
| `continue` | `c` | Resume execution | `c` |
| `step` | `s` | Step into (TODO) | `s` |
| `next` | `n` | Step over (TODO) | `n` |
| `finish` | `f` | Execute until return (TODO) | `f` |

### Stack Frame Navigation
| Command | Alias | Description | Example |
|---------|-------|-------------|---------|
| `backtrace` | `bt`, `where` | Show call stack | `bt` |
| `frame <n>` | `fr` | Switch to frame n | `fr 2` |
| `up` | - | Move up one frame | `up` |
| `down` | - | Move down one frame | `down` |

### Variable Inspection
| Command | Alias | Description | Example |
|---------|-------|-------------|---------|
| `locals` | - | Show local variables | `locals` |
| `print <var>` | `p` | Print variable value | `p myVar` |

### Thread Management
| Command | Alias | Description | Example |
|---------|-------|-------------|---------|
| `threads` | - | List all threads | `threads` |
| `thread <id>` | `t` | Switch to specified thread | `t 1` |

---

## Debugging Examples

### Example 1: Basic Debugging Workflow
We'll use the built-in test program for demonstration.

#### 1. Start Test Program (Debug Mode)
Open Terminal 1:
```bash
cd kt-debugger/test-program
./run-debug.sh
```

Output:
```
Starting InteractiveTest with debug enabled on port 5005
Use: attach localhost:5005

Listening for transport dt_socket at address: 5005
```

#### 2. Start Debugger and Connect
Open Terminal 2:
```bash
cd kt-debugger
java -jar build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar
```

```
╔═══════════════════════════════════════════╗
║         Kotlin Debugger v1.0.0            ║
║     Type 'help' for available commands    ║
╚═══════════════════════════════════════════╝

(kdb) attach localhost:5005
Attached to localhost:5005
```

#### 3. Set Breakpoints
```
(kdb) b InteractiveTest.kt:65
Breakpoint 1 set at InteractiveTest.kt:65

(kdb) b InteractiveTest.kt:67
Breakpoint 2 set at InteractiveTest.kt:67

(kdb) list
ID  Location                   Status   Condition
--  ------------------------   -------  ---------
1   InteractiveTest.kt:65      enabled
2   InteractiveTest.kt:67      enabled
```

#### 4. Resume Execution
```
(kdb) c
Continuing...
```

The test program in Terminal 1 will start running and display a menu:
```
=== Kotlin Debug Test Program ===
Commands: calc, list, random, inline, lambda, loop, quit

>
```

#### 5. Trigger Breakpoint
Enter `calc` in Terminal 1 to trigger the breakpoint:

Terminal 2 will show:
```
Hit breakpoint 1 at InteractiveTest.kt:65
```

#### 6. Inspect Stack Frames
```
(kdb) bt
→ #0  InteractiveTestKt.testCalculation(InteractiveTest.kt:65)
  #1  InteractiveTestKt.main(InteractiveTest.kt:22)
  #2  InteractiveTestKt.main(InteractiveTest.kt)
```

#### 7. Inspect Variables
```
(kdb) locals
Local Variables:
  x: int = 42
  y: int = 10

(kdb) p x
x: int = 42

(kdb) p y
y: int = 10
```

#### 8. Continue to Next Breakpoint
```
(kdb) c
Continuing...
Hit breakpoint 2 at InteractiveTest.kt:67

(kdb) locals
Local Variables:
  x: int = 42
  y: int = 10
  sum: int = 52
  product: int = 420
```

#### 9. Exit Debugging
```
(kdb) c
Continuing...

(kdb) quit
Goodbye!
```

---

### Example 2: Debugging Loops
```
(kdb) attach localhost:5005
Attached to localhost:5005

(kdb) b InteractiveTest.kt:140
Breakpoint 1 set at InteractiveTest.kt:140

(kdb) c
```

Enter `loop` in the test program:
```
(kdb)
Hit breakpoint 1 at InteractiveTest.kt:140

(kdb) locals
Local Variables:
  sum: int = 0
  i: int = 1

(kdb) c
Hit breakpoint 1 at InteractiveTest.kt:140

(kdb) locals
Local Variables:
  sum: int = 1
  i: int = 2

(kdb) c
Hit breakpoint 1 at InteractiveTest.kt:140

(kdb) p sum
sum: int = 3
```

---

### Example 3: Inspecting Threads
```
(kdb) threads
ID   Name                  Status    State
--   ----                  ------    -----
*1   main                  running   suspended
2    Reference Handler     waiting   suspended
3    Finalizer             waiting   suspended
4    Signal Dispatcher     running   suspended
```

Switch threads:
```
(kdb) thread 1
Switched to thread 1
```

---

## Advanced Features

### Kotlin Feature Support

#### Inline Function Debugging
The debugger supports correct source code location display for inline functions via SMAP (Source Map):
```kotlin
inline fun inlineCalculate(a: Int, b: Int, operation: (Int, Int) -> Int): Int {
    val result = operation(a, b)  // Breakpoint can be set here
    return result
}
```

When setting a breakpoint, use the source file and line number where the inline function is defined:
```
(kdb) b InteractiveTest.kt:98
```

#### Lambda Debugging
Breakpoints can also be set inside Lambda expressions:
```kotlin
items.forEach { item ->
    val upper = item.uppercase()  // Breakpoint can be set here
    println("  $item -> $upper")
}
```

### Breakpoint Location Formats
| Location Type | Format | Example |
|---------------|--------|---------|
| File:Line | `file.kt:line` | `Main.kt:10` |
| File Name Only | Auto-matching | `Main.kt:10` |
| Full Path | Supported | `/path/to/Main.kt:10` |

---

## Frequently Asked Questions

### Q: Connection Failed "Connection refused"
**Cause**: Target program not running or incorrect port

**Solutions**:
1. Verify the target program is running and listening on the specified port
2. Check if the port number is correct
3. Verify firewall settings are not blocking the connection

### Q: Breakpoint Not Triggering
**Cause**: Incorrect breakpoint location

**Solutions**:
1. Ensure the source file name is correct (case-sensitive)
2. Confirm the line number contains executable code
3. Use the `list` command to check breakpoint status

### Q: Cannot See Variable Values
**Cause**: Possible in optimized code

**Solutions**:
1. Ensure debug information is included during compilation (`-g` option)
2. Verify the variable is in the current scope

### Q: "No active debug session" Error
**Cause**: Not connected to a target program

**Solution**:
```
(kdb) attach localhost:5005
# Or
(kdb) run MainKt -cp your-app.jar
```

---

## Project Structure
```
kt-debugger/
├── build.gradle.kts              # Gradle build configuration
├── src/
│   ├── main/kotlin/
│   │   └── com/example/kotlindebugger/
│   │       ├── Main.kt           # Entry point
│   │       ├── cli/              # CLI interaction
│   │       ├── core/             # Debugger core logic
│   │       ├── common/           # Common utilities
│   │       └── kotlin/           # Kotlin language support
│   └── test/kotlin/              # Test code
├── test-program/                 # Test application
│   ├── InteractiveTest.kt        # Interactive test program
│   ├── InteractiveTest.jar
│   ├── run.sh                    # Run normally
│   └── run-debug.sh              # Run in debug mode
└── build/libs/
    └── kotlin-debugger-1.0-SNAPSHOT-all.jar
```

---

## Planned Features
The following features are under development:
- [ ] Step execution (step/next/finish)
- [ ] Expression evaluation (eval)
- [ ] Conditional breakpoints
- [ ] Source code display
- [ ] Coroutine debugging support
- [ ] Inline stack frame display

---

## Feedback & Contributions
For issues or suggestions, please submit an Issue or Pull Request.
