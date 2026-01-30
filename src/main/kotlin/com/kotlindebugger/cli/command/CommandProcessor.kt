package com.kotlindebugger.cli.command

import com.kotlindebugger.cli.output.OutputFormatter
import com.kotlindebugger.common.model.*
import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.core.SessionState
import com.kotlindebugger.core.coroutine.CoroutineState
import com.kotlindebugger.core.event.DebugEventListener
import com.kotlindebugger.core.jdi.DebugTarget
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 命令执行结果
 */
sealed class CommandResult {
    object Success : CommandResult()
    data class Message(val text: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
    object Exit : CommandResult()
    object Continue : CommandResult() // 继续等待事件
}

/**
 * 命令处理器
 */
class CommandProcessor(
    private val formatter: OutputFormatter = OutputFormatter()
) : DebugEventListener {

    private var session: DebugSession? = null
    private var shouldExit = false

    // 事件队列，用于在主线程中处理输出
    private val eventQueue = ConcurrentLinkedQueue<() -> Unit>()

    /**
     * 处理命令
     */
    fun process(input: String): CommandResult {
        // 先处理队列中的事件输出
        processEventQueue()

        // 特殊处理 "info locals" 命令
        if (input.trim().lowercase() == "info locals") {
            return cmdLocals()
        }

        val parts = input.trim().split(Regex("\\s+"), limit = 2)
        if (parts.isEmpty() || parts[0].isBlank()) {
            return CommandResult.Success
        }

        val command = parts[0].lowercase()
        val args = parts.getOrNull(1) ?: ""

        return try {
            when (command) {
                "run", "r" -> cmdRun(args)
                "attach" -> cmdAttach(args)
                "quit", "q", "exit" -> cmdQuit()
                "help", "h", "?" -> cmdHelp()

                "break", "b" -> cmdBreakpoint(args)
                "delete", "d" -> cmdDelete(args)
                "enable" -> cmdEnableBreakpoint(args)
                "disable" -> cmdDisableBreakpoint(args)

                "continue", "c" -> cmdContinue()
                "step", "s" -> cmdStep()
                "next", "n" -> cmdNext()
                "finish", "f" -> cmdFinish()
                "interrupt" -> interrupt()

                "backtrace", "bt", "where" -> cmdBacktrace()
                "frame", "fr" -> cmdFrame(args)
                "up" -> cmdUp()
                "down" -> cmdDown()

                "locals", "info locals" -> cmdLocals()
                "print", "p" -> cmdPrint(args)

                "threads" -> cmdThreads()
                "thread", "t" -> cmdThread(args)

                "coroutines" -> cmdCoroutines()

                "list", "l" -> cmdListBreakpoints()

                "status" -> cmdStatus()

                else -> CommandResult.Error("Unknown command: $command. Type 'help' for available commands.")
            }
        } catch (e: Exception) {
            CommandResult.Error("Error: ${e.message}")
        }
    }

    // ==================== 会话管理命令 ====================

    private fun cmdRun(args: String): CommandResult {
        if (session != null && !session!!.isTerminated()) {
            return CommandResult.Error("A debug session is already running. Use 'quit' to end it first.")
        }

        val parts = args.split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0].isBlank()) {
            return CommandResult.Error("Usage: run <mainClass> [-cp <classpath>] [args...]")
        }

        val mainClass = parts[0]
        var classpath = listOf<String>()
        val programArgs = mutableListOf<String>()

        var i = 1
        while (i < parts.size) {
            when (parts[i]) {
                "-cp", "-classpath" -> {
                    if (i + 1 < parts.size) {
                        classpath = parts[i + 1].split(File.pathSeparator)
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> {
                    programArgs.add(parts[i])
                    i++
                }
            }
        }

        return try {
            val target = DebugTarget.Launch(
                mainClass = mainClass,
                classpath = classpath,
                programArgs = programArgs,
                suspend = true
            )

            val newSession = DebugSession(target)
            newSession.addListener(this)
            newSession.start()

            session = newSession
            CommandResult.Message(formatter.success("Started debugging $mainClass"))
        } catch (e: Exception) {
            CommandResult.Error("Failed to start debugging $mainClass: ${e.message}")
        }
    }

    private fun cmdAttach(args: String): CommandResult {
        if (session != null && !session!!.isTerminated()) {
            return CommandResult.Error("A debug session is already running.")
        }

        val parts = args.split(":")
        if (parts.size != 2) {
            return CommandResult.Error("Usage: attach <host>:<port>")
        }

        val host = parts[0]
        val port = parts[1].toIntOrNull() ?: return CommandResult.Error("Invalid port number")

        return try {
            val target = DebugTarget.Attach(host, port)
            val newSession = DebugSession(target)
            newSession.addListener(this)
            newSession.start()

            session = newSession
            CommandResult.Message(formatter.success("Attached to $host:$port"))
        } catch (e: Exception) {
            CommandResult.Error("Failed to attach to $host:$port: ${e.message}")
        }
    }

    private fun cmdQuit(): CommandResult {
        session?.stop()
        shouldExit = true
        return CommandResult.Exit
    }

    private fun cmdHelp(): CommandResult {
        val helpText = """
            ${formatter.header("Kotlin Debugger Commands")}

            ${formatter.bold("Session:")}
              run <class> [-cp path]  Launch a program
              attach <host>:<port>    Attach to a remote JVM
              quit, q                 Exit debugger

            ${formatter.bold("Breakpoints:")}
              break, b <file>:<line>  Set breakpoint
              delete, d <id>          Delete breakpoint
              list, l                 List breakpoints
              enable <id>             Enable breakpoint
              disable <id>            Disable breakpoint

            ${formatter.bold("Execution:")}
              continue, c             Continue execution
              interrupt               Interrupt running program (use 'interrupt' command)
              step, s                 Step into
              next, n                 Step over
              finish, f               Step out

            ${formatter.bold("Stack:")}
              backtrace, bt           Show stack trace
              frame, fr <n>           Select frame
              up                      Move up one frame
              down                    Move down one frame

            ${formatter.bold("Variables:")}
              locals                  Show local variables
              print, p <name>         Print variable value

            ${formatter.bold("Threads:")}
              threads                 List all threads
              thread, t <id>          Switch to thread

            ${formatter.bold("Coroutines:")}
              coroutines              List all coroutines

            ${formatter.bold("Info:")}
              status                  Show session status
              help, h                 Show this help
        """.trimIndent()

        return CommandResult.Message(helpText)
    }

    // ==================== 断点命令 ====================

    private fun cmdBreakpoint(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        // 解析 file:line 格式
        val colonIndex = args.lastIndexOf(':')
        if (colonIndex < 0) {
            return CommandResult.Error("Usage: break <file>:<line>")
        }

        val file = args.substring(0, colonIndex)
        val line = args.substring(colonIndex + 1).toIntOrNull()
            ?: return CommandResult.Error("Invalid line number")

        val bp = s.addBreakpoint(file, line)
        return CommandResult.Message(formatter.success("Breakpoint ${bp.id} set at $file:$line"))
    }

    private fun cmdDelete(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        val id = args.trim().toIntOrNull()
            ?: return CommandResult.Error("Usage: delete <breakpoint-id>")

        return if (s.removeBreakpoint(id)) {
            CommandResult.Message(formatter.success("Breakpoint $id deleted"))
        } else {
            CommandResult.Error("Breakpoint $id not found")
        }
    }

    private fun cmdListBreakpoints(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        val breakpoints = s.listBreakpoints()
        if (breakpoints.isEmpty()) {
            return CommandResult.Message("No breakpoints set")
        }

        val rows = breakpoints.map { bp ->
            when (bp) {
                is Breakpoint.LineBreakpoint -> listOf(
                    bp.id.toString(),
                    "${bp.file}:${bp.line}",
                    if (bp.enabled) "enabled" else "disabled",
                    bp.condition ?: ""
                )
                is Breakpoint.MethodBreakpoint -> listOf(
                    bp.id.toString(),
                    "${bp.className}.${bp.methodName}()",
                    if (bp.enabled) "enabled" else "disabled",
                    bp.condition ?: ""
                )
            }
        }

        val table = formatter.table(
            listOf("ID", "Location", "Status", "Condition"),
            rows
        )
        return CommandResult.Message(table)
    }

    private fun cmdEnableBreakpoint(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")
        val id = args.trim().toIntOrNull() ?: return CommandResult.Error("Usage: enable <id>")

        return if (s.enableBreakpoint(id)) {
            CommandResult.Message(formatter.success("Breakpoint $id enabled"))
        } else {
            CommandResult.Error("Breakpoint $id not found")
        }
    }

    private fun cmdDisableBreakpoint(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")
        val id = args.trim().toIntOrNull() ?: return CommandResult.Error("Usage: disable <id>")

        return if (s.disableBreakpoint(id)) {
            CommandResult.Message(formatter.success("Breakpoint $id disabled"))
        } else {
            CommandResult.Error("Breakpoint $id not found")
        }
    }

    // ==================== 执行控制命令 ====================

    private fun cmdContinue(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        s.resume()
        // 程序继续执行，断点命中时会显示相关信息
        return CommandResult.Success
    }

    private fun cmdStep(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        s.stepInto()
        return CommandResult.Success
    }

    private fun cmdNext(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        s.stepOver()
        return CommandResult.Success
    }

    private fun cmdFinish(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        s.stepOut()
        return CommandResult.Success
    }

    // ==================== 栈帧命令 ====================

    private fun cmdBacktrace(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        val frames = s.getStackFrames()
        if (frames.isEmpty()) {
            return CommandResult.Message("No stack frames")
        }

        val currentIdx = s.getCurrentFrameIndex()
        val sb = StringBuilder()

        frames.forEach { frame ->
            val prefix = if (frame.index == currentIdx) formatter.green("→ ") else "  "
            val inlineMarker = if (frame.isInline) formatter.yellow(" [inline]") else ""
            val nativeMarker = if (frame.isNative) formatter.dim(" [native]") else ""

            val location = frame.location?.let { "${it.file}:${it.line}" } ?: "unknown"
            sb.appendLine("$prefix#${frame.index}  ${frame.className}.${frame.methodName}($location)$inlineMarker$nativeMarker")
        }

        return CommandResult.Message(sb.toString())
    }

    private fun cmdFrame(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")
        val index = args.trim().toIntOrNull() ?: return CommandResult.Error("Usage: frame <number>")

        return if (s.selectFrame(index)) {
            val frame = s.getCurrentFrame()
            CommandResult.Message(formatter.success("Selected frame #$index: ${frame?.methodName ?: "unknown"}"))
        } else {
            CommandResult.Error("Invalid frame number")
        }
    }

    private fun cmdUp(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")
        val current = s.getCurrentFrameIndex()
        return cmdFrame((current + 1).toString())
    }

    private fun cmdDown(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")
        val current = s.getCurrentFrameIndex()
        if (current <= 0) {
            return CommandResult.Error("Already at bottom frame")
        }
        return cmdFrame((current - 1).toString())
    }

    // ==================== 变量命令 ====================

    private fun cmdLocals(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        val variables = s.getLocalVariables()
        if (variables.isEmpty()) {
            return CommandResult.Message("No local variables")
        }

        val sb = StringBuilder()
        sb.appendLine(formatter.header("Local Variables:"))
        variables.forEach { v ->
            val typeStr = formatter.dim(v.typeName)
            sb.appendLine("  ${formatter.cyan(v.name)}: $typeStr = ${v.value}")
        }

        return CommandResult.Message(sb.toString())
    }

    private fun cmdPrint(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        val name = args.trim()
        if (name.isBlank()) {
            return CommandResult.Error("Usage: print <variable>")
        }

        val variable = s.getVariable(name)
            ?: return CommandResult.Error("Variable '$name' not found")

        return CommandResult.Message("${formatter.cyan(name)}: ${formatter.dim(variable.typeName)} = ${variable.value}")
    }

    // ==================== 线程命令 ====================

    private fun cmdThreads(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        val threads = s.getThreads()
        val currentThread = s.getCurrentThread()

        val rows = threads.map { t ->
            val marker = if (t.id == currentThread?.id) "*" else ""
            listOf(
                "$marker${t.id}",
                t.name,
                t.status.name.lowercase(),
                if (t.isSuspended) "suspended" else "running"
            )
        }

        val table = formatter.table(
            listOf("ID", "Name", "Status", "State"),
            rows
        )
        return CommandResult.Message(table)
    }

    private fun cmdThread(args: String): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")
        val id = args.trim().toLongOrNull() ?: return CommandResult.Error("Usage: thread <id>")

        return if (s.selectThread(id)) {
            CommandResult.Message(formatter.success("Switched to thread $id"))
        } else {
            CommandResult.Error("Thread $id not found or not suspended")
        }
    }

    // ==================== 协程命令 ====================

    private fun cmdCoroutines(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        if (!s.isSuspended()) {
            return CommandResult.Error("Program is not suspended")
        }

        val coroutines = s.getCoroutines()
        if (coroutines.isEmpty()) {
            val status = s.getCoroutineDebugStatus()
            return CommandResult.Message("No coroutines found.\n${formatter.dim(status)}")
        }

        val sb = StringBuilder()
        sb.appendLine(formatter.header("Coroutines (${coroutines.size}):"))

        // 按状态分组显示
        val grouped = coroutines.groupBy { it.state }

        // 先显示 RUNNING 的协程
        grouped[CoroutineState.RUNNING]?.let { running ->
            sb.appendLine(formatter.green("  Running (${running.size}):"))
            running.forEach { coroutine ->
                val threadInfo = coroutine.lastObservedThread?.name()?.let { " on $it" } ?: ""
                sb.appendLine("    ${formatter.bold(coroutine.name)}:${coroutine.id ?: "?"} ${formatter.green("RUNNING")}$threadInfo")
                if (coroutine.dispatcher != null) {
                    sb.appendLine("      Dispatcher: ${coroutine.dispatcher}")
                }
            }
        }

        // 显示 SUSPENDED 的协程
        grouped[CoroutineState.SUSPENDED]?.let { suspended ->
            sb.appendLine(formatter.yellow("  Suspended (${suspended.size}):"))
            suspended.forEach { coroutine ->
                sb.appendLine("    ${formatter.bold(coroutine.name)}:${coroutine.id ?: "?"} ${formatter.yellow("SUSPENDED")}")
                if (coroutine.dispatcher != null) {
                    sb.appendLine("      Dispatcher: ${coroutine.dispatcher}")
                }
                // 显示栈顶帧
                coroutine.continuationStackFrames.firstOrNull()?.let { frame ->
                    val location = frame.location?.toString() ?: "unknown"
                    sb.appendLine("      at ${frame.className}.${frame.methodName}($location)")
                }
            }
        }

        // 显示 CREATED 的协程
        grouped[CoroutineState.CREATED]?.let { created ->
            sb.appendLine(formatter.dim("  Created (${created.size}):"))
            created.forEach { coroutine ->
                sb.appendLine("    ${formatter.bold(coroutine.name)}:${coroutine.id ?: "?"} ${formatter.dim("CREATED")}")
            }
        }

        // 显示调试状态
        if (!s.isCoroutineDebugProbesInstalled()) {
            sb.appendLine()
            sb.appendLine(formatter.dim("Note: Coroutine debug probes are not installed."))
            sb.appendLine(formatter.dim("Add -Dkotlinx.coroutines.debug to enable full coroutine debugging."))
        }

        return CommandResult.Message(sb.toString())
    }

    
    // ==================== 信息命令 ====================

    private fun cmdStatus(): CommandResult {
        val s = session

        if (s == null) {
            return CommandResult.Message("No active debug session")
        }

        val state = when (s.getState()) {
            SessionState.NOT_STARTED -> "not started"
            SessionState.RUNNING -> formatter.green("running")
            SessionState.SUSPENDED -> formatter.yellow("suspended")
            SessionState.TERMINATED -> formatter.red("terminated")
        }

        val sb = StringBuilder()
        sb.appendLine("Session state: $state")

        if (s.isSuspended()) {
            val thread = s.getCurrentThread()
            if (thread != null) {
                sb.appendLine("Current thread: ${thread.name} (#${thread.id})")
            }

            val position = s.getCurrentPosition()
            if (position != null) {
                sb.appendLine("Location: ${position.file}:${position.line}")
            }

            val frame = s.getCurrentFrame()
            if (frame != null) {
                sb.appendLine("Frame: ${frame.className}.${frame.methodName}")
            }
        }

        return CommandResult.Message(sb.toString())
    }

    // ==================== 事件处理 ====================

    override fun onEvent(event: DebugEvent) {
        when (event) {
            is DebugEvent.BreakpointHit -> {
                eventQueue.offer {
                    val location = event.location?.let { "${it.file}:${it.line}" } ?: "unknown"
                    println()
                    println(formatter.highlightBreakpoint(event.location?.file ?: "unknown",
                                                       event.location?.line ?: 0))

                    // 自动显示栈帧
                    showCurrentStackFrameInMainThread()

                    // 确保输出缓冲区刷新，为下一个命令提示符做准备
                    println() // 确保换行
                    System.out.flush()
                }
            }

            is DebugEvent.StepCompleted -> {
                eventQueue.offer {
                    val location = event.location?.let { "${it.file}:${it.line}" } ?: "unknown"
                    println()
                    println(formatter.highlightStep(location))

                    // 自动显示栈帧
                    showCurrentStackFrameInMainThread()

                    // 确保输出缓冲区刷新，为下一个命令提示符做准备
                    println() // 确保换行
                    System.out.flush()
                }
            }

            is DebugEvent.VMStarted -> {
                println(formatter.green("VM started, main thread: ${event.mainThread.name}"))
            }

            is DebugEvent.VMDisconnected -> {
                println()
                println(formatter.yellow("VM disconnected"))
            }

            is DebugEvent.VMDeath -> {
                println()
                println(formatter.yellow("VM terminated"))
            }

            is DebugEvent.ExceptionThrown -> {
                val location = event.location?.let { " at ${it.file}:${it.line}" } ?: ""
                println()
                println(formatter.red("Exception: ${event.exceptionClass}$location"))
                if (event.message != null) {
                    println(formatter.red("  Message: ${event.message}"))
                }
            }

            else -> {}
        }
    }

    /**
     * 显示当前栈帧和位置信息（在主线程中执行）
     */
    private fun showCurrentStackFrameInMainThread() {
        val s = session ?: return

        // 显示当前位置
        val frame = s.getCurrentFrame()
        if (frame != null) {
            val location = frame.location?.let { "${it.file}:${it.line}" } ?: "unknown"
            println()
            println(formatter.bold("Current frame:"))
            println("  ${frame.className}.${frame.methodName}($location)")
        }

        // 显示局部变量摘要
        val variables = s.getLocalVariables()
        if (variables.isNotEmpty()) {
            println()
            println(formatter.bold("Local variables:"))
            variables.take(5).forEach { v ->
                println("  ${formatter.cyan(v.name)}: ${formatter.dim(v.typeName)} = ${v.value}")
            }
            if (variables.size > 5) {
                println(formatter.dim("  ... and ${variables.size - 5} more (use 'locals' to see all)"))
            }
        }
        println()
    }

    /**
     * 显示当前栈帧和位置信息
     */
    private fun showCurrentStackFrame() {
        val s = session ?: return

        // 显示当前位置
        val frame = s.getCurrentFrame()
        if (frame != null) {
            val location = frame.location?.let { "${it.file}:${it.line}" } ?: "unknown"
            println()
            println(formatter.bold("Current frame:"))
            println("  ${frame.className}.${frame.methodName}($location)")
        }

        // 显示局部变量摘要
        val variables = s.getLocalVariables()
        if (variables.isNotEmpty()) {
            println()
            println(formatter.bold("Local variables:"))
            variables.take(5).forEach { v ->
                println("  ${formatter.cyan(v.name)}: ${formatter.dim(v.typeName)} = ${v.value}")
            }
            if (variables.size > 5) {
                println(formatter.dim("  ... and ${variables.size - 5} more (use 'locals' to see all)"))
            }
        }
        println()
    }

    fun shouldExit(): Boolean = shouldExit

    fun getSession(): DebugSession? = session

    /**
     * 处理事件队列中的输出（在主线程中执行）
     */
    fun processEventQueue() {
        while (eventQueue.isNotEmpty()) {
            eventQueue.poll()?.invoke()
        }
    }

    /**
     * 显示智能提示（根据当前调试状态）
     */
    fun showSmartHints() {
        val s = session ?: return

        val hints = mutableListOf<String>()

        when {
            s.isSuspended() -> hints.add("continue | step | next | finish")
            s.isRunning() -> hints.add("interrupt to pause")
            s.isTerminated() -> hints.add("run <class> to start")
        }

        if (s.listBreakpoints().isNotEmpty()) {
            hints.add("list to see breakpoints")
        }

        if (hints.isNotEmpty()) {
            // 随机显示提示，避免太烦人
            if (Math.random() < 0.3) {
                println(formatter.hint(hints.joinToString(" | ")))
            }
        }
    }

    /**
     * 中断当前执行
     */
    fun interrupt(): CommandResult {
        val s = session ?: return CommandResult.Error("No active debug session")

        return try {
            if (s.isSuspended()) {
                CommandResult.Message(formatter.info("Program is already suspended"))
            } else {
                s.suspend()
                CommandResult.Message(formatter.warning("Program interrupted"))
            }
        } catch (e: Exception) {
            CommandResult.Error("Failed to interrupt: ${e.message}")
        }
    }
}
