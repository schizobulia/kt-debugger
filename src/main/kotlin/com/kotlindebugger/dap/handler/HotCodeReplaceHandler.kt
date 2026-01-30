package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.core.hotswap.ClassToRedefine
import com.kotlindebugger.core.hotswap.HotCodeReplaceResult
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import kotlinx.serialization.json.*
import java.util.Base64

/**
 * 热代码替换处理器
 * DAP handler for hot code replacement (redefineClasses)
 * 
 * This handler processes requests to redefine classes during a debug session.
 * It supports providing class bytes as Base64 encoded strings or as file paths.
 * 
 * Request arguments:
 * {
 *   "classes": [
 *     {
 *       "className": "com.example.MyClass",
 *       "classBytes": "base64_encoded_bytes"  // Option 1: Base64 encoded class bytes
 *     },
 *     {
 *       "className": "com.example.AnotherClass",
 *       "classFile": "/path/to/AnotherClass.class"  // Option 2: Path to class file
 *     }
 *   ]
 * }
 * 
 * Response body:
 * {
 *   "success": true/false,
 *   "message": "Hot code replacement completed...",
 *   "reloadedClasses": ["com.example.MyClass", ...],
 *   "failedClasses": ["com.example.FailedClass", ...]
 * }
 */
class RedefineClassesHandler(private val server: DAPServer) : RequestHandler {
    override val command = "redefineClasses"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement? {
        Logger.info("Handling 'redefineClasses' command")

        if (session == null) {
            return buildErrorResponse("No active debug session")
        }

        if (args == null) {
            return buildErrorResponse("Missing arguments")
        }

        val classesArray = args["classes"]?.jsonArray
        if (classesArray == null || classesArray.isEmpty()) {
            return buildErrorResponse("No classes specified for redefinition")
        }

        // Check if hot code replacement is supported
        if (!session.canRedefineClasses()) {
            val capabilities = session.getHotCodeReplaceCapabilities()
            val message = if (capabilities != null) {
                "Hot code replacement not supported. Capabilities: $capabilities"
            } else {
                "Hot code replacement not supported by the target VM"
            }
            return buildErrorResponse(message)
        }

        // Parse the classes to redefine
        val classesToRedefine = mutableListOf<ClassToRedefine>()
        val classFilesToRedefine = mutableMapOf<String, String>()
        val parseErrors = mutableListOf<String>()

        for (classEntry in classesArray) {
            try {
                val classObj = classEntry.jsonObject
                val className = classObj["className"]?.jsonPrimitive?.contentOrNull
                if (className.isNullOrBlank()) {
                    parseErrors.add("Missing className in class entry")
                    continue
                }

                // Option 1: Class bytes as Base64
                val classBytesBase64 = classObj["classBytes"]?.jsonPrimitive?.contentOrNull
                if (!classBytesBase64.isNullOrBlank()) {
                    try {
                        val classBytes = Base64.getDecoder().decode(classBytesBase64)
                        classesToRedefine.add(ClassToRedefine(className, classBytes))
                        continue
                    } catch (e: IllegalArgumentException) {
                        parseErrors.add("Invalid Base64 encoding for class $className: ${e.message}")
                        continue
                    }
                }

                // Option 2: Class file path
                val classFile = classObj["classFile"]?.jsonPrimitive?.contentOrNull
                if (!classFile.isNullOrBlank()) {
                    classFilesToRedefine[className] = classFile
                    continue
                }

                parseErrors.add("Class $className has neither classBytes nor classFile specified")
            } catch (e: Exception) {
                parseErrors.add("Error parsing class entry: ${e.message}")
            }
        }

        if (classesToRedefine.isEmpty() && classFilesToRedefine.isEmpty()) {
            val errorMsg = if (parseErrors.isNotEmpty()) {
                "No valid classes to redefine. Errors: ${parseErrors.joinToString("; ")}"
            } else {
                "No valid classes to redefine"
            }
            return buildErrorResponse(errorMsg)
        }

        // Perform hot code replacement
        val results = mutableListOf<HotCodeReplaceResult>()

        // Redefine classes from byte arrays
        if (classesToRedefine.isNotEmpty()) {
            val result = session.redefineClasses(classesToRedefine)
            results.add(result)
        }

        // Redefine classes from files
        if (classFilesToRedefine.isNotEmpty()) {
            val result = session.redefineClassesFromFiles(classFilesToRedefine)
            results.add(result)
        }

        // Aggregate results
        return buildAggregatedResponse(results, parseErrors)
    }

    private fun buildErrorResponse(message: String): JsonElement {
        return buildJsonObject {
            put("success", false)
            put("message", message)
            put("reloadedClasses", JsonArray(emptyList()))
            put("failedClasses", JsonArray(emptyList()))
        }
    }

    private fun buildAggregatedResponse(
        results: List<HotCodeReplaceResult>,
        parseErrors: List<String>
    ): JsonElement {
        val allReloadedClasses = mutableListOf<String>()
        val allFailedClasses = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var overallSuccess = true

        for (result in results) {
            when (result) {
                is HotCodeReplaceResult.Success -> {
                    allReloadedClasses.addAll(result.reloadedClasses)
                    messages.add(result.message)
                }
                is HotCodeReplaceResult.Failure -> {
                    overallSuccess = false
                    allFailedClasses.addAll(result.failedClasses)
                    messages.add(result.errorMessage)
                }
                is HotCodeReplaceResult.NotSupported -> {
                    overallSuccess = false
                    messages.add(result.reason)
                }
            }
        }

        if (parseErrors.isNotEmpty()) {
            messages.add("Parse warnings: ${parseErrors.joinToString("; ")}")
        }

        val finalMessage = if (messages.isEmpty()) {
            if (overallSuccess) "Hot code replacement completed" else "Hot code replacement failed"
        } else {
            messages.joinToString(". ")
        }

        return buildJsonObject {
            put("success", overallSuccess && allReloadedClasses.isNotEmpty())
            put("message", finalMessage)
            putJsonArray("reloadedClasses") {
                allReloadedClasses.forEach { add(it) }
            }
            putJsonArray("failedClasses") {
                allFailedClasses.forEach { add(it) }
            }
        }
    }
}
