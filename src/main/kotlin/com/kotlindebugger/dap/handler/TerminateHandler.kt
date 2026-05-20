package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 处理 terminate 请求
 *
 * terminate 请求用于终止目标程序（杀掉被调试的进程），
 * 与 disconnect 不同，terminate 会主动结束目标进程。
 */
class TerminateHandler(private val server: DAPServer) : RequestHandler {
    override val command = "terminate"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        Logger.info("Handling 'terminate' command")
        server.variableReferenceManager.clear()

        val debugSession = server.getDebugSession()
        if (debugSession != null) {
            // vm.exit() 会通知目标 JVM 退出，触发 VMDeath 事件
            // 事件处理器将负责发送 terminated 事件给 DAP 客户端
            debugSession.terminate()
        }

        return null
    }
}
