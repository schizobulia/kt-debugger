package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.protocol.Breakpoint
import com.kotlindebugger.dap.protocol.Source
import kotlinx.serialization.json.*
import java.io.File

class SetBreakpointsHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setBreakpoints"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val source = args?.get("source")?.jsonObject
        val sourcePath = source?.get("path")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("source.path is required")

        // 提取文件名用于断点匹配
        val fileName = File(sourcePath).name
        
        val breakpoints = args["breakpoints"]?.jsonArray ?: JsonArray(emptyList())

        // 清除该文件的所有断点（按文件名匹配）
        val existingBreakpoints = debugSession.listBreakpoints()
        existingBreakpoints.filterIsInstance<com.kotlindebugger.common.model.Breakpoint.LineBreakpoint>()
            .filter { 
                it.file == sourcePath || 
                it.file == fileName || 
                File(it.file).name == fileName 
            }.forEach {
                debugSession.removeBreakpoint(it.id)
            }

        // 设置新断点（使用文件名以便JDI匹配）
        val result = breakpoints.map { bp ->
            val line = bp.jsonObject["line"]?.jsonPrimitive?.int
                ?: throw IllegalArgumentException("breakpoint.line is required")
            
            // 解析条件表达式（VSCode DAP协议）
            val condition = bp.jsonObject["condition"]?.jsonPrimitive?.contentOrNull
            // 解析 logMessage（Logpoints: 命中时打印日志而非暂停）
            val logMessage = bp.jsonObject["logMessage"]?.jsonPrimitive?.contentOrNull
            // 解析 hitCondition（命中次数条件，如 "5" 表示第5次命中时触发）
            val hitCondition = bp.jsonObject["hitCondition"]?.jsonPrimitive?.contentOrNull
            val hitCount = parseHitCondition(hitCondition)

            try {
                // 使用文件名进行JDI断点设置，传递条件表达式、logMessage 和 hitCount
                val breakpoint = debugSession.addBreakpoint(fileName, line, condition, logMessage, hitCount)
                Breakpoint(
                    id = breakpoint.id,
                    verified = true,
                    line = line,
                    source = Source(name = fileName, path = sourcePath)
                )
            } catch (e: Exception) {
                Breakpoint(
                    id = -1,
                    verified = false,
                    line = line,
                    source = Source(name = fileName, path = sourcePath),
                    message = e.message
                )
            }
        }

        return buildJsonObject {
            put("breakpoints", Json.encodeToJsonElement(result))
        }
    }
}

class ConfigurationDoneHandler(private val server: DAPServer) : RequestHandler {
    override val command = "configurationDone"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        // 配置完成后恢复 VM 运行
        server.getDebugSession()?.forceResume()
        return null
    }
}

/**
 * 解析 DAP 协议中的 hitCondition 字符串，返回命中次数整数值。
 * 支持格式：
 *   "5"    → 第 5 次命中时触发
 *   "== 5" → 第 5 次命中时触发
 *   ">= 5" → 第 5 次命中时触发（JDI 只支持精确次数，近似处理）
 *   "% 5"  → 每第 5 次命中时触发（JDI 只支持精确次数，近似处理）
 */
fun parseHitCondition(hitCondition: String?): Int {
    if (hitCondition.isNullOrBlank()) return 0
    val trimmed = hitCondition.trim()
    // 纯数字
    trimmed.toIntOrNull()?.let { return it.coerceAtLeast(0) }
    // "== N", ">= N", "> N", "= N", "% N" 等格式
    val match = Regex("""[=><%%]+\s*(\d+)""").find(trimmed)
    return match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
}
