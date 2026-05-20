package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.protocol.Capabilities
import com.kotlindebugger.dap.protocol.ExceptionBreakpointsFilter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json

class InitializeHandler : RequestHandler {
    override val command = "initialize"
    
    private val json = Json { encodeDefaults = true }

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val capabilities = Capabilities(
            supportsConfigurationDoneRequest = true,
            supportsFunctionBreakpoints = true,
            supportsConditionalBreakpoints = true,
            supportsEvaluateForHovers = true,
            supportsStepBack = false,
            supportsSetVariable = true,
            supportsRestartFrame = false,
            supportsStepInTargetsRequest = false,
            supportsValueFormattingOptions = true,
            supportsExceptionInfoRequest = true,
            supportsCompletionsRequest = true,
            supportsHotCodeReplace = true,
            // 声明支持 Logpoints
            supportsLogPoints = true,
            // 声明支持命中次数断点
            supportsHitConditionalBreakpoints = true,
            // 声明支持 breakpointLocations 请求
            supportsBreakpointLocationsRequest = true,
            // 声明支持 loadedSources 请求
            supportsLoadedSourcesRequest = true,
            // 声明支持 terminate 请求
            supportsTerminateRequest = true,
            exceptionBreakpointFilters = listOf(
                ExceptionBreakpointsFilter(
                    filter = "caught",
                    label = "Caught Exceptions",
                    default = false,
                    description = "Break on caught exceptions"
                ),
                ExceptionBreakpointsFilter(
                    filter = "uncaught",
                    label = "Uncaught Exceptions",
                    default = true,
                    description = "Break on uncaught exceptions"
                )
            )
        )
        return json.encodeToJsonElement(capabilities)
    }
}
