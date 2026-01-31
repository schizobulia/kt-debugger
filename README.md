# Kotlin Debugger

<p align="center">
  <img src="vscode-kotlin-debug/images/kotlin-debug-icon.png" alt="Kotlin Debugger" width="128">
</p>

<p align="center">
  <strong>A standalone debugger for Kotlin/JVM programs</strong>
</p>

<p align="center">
  <a href="README_CN.md">中文文档</a> •
  <a href="#features">Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#vscode-extension">VSCode Extension</a>
</p>

---

## Features

- 🔍 **Standalone Debugger** - Works independently of IntelliJ IDEA
- 🎯 **Breakpoint Management** - Set, enable, disable, and conditional breakpoints
- 📚 **Stack Frame Navigation** - View and navigate call stacks
- 🔎 **Variable Inspection** - Inspect local variables and object properties
- 🧵 **Thread Management** - Switch between threads
- 💡 **Expression Evaluation** - Evaluate expressions at breakpoints
- 🔌 **DAP Protocol Support** - Integrates with VSCode and other DAP-compatible editors
- ⚡ **Kotlin-Specific Features** - Inline functions, lambdas, data classes support

## System Requirements

- JDK 17 or higher
- Gradle 8.x (for building from source)
- Node.js 18+ (for VSCode extension development)

## Installation

### Option 1: Download Pre-built Release

Download the latest release from [GitHub Releases](https://github.com/schizobulia/kt-debugger/releases):

```bash
# Download and extract
wget https://github.com/schizobulia/kt-debugger/releases/latest/download/kotlin-debugger-all.jar

# Run the debugger
java -jar kotlin-debugger-all.jar
```

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/schizobulia/kt-debugger.git
cd kt-debugger

# Build the debugger
bash scripts/build.sh

# The JAR file will be at: build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar
```

### Option 3: Install VSCode Extension

See [VSCode Extension](#vscode-extension) section below.

## Quick Start

### 1. Start Your Kotlin Program with Debug Options

```bash
# Basic debug mode (program waits for debugger)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar your-app.jar

# For Gradle projects
./gradlew run --debug-jvm
```

### 2. Connect the Debugger

**Using CLI:**
```bash
java -jar kotlin-debugger-1.0-SNAPSHOT-all.jar

# In the debugger console:
(kdb) attach localhost:5005
(kdb) break Main.kt:10
(kdb) continue
```

**Using VSCode:**
1. Install the Kotlin Debug extension
2. Create `.vscode/launch.json`:
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "kotlin",
      "request": "attach",
      "name": "Attach to Kotlin",
      "host": "localhost",
      "port": 5005,
      "sourcePaths": ["${workspaceFolder}/src/main/kotlin"]
    }
  ]
}
```
3. Press `F5` to start debugging

## Command Reference

### Session Management
| Command | Alias | Description |
|---------|-------|-------------|
| `attach <host>:<port>` | - | Connect to remote JVM |
| `run <class> [-cp path]` | `r` | Start program debugging |
| `quit` | `q` | Exit debugger |
| `help` | `h`, `?` | Show help |
| `status` | - | Show session status |

### Breakpoint Management
| Command | Alias | Description |
|---------|-------|-------------|
| `break <file>:<line>` | `b` | Set breakpoint |
| `break <file>:<line> if <cond>` | - | Set conditional breakpoint |
| `delete <id>` | `d` | Delete breakpoint |
| `list` | `l` | List all breakpoints |
| `enable <id>` | - | Enable breakpoint |
| `disable <id>` | - | Disable breakpoint |

### Execution Control
| Command | Alias | Description |
|---------|-------|-------------|
| `continue` | `c` | Resume execution |
| `step` | `s` | Step into |
| `next` | `n` | Step over |
| `finish` | `f` | Step out |

### Stack & Variables
| Command | Alias | Description |
|---------|-------|-------------|
| `backtrace` | `bt`, `where` | Show call stack |
| `frame <n>` | `fr` | Switch to frame n |
| `up` / `down` | - | Navigate frames |
| `locals` | - | Show local variables |
| `print <expr>` | `p` | Print expression value |

### Thread Management
| Command | Alias | Description |
|---------|-------|-------------|
| `threads` | - | List all threads |
| `thread <id>` | `t` | Switch to thread |

## VSCode Extension

The Kotlin Debug extension for VSCode provides a graphical debugging experience.

### Installation

**From VSCode Marketplace:**
1. Open VSCode
2. Press `Ctrl+Shift+X` (or `Cmd+Shift+X` on Mac)
3. Search for "Kotlin Debug"
4. Click Install

**From VSIX file:**
```bash
# Build the extension
bash scripts/vscode-ext.sh build

# Install to VSCode
bash scripts/vscode-ext.sh install
```

### Features

- Set breakpoints by clicking in the gutter
- View variables in the Variables panel
- Navigate call stacks
- Evaluate expressions in Debug Console
- Step through code with F10/F11

See [VSCode Extension README](vscode-kotlin-debug/README.md) for detailed usage.

## Project Structure

```
kt-debug/
├── src/main/kotlin/          # Debugger core source code
├── src/test/kotlin/          # Unit tests
├── vscode-kotlin-debug/      # VSCode extension
├── scripts/
│   ├── build.sh              # Main build script
│   └── vscode-ext.sh         # VSCode extension build script
├── docs/                     # Documentation
├── release/                  # Release artifacts
└── test-program/             # Test programs
```

## Development

### Building

```bash
# Full build with tests
bash scripts/build.sh

# Skip tests
bash scripts/build.sh -s

# Build VSCode extension
bash scripts/vscode-ext.sh build
```

### Running Tests

```bash
./gradlew test
```

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## Documentation

- [Design Document](docs/DESIGN.md) - Architecture and design decisions
- [Quick Reference](docs/QUICKREF.md) - Command quick reference
- [Tutorial](docs/TUTORIAL.md) - Step-by-step tutorial
- [DAP Integration](docs/DAP_INTEGRATION_PLAN.md) - DAP protocol implementation

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Acknowledgments

- [IntelliJ Community](https://github.com/JetBrains/intellij-community) - Reference implementation
- [java-debug](https://github.com/microsoft/java-debug) - DAP protocol reference
- [Kotlin](https://github.com/JetBrains/kotlin) - Kotlin compiler

---

<p align="center">
  Made with ❤️ for Kotlin developers
</p>
