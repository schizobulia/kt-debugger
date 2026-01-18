package com.kotlindebugger.dap.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val name: String? = null,
    val path: String? = null,
    val sourceReference: Int = 0
)

@Serializable
data class Breakpoint(
    val id: Int,
    val verified: Boolean,
    val line: Int,
    val source: Source? = null,
    val message: String? = null
)

@Serializable
data class SourceBreakpoint(
    val line: Int,
    val column: Int? = null,
    val condition: String? = null
)

@Serializable
data class StackFrame(
    val id: Int,
    val name: String,
    val source: Source? = null,
    val line: Int,
    val column: Int,
    val presentationHint: String? = null
)

@Serializable
data class Thread(
    val id: Int,
    val name: String
)

@Serializable
data class Scope(
    val name: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val variablesReference: Int,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val expensive: Boolean = false
)

@Serializable
data class Variable(
    val name: String,
    val value: String,
    val type: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val variablesReference: Int = 0
)

@Serializable
data class Capabilities(
    val supportsConfigurationDoneRequest: Boolean = true,
    val supportsFunctionBreakpoints: Boolean = false,
    val supportsConditionalBreakpoints: Boolean = false,
    val supportsEvaluateForHovers: Boolean = false,
    val supportsStepBack: Boolean = false,
    val supportsSetVariable: Boolean = false,
    val supportsRestartFrame: Boolean = false,
    val supportsStepInTargetsRequest: Boolean = false
)
