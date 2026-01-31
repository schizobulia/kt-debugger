package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*

/**
 * 处理 source 请求
 * 
 * 当客户端需要获取源代码内容时发送此请求，
 * 通常用于没有本地文件的情况（如远程调试或反编译的代码）。
 */
class SourceHandler(private val server: DAPServer) : RequestHandler {
    override val command = "source"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'source' command")

        val sourceReference = args?.get("sourceReference")?.jsonPrimitive?.intOrNull
            ?: args?.get("source")?.jsonObject?.get("sourceReference")?.jsonPrimitive?.intOrNull

        if (sourceReference == null || sourceReference == 0) {
            // sourceReference 为 0 或未提供，无法获取源代码
            return buildJsonObject {
                put("content", "")
                put("mimeType", "text/x-kotlin")
            }
        }

        // 目前返回空内容，因为我们主要依赖本地文件
        // 在更完整的实现中，可以从 JVM 的类加载器获取源代码
        // 或者使用反编译器生成源代码
        Logger.debug("Source request for reference: $sourceReference")
        
        return buildJsonObject {
            put("content", "// Source not available for reference: $sourceReference")
            put("mimeType", "text/x-kotlin")
        }
    }
}
