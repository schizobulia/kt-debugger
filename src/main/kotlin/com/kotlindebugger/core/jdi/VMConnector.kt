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
     * 启动新进程调试
     */
    data class Launch(
        val mainClass: String,
        val classpath: List<String>,
        val jvmArgs: List<String> = emptyList(),
        val programArgs: List<String> = emptyList(),
        val workingDir: String? = null,
        val suspend: Boolean = true
    ) : DebugTarget()

    /**
     * 附加到已有进程
     */
    data class Attach(
        val host: String = "localhost",
        val port: Int
    ) : DebugTarget()

    /**
     * 通过进程ID附加
     */
    data class AttachPid(
        val pid: Long
    ) : DebugTarget()
}

/**
 * VM 连接管理器
 * 负责建立和管理与目标 JVM 的连接
 */
class VMConnector {

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
     */
    private fun launchVM(target: DebugTarget.Launch): VirtualMachine {
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
