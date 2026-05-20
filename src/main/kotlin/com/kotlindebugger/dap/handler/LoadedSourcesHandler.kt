package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*

/**
 * 处理 loadedSources 请求
 *
 * 返回调试会话中当前已加载的所有源文件列表。
 * 客户端可用此信息显示已加载的源文件。
 */
class LoadedSourcesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "loadedSources"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'loadedSources' command")

        val debugSession = server.getDebugSession()
        val sourceNames = debugSession?.getLoadedSourceNames() ?: emptyList()

        val sourcePathResolver = server.sourcePathResolver

        val sources = sourceNames.mapNotNull { name ->
            // 尝试解析完整路径
            val resolvedPath = sourcePathResolver.resolveSourcePath(name)
            buildJsonObject {
                put("name", name)
                if (resolvedPath != null) {
                    put("path", resolvedPath)
                }
            }
        }

        return buildJsonObject {
            put("sources", JsonArray(sources))
        }
    }
}
