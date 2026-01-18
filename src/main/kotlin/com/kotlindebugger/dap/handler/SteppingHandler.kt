package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ContinueHandler(private val server: DAPServer) : RequestHandler {
    override val command = "continue"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        // 线程恢复执行前清理所有变量引用
        // 因为当线程恢复后，之前的 StackFrame 和 ObjectReference 都会失效
        server.variableReferenceManager.clear()
        Logger.debug("Cleared variable references before continue")

        debugSession.resume()

        return buildJsonObject {
            put("allThreadsContinued", true)
        }
    }
}

class NextHandler(private val server: DAPServer) : RequestHandler {
    override val command = "next"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        // 单步执行前清理变量引用
        server.variableReferenceManager.clear()
        Logger.debug("Cleared variable references before step over")

        debugSession.stepOver()
        return null
    }
}

class StepInHandler(private val server: DAPServer) : RequestHandler {
    override val command = "stepIn"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        // 单步执行前清理变量引用
        server.variableReferenceManager.clear()
        Logger.debug("Cleared variable references before step into")

        debugSession.stepInto()
        return null
    }
}

class StepOutHandler(private val server: DAPServer) : RequestHandler {
    override val command = "stepOut"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        // 单步执行前清理变量引用
        server.variableReferenceManager.clear()
        Logger.debug("Cleared variable references before step out")

        debugSession.stepOut()
        return null
    }
}

class DisconnectHandler(private val server: DAPServer) : RequestHandler {
    override val command = "disconnect"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        // 断开连接前清理所有引用
        server.variableReferenceManager.clear()
        Logger.debug("Cleared variable references before disconnect")
        
        val debugSession = server.getDebugSession()
        debugSession?.stop()
        return null
    }
}
