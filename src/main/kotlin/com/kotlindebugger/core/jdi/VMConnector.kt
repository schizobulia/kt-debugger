package com.kotlindebugger.core.jdi

import com.sun.jdi.*
import com.sun.jdi.connect.*
import com.sun.jdi.event.*
import java.io.File

/**
 * 调试目标配置
 */
sealed class DebugTarget {
    /**
     * 获取源代码根目录列表（可选）
     */
    abstract val sourceRoots: List<String>

    /**
     * 启动新进程调试
     */
    data class Launch(
        val mainClass: String,
        val classpath: List<String>,
        val jvmArgs: List<String> = emptyList(),
        val programArgs: List<String> = emptyList(),
        val workingDir: String? = null,
        val env: Map<String, String> = emptyMap(),
        val suspend: Boolean = true,
        override val sourceRoots: List<String> = emptyList()
    ) : DebugTarget()

    /**
     * 附加到已有进程
     */
    data class Attach(
        val host: String = "localhost",
        val port: Int,
        val suspend: Boolean = true,
        override val sourceRoots: List<String> = emptyList()
    ) : DebugTarget()

    /**
     * 通过进程ID附加
     */
    data class AttachPid(
        val pid: Long,
        val suspend: Boolean = true,
        override val sourceRoots: List<String> = emptyList()
    ) : DebugTarget()
}

/**
 * VM 连接管理器
 * 负责建立和管理与目标 JVM 的连接
 */
class VMConnector {

    // 通过 ProcessBuilder 启动的子进程（用于 workingDir/env 支持）
    private var launchedProcess: Process? = null

    /**
     * 获取通过 ProcessBuilder 启动的子进程（如有）
     */
    fun getLaunchedProcess(): Process? = launchedProcess

    /**
     * 连接到目标 JVM
     */
    fun connect(target: DebugTarget): VirtualMachine {
        return when (target) {
            is DebugTarget.Launch -> launchVM(target)
            is DebugTarget.Attach -> attachVM(target)
            is DebugTarget.AttachPid -> attachByPid(target)
        }
    }

    /**
     * 启动新的 JVM 进程
     * 如果设置了 workingDir 或 env，使用 ProcessBuilder 方式启动以支持这些特性
     */
    private fun launchVM(target: DebugTarget.Launch): VirtualMachine {
        // 如果需要 workingDir 或环境变量，使用 ProcessBuilder 方式
        if (target.workingDir != null || target.env.isNotEmpty()) {
            return launchVMWithProcessBuilder(target)
        }

        val vmm = Bootstrap.virtualMachineManager()

        // 获取启动连接器
        val connector = vmm.launchingConnectors().find {
            it.name() == "com.sun.jdi.CommandLineLaunch"
        } ?: throw IllegalStateException("CommandLineLaunch connector not found")

        val args = connector.defaultArguments().toMutableMap()

        // 构建命令行
        val classpath = target.classpath.joinToString(File.pathSeparator)
        val jvmOptions = buildList {
            if (classpath.isNotEmpty()) {
                add("-cp")
                add(classpath)
            }
            addAll(target.jvmArgs)
        }.joinToString(" ")

        val mainCmd = buildList {
            add(target.mainClass)
            addAll(target.programArgs)
        }.joinToString(" ")

        args["main"]?.setValue(mainCmd)
        args["options"]?.setValue(jvmOptions)
        args["suspend"]?.setValue(target.suspend.toString())

        return connector.launch(args)
    }

    /**
     * 使用 ProcessBuilder 启动 JVM 进程，支持 workingDir 和环境变量
     */
    private fun launchVMWithProcessBuilder(target: DebugTarget.Launch): VirtualMachine {
        val port = findFreePort()
        val javaExe = getJavaExecutable()

        val command = mutableListOf<String>()
        command.add(javaExe)
        // JDWP agent 配置
        command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:$port")
        if (target.classpath.isNotEmpty()) {
            command.add("-cp")
            command.add(target.classpath.joinToString(File.pathSeparator))
        }
        command.addAll(target.jvmArgs)
        command.add(target.mainClass)
        command.addAll(target.programArgs)

        val pb = ProcessBuilder(command)
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)

        // 设置工作目录
        target.workingDir?.let { pb.directory(File(it)) }

        // 设置环境变量（合并到现有环境中）
        if (target.env.isNotEmpty()) {
            pb.environment().putAll(target.env)
        }

        val process = pb.start()
        launchedProcess = process

        // 连接到启动的 JVM，带重试（等待 JVM 启动 JDWP）
        val attachTarget = DebugTarget.Attach(host = "localhost", port = port)
        return connectWithRetry(attachTarget, process)
    }

    /**
     * 带重试的 Attach 连接，用于等待 JVM JDWP 就绪
     */
    private fun connectWithRetry(attachTarget: DebugTarget.Attach, process: Process): VirtualMachine {
        val maxRetries = 30
        val retryDelayMs = 300L

        repeat(maxRetries) { attempt ->
            if (!process.isAlive) {
                throw IllegalStateException("Launched JVM process terminated before JDWP connection was established")
            }
            try {
                return attachVM(attachTarget)
            } catch (e: Exception) {
                if (attempt < maxRetries - 1) {
                    Thread.sleep(retryDelayMs)
                } else {
                    throw IllegalStateException(
                        "Could not attach to launched JVM after $maxRetries attempts: ${e.message}"
                    )
                }
            }
        }
        throw IllegalStateException("Should not reach here")
    }

    /**
     * 查找可用的随机端口
     */
    private fun findFreePort(): Int {
        java.net.ServerSocket(0).use { return it.localPort }
    }

    /**
     * 获取当前 JVM 可执行文件路径
     */
    private fun getJavaExecutable(): String {
        return ProcessHandle.current().info().command().orElseGet {
            "${System.getProperty("java.home")}/bin/java"
        }
    }

    /**
     * 通过 Socket 附加到远程 JVM
     */
    private fun attachVM(target: DebugTarget.Attach): VirtualMachine {
        val vmm = Bootstrap.virtualMachineManager()

        // 获取 Socket 附加连接器
        val connector = vmm.attachingConnectors().find {
            it.name() == "com.sun.jdi.SocketAttach"
        } ?: throw IllegalStateException("SocketAttach connector not found")

        val args = connector.defaultArguments().toMutableMap()
        args["hostname"]?.setValue(target.host)
        args["port"]?.setValue(target.port.toString())

        return connector.attach(args)
    }

    /**
     * 通过进程 ID 附加
     */
    private fun attachByPid(target: DebugTarget.AttachPid): VirtualMachine {
        val vmm = Bootstrap.virtualMachineManager()

        // 获取进程附加连接器
        val connector = vmm.attachingConnectors().find {
            it.name() == "com.sun.jdi.ProcessAttach"
        } ?: throw IllegalStateException("ProcessAttach connector not found")

        val args = connector.defaultArguments().toMutableMap()
        args["pid"]?.setValue(target.pid.toString())

        return connector.attach(args)
    }

    companion object {
        /**
         * 获取可用的连接器列表
         */
        fun listConnectors(): List<Connector> {
            return Bootstrap.virtualMachineManager().allConnectors()
        }

        /**
         * 检查 JDI 是否可用
         */
        fun isJdiAvailable(): Boolean {
            return try {
                Bootstrap.virtualMachineManager()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
