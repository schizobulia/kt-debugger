package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.core.coroutine.CoroutineInfo
import com.kotlindebugger.core.coroutine.CoroutineState
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*

/**
 * 协程列表请求处理器
 *
 * 自定义 DAP 命令：getCoroutines
 * 响应格式：
 * {
 *   "coroutines": [
 *     {
 *       "id": 1,
 *       "name": "coroutine#1",
 *       "state": "SUSPENDED",
 *       "dispatcher": "Dispatchers.Main",
 *       "description": "\"coroutine#1:1\" SUSPENDED [Dispatchers.Default]"
 *     }
 *   ],
 *   "probesInstalled": true,
 *   "statusMessage": "kotlinx.coroutines debug probes are active"
 * }
 *
 * 参考：IntelliJ Community - CoroutineInfoProvider, XCoroutineFrameworkCommandProcessor
 */
class GetCoroutinesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "getCoroutines"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.info("Handling 'getCoroutines' command")

        val debugSession = server.getDebugSession()
        if (debugSession == null) {
            Logger.warn("No active debug session for getCoroutines")
            return buildJsonObject {
                putJsonArray("coroutines") {}
                put("probesInstalled", false)
                put("statusMessage", "No active debug session")
            }
        }

        val probesInstalled = debugSession.isCoroutineDebugProbesInstalled()
        val statusMessage = debugSession.getCoroutineDebugStatus()
        val coroutines = debugSession.getCoroutines()

        Logger.info("Found ${coroutines.size} coroutine(s), probesInstalled=$probesInstalled")

        return buildJsonObject {
            putJsonArray("coroutines") {
                coroutines.forEach { coroutine ->
                    add(coroutineToJson(coroutine))
                }
            }
            put("probesInstalled", probesInstalled)
            put("statusMessage", statusMessage)
        }
    }

    /**
     * 将协程信息转换为 JSON 格式
     */
    private fun coroutineToJson(coroutine: CoroutineInfo): JsonObject {
        return buildJsonObject {
            put("id", coroutine.id ?: -1L)
            put("name", coroutine.name)
            put("state", coroutine.state.displayName)
            put("dispatcher", coroutine.dispatcher ?: "")
            put("description", coroutine.getDescription())
            put("isSuspended", coroutine.isSuspended)
            put("isRunning", coroutine.isRunning)
            // 协程栈帧（仅包含关键信息，不传输完整 JDI 引用）
            putJsonArray("stackFrames") {
                coroutine.continuationStackFrames.take(20).forEach { frame ->
                    add(buildJsonObject {
                        put("className", frame.className)
                        put("methodName", frame.methodName)
                        put("isCreationFrame", frame.isCreationFrame)
                        frame.location?.let { loc ->
                            put("file", loc.file)
                            put("line", loc.line)
                        }
                    })
                }
            }
        }
    }
}
