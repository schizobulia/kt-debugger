package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.core.jdi.DebugTarget
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.kotlindebugger.common.model.DebugEvent
import com.kotlindebugger.core.event.DebugEventListener
import kotlinx.serialization.json.*

class LaunchHandler(private val server: DAPServer) : RequestHandler {
    override val command = "launch"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        Logger.info("Handling 'launch' command")

        val mainClass = args?.get("mainClass")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("mainClass is required")

        val classpath = args["classpath"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val jvmArgs = args["jvmArgs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val programArgs = args["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val noDebug = args["noDebug"]?.jsonPrimitive?.booleanOrNull ?: false

        Logger.info("Launching: mainClass=$mainClass")
        Logger.debug("Classpath: $classpath")
        Logger.debug("JVM args: $jvmArgs")
        Logger.debug("Program args: $programArgs")
        Logger.debug("NoDebug: $noDebug")

        // 解析源路径配置
        val sourcePaths = args["sourcePaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        server.sourcePathResolver.setSourcePaths(sourcePaths)
        Logger.debug("Source paths: $sourcePaths")

        val target = DebugTarget.Launch(
            mainClass = mainClass,
            classpath = classpath,
            jvmArgs = jvmArgs,
            programArgs = programArgs,
            suspend = !noDebug
        )

        Logger.info("Creating debug session...")
        val debugSession = DebugSession(target)
        server.setDebugSession(debugSession)
        debugSession.addListener(object : DebugEventListener {
            override fun onEvent(event: DebugEvent) {
                handleDebugEvent(event)
            }
        })

        Logger.info("Starting debug session...")
        debugSession.start()
        Logger.info("Debug session started successfully")

        Logger.info("Sending 'initialized' event")
        server.eventEmitter.sendInitialized()

        return null
    }

    private fun handleDebugEvent(event: DebugEvent) {
        Logger.debug("Debug event: $event")
        when (event) {
            is DebugEvent.BreakpointHit -> {
                server.eventEmitter.sendStopped(
                    reason = "breakpoint",
                    threadId = event.threadId.toInt(),
                    hitBreakpointIds = listOf(event.breakpoint.id)
                )
            }
            is DebugEvent.StepCompleted -> {
                Logger.info("Step completed on thread ${event.threadId}")
                server.eventEmitter.sendStopped("step", event.threadId.toInt())
            }
            is DebugEvent.ExceptionThrown -> {
                Logger.info("Exception thrown: ${event.exceptionClass} on thread ${event.threadId}")
                server.eventEmitter.sendStopped(
                    reason = "exception",
                    threadId = event.threadId.toInt(),
                    description = "${event.exceptionClass}: ${event.message ?: ""}",
                    text = event.exceptionClass
                )
            }
            is DebugEvent.VMDeath -> {
                Logger.info("VM death event received")
                server.eventEmitter.sendExited(0)
                server.eventEmitter.sendTerminated()
            }
            is DebugEvent.VMStarted -> {
                Logger.info("VM started")
            }
            is DebugEvent.VMDisconnected -> {
                Logger.info("VM disconnected")
            }
            else -> {
                Logger.debug("Unhandled debug event: $event")
            }
        }
    }
}

class AttachHandler(private val server: DAPServer) : RequestHandler {
    override val command = "attach"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        Logger.info("Handling 'attach' command")
        val host = args?.get("host")?.jsonPrimitive?.content ?: "localhost"
        val port = args?.get("port")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("port is required")

        Logger.info("Attaching to JVM at $host:$port")
        
        // 解析源路径配置
        val sourcePaths = args["sourcePaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        server.sourcePathResolver.setSourcePaths(sourcePaths)
        Logger.debug("Source paths: $sourcePaths")

        val target = DebugTarget.Attach(host = host, port = port)

        val debugSession = DebugSession(target)
        server.setDebugSession(debugSession)
        debugSession.addListener(object : DebugEventListener {
            override fun onEvent(event: DebugEvent) {
                handleDebugEvent(event)
            }
        })

        Logger.info("Starting debug session...")
        debugSession.start()
        Logger.info("Debug session started, sending 'initialized' event")
        server.eventEmitter.sendInitialized()

        return null
    }

    private fun handleDebugEvent(event: DebugEvent) {
        Logger.debug("Debug event: $event")
        when (event) {
            is DebugEvent.BreakpointHit -> {
                server.eventEmitter.sendStopped(
                    reason = "breakpoint",
                    threadId = event.threadId.toInt(),
                    hitBreakpointIds = listOf(event.breakpoint.id)
                )
            }
            is DebugEvent.StepCompleted -> {
                Logger.info("Step completed on thread ${event.threadId}")
                server.eventEmitter.sendStopped("step", event.threadId.toInt())
            }
            is DebugEvent.ExceptionThrown -> {
                Logger.info("Exception thrown: ${event.exceptionClass} on thread ${event.threadId}")
                server.eventEmitter.sendStopped(
                    reason = "exception",
                    threadId = event.threadId.toInt(),
                    description = "${event.exceptionClass}: ${event.message ?: ""}",
                    text = event.exceptionClass
                )
            }
            is DebugEvent.VMDeath -> {
                Logger.info("VM death event received")
                server.eventEmitter.sendExited(0)
                server.eventEmitter.sendTerminated()
            }
            is DebugEvent.VMStarted -> {
                Logger.info("VM started")
            }
            is DebugEvent.VMDisconnected -> {
                Logger.info("VM disconnected")
            }
            else -> {
                Logger.debug("Unhandled debug event: $event")
            }
        }
    }
}
