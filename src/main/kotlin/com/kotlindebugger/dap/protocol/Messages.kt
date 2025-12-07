package com.kotlindebugger.dap.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class DAPMessage {
    abstract val seq: Int
    abstract val type: String
}

@Serializable
data class DAPRequest(
    override val seq: Int,
    override val type: String = "request",
    val command: String,
    val arguments: JsonObject? = null
) : DAPMessage()

@Serializable
data class DAPResponse(
    override val seq: Int,
    override val type: String = "response",
    val request_seq: Int,
    val success: Boolean,
    val command: String,
    val message: String? = null,
    val body: JsonElement? = null
) : DAPMessage()

@Serializable
data class DAPEvent(
    override val seq: Int,
    override val type: String = "event",
    val event: String,
    val body: JsonElement? = null
) : DAPMessage()
