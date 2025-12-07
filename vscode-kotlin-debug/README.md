# Kotlin Debugger for VSCode

Kotlin debugging support for Visual Studio Code.

## Features

- Set breakpoints in Kotlin files
- Step through code (Step Over, Step Into, Step Out)
- View variables and call stack
- Support for inline functions

## Requirements

- Java 17 or higher
- Kotlin debugger JAR file

## Installation

1. Build the Kotlin debugger:
   ```bash
   cd ..
   bash scripts/build.sh
   ```

2. Install VSCode extension dependencies:
   ```bash
   cd vscode-kotlin-debug
   npm install
   ```

3. Compile the extension:
   ```bash
   npm run compile
   ```

4. Install the extension:
   - Press F5 in VSCode to open Extension Development Host
   - Or package and install: `vsce package && code --install-extension kotlin-debug-1.0.0.vsix`

## Usage

1. Open a Kotlin project in VSCode

2. Create a `.vscode/launch.json` file:
   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "kotlin",
         "request": "launch",
         "name": "Debug Kotlin",
         "mainClass": "MainKt",
         "classpath": ["${workspaceFolder}/build/classes/kotlin/main"]
       }
     ]
   }
   ```

3. Set breakpoints by clicking in the gutter

4. Press F5 to start debugging

## Configuration

### Launch Configuration

- `mainClass`: Main class to debug (required)
- `classpath`: Array of classpath entries
- `jvmArgs`: JVM arguments
- `args`: Program arguments
- `debuggerPath`: Path to kotlin-debugger JAR

### Attach Configuration

- `host`: Host to attach to (default: localhost)
- `port`: Port to attach to (required)
- `debuggerPath`: Path to kotlin-debugger JAR

## License

MIT
