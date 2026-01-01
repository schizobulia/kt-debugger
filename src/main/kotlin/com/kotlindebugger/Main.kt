package com.kotlindebugger

import com.kotlindebugger.cli.command.CommandProcessor
import com.kotlindebugger.cli.command.CommandResult
import com.kotlindebugger.cli.output.OutputFormatter
import com.kotlindebugger.cli.DebugCompleter
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.EndOfFileException
import org.jline.terminal.TerminalBuilder

/**
 * Kotlin Debugger 主入口
 */
fun main(args: Array<String>) {
    // 解析命令行参数
    var dapMode = false
    var debugMode = false
    var logFilePath: String? = null
    var argIndex = 0

    while (argIndex < args.size) {
        when (args[argIndex]) {
            "--dap" -> dapMode = true
            "--debug", "--log" -> debugMode = true
            "--log-file" -> {
                if (argIndex + 1 < args.size) {
                    logFilePath = args[++argIndex]
                }
            }
            else -> break
        }
        argIndex++
    }

    // 如果启用调试模式，设置Logger
    if (debugMode) {
        Logger.enableDebugMode()

        // 如果没有指定日志文件，使用临时文件
        if (logFilePath == null) {
            val tempDir = System.getProperty("java.io.tmpdir")
            val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            logFilePath = "$tempDir/kotlin-debugger-$timestamp.log"
        }

        Logger.setLogFile(logFilePath!!)
        System.err.println("Debug log file: $logFilePath")
    }

    // 检查是否是 DAP 模式
    if (dapMode) {
        if (debugMode) {
            Logger.info("Starting DAP server in DEBUG mode")
        }
        val dapServer = DAPServer()
        dapServer.start()
        return
    }

    val debugger = KotlinDebugger()
    debugger.run(args)
}

class KotlinDebugger {
    private val formatter = OutputFormatter()
    private val processor = CommandProcessor(formatter)

    fun run(args: Array<String>) {
        printBanner()

        // 处理命令行参数
        if (args.isNotEmpty()) {
            handleCommandLineArgs(args)
        }

        // 进入交互模式
        runInteractiveMode()
    }

    private fun printBanner() {
        println(formatter.header("""
            ╔═══════════════════════════════════════════╗
            ║         Kotlin Debugger v1.0.0            ║
            ║     Type 'help' for available commands    ║
            ╚═══════════════════════════════════════════╝
        """.trimIndent()))
        println()
    }

    private fun handleCommandLineArgs(args: Array<String>) {
        when (args[0]) {
            "--help", "-h" -> {
                printUsage()
                return
            }
            "--version", "-v" -> {
                println("Kotlin Debugger v1.0.0")
                return
            }
            "run" -> {
                if (args.size > 1) {
                    val runArgs = args.drop(1).joinToString(" ")
                    val result = processor.process("run $runArgs")
                    handleResult(result)
                }
            }
            "attach" -> {
                if (args.size > 1) {
                    val result = processor.process("attach ${args[1]}")
                    handleResult(result)
                }
            }
        }
    }

    private fun printUsage() {
        println("""
            Usage: kotlin-debugger [options] [command]

            Options:
              -h, --help      Show this help message
              -v, --version   Show version
              --dap           Start in DAP (Debug Adapter Protocol) mode
              --debug, --log  Enable debug logging (for DAP mode)

            Commands:
              run <class> [-cp path]    Launch and debug a program
              attach <host>:<port>      Attach to a remote JVM

            Examples:
              kotlin-debugger run MainKt -cp ./build/classes
              kotlin-debugger attach localhost:5005
              kotlin-debugger --dap --debug  (starts DAP server with debug logging)
              kotlin-debugger  (starts interactive mode)
        """.trimIndent())
    }

    private fun runInteractiveMode() {
        val terminal = TerminalBuilder.builder()
            .system(true)
            .jansi(true)  // 启用 Jansi 获得更好的终端支持
            .build()

        val lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(DebugCompleter())  // 添加自动补全
            .build()

        while (!processor.shouldExit()) {
            try {
                // 在等待用户输入之前，先处理事件队列
                processor.processEventQueue()

                val line = lineReader.readLine(formatter.highlightPrompt())
                val result = processor.process(line)
                handleResult(result)

            } catch (e: UserInterruptException) {
                // Ctrl+C - 立即退出
                println(formatter.info("\nGoodbye!"))
                return
            } catch (e: EndOfFileException) {
                // Ctrl+D
                processor.process("quit")
                break
            } catch (e: Exception) {
                println(formatter.error("Error: ${e.message}"))
            }
        }

        println(formatter.info("Goodbye!"))
    }

    private fun handleResult(result: CommandResult) {
        when (result) {
            is CommandResult.Success -> { /* 无输出 */ }
            is CommandResult.Message -> println(result.text)
            is CommandResult.Error -> println(formatter.error(result.message))
            is CommandResult.Exit -> { /* 退出处理 */ }
            is CommandResult.Continue -> { /* 继续等待 */ }
        }
    }
}
