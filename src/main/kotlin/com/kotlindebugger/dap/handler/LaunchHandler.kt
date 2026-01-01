package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.core.jdi.DebugTarget
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.common.model.DebugEvent
import com.kotlindebugger.core.event.DebugEventListener
import kotlinx.serialization.json.*

class LaunchHandler(private val server: DAPServer) : RequestHandler {
    override val command = "launch"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val mainClass = args?.get("mainClass")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("mainClass is required")

        val classpath = args["classpath"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val jvmArgs = args["jvmArgs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val programArgs = args["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val noDebug = args["noDebug"]?.jsonPrimitive?.booleanOrNull ?: false
        
        // 解析源路径配置
        val sourcePaths = args["sourcePaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        server.sourcePathResolver.setSourcePaths(sourcePaths)

        val target = DebugTarget.Launch(
            mainClass = mainClass,
            classpath = classpath,
            jvmArgs = jvmArgs,
            programArgs = programArgs,
            suspend = !noDebug
        )

        val debugSession = DebugSession(target)
        server.setDebugSession(debugSession)
        debugSession.addListener(object : DebugEventListener {
            override fun onEvent(event: DebugEvent) {
                handleDebugEvent(event)
            }
        })

        debugSession.start()
        server.eventEmitter.sendInitialized()

        return null
    }

    private fun handleDebugEvent(event: DebugEvent) {
        when (event) {
            is DebugEvent.BreakpointHit -> {
                server.eventEmitter.sendStopped("breakpoint", event.threadId.toInt())
            }
            is DebugEvent.StepCompleted -> {
                server.eventEmitter.sendStopped("step", event.threadId.toInt())
            }
            is DebugEvent.VMDeath -> {
                server.eventEmitter.sendExited(0)
                server.eventEmitter.sendTerminated()
            }
            else -> {}
        }
    }
}

class AttachHandler(private val server: DAPServer) : RequestHandler {
    override val command = "attach"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        val host = args?.get("host")?.jsonPrimitive?.content ?: "localhost"
        val port = args?.get("port")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("port is required")

        // 解析源路径配置
        val sourcePaths = args["sourcePaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        server.sourcePathResolver.setSourcePaths(sourcePaths)

        val target = DebugTarget.Attach(host = host, port = port)

        val debugSession = DebugSession(target)
        server.setDebugSession(debugSession)
        debugSession.addListener(object : DebugEventListener {
            override fun onEvent(event: DebugEvent) {
                handleDebugEvent(event)
            }
        })

        debugSession.start()
        server.eventEmitter.sendInitialized()

        return null
    }

    private fun handleDebugEvent(event: DebugEvent) {
        when (event) {
            is DebugEvent.BreakpointHit -> {
                server.eventEmitter.sendStopped("breakpoint", event.threadId.toInt())
            }
            is DebugEvent.StepCompleted -> {
                server.eventEmitter.sendStopped("step", event.threadId.toInt())
            }
            is DebugEvent.VMDeath -> {
                server.eventEmitter.sendExited(0)
                server.eventEmitter.sendTerminated()
            }
            else -> {}
        }
    }
}
