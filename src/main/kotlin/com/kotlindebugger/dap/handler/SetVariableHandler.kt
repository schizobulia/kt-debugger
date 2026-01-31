package com.kotlindebugger.dap.handler

import com.kotlindebugger.core.DebugSession
import com.kotlindebugger.dap.DAPServer
import com.kotlindebugger.dap.Logger
import com.kotlindebugger.dap.VariableReferenceType
import com.sun.jdi.*
import kotlinx.serialization.json.*

/**
 * Handler for the DAP 'setVariable' request.
 * Modifies the value of a variable.
 *
 * Request arguments:
 * - variablesReference: The reference to the scope/object containing the variable
 * - name: The name of the variable to set
 * - value: The new value for the variable (as a string expression)
 *
 * Response:
 * - value: The new value of the variable (as displayed string)
 * - type: The type of the new value (optional)
 * - variablesReference: If the new value is structured, this provides a reference for expansion
 */
class SetVariableHandler(private val server: DAPServer) : RequestHandler {
    override val command = "setVariable"

    override suspend fun handle(args: JsonObject?, session: DebugSession?): JsonElement {
        Logger.debug("Handling 'setVariable' command")

        val variablesReference = args?.get("variablesReference")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("variablesReference is required")
        val name = args["name"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("name is required")
        val valueStr = args["value"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("value is required")

        Logger.debug("Setting variable: name=$name, value=$valueStr, variablesReference=$variablesReference")

        val debugSession = server.getDebugSession()
            ?: throw IllegalStateException("No debug session")

        val vm = debugSession.getVirtualMachine()

        // Get the variable reference to find where to set the variable
        val varRef = server.variableReferenceManager.getReference(variablesReference)
            ?: throw IllegalArgumentException("Invalid variablesReference: $variablesReference")

        Logger.debug("Variable reference type: ${varRef.type}")

        return when (varRef.type) {
            VariableReferenceType.STACK_FRAME -> {
                // Setting a local variable in a stack frame
                val stackFrame = server.variableReferenceManager.getStackFrame(varRef, vm)
                    ?: throw IllegalStateException("Stack frame not available")
                setLocalVariable(stackFrame, name, valueStr, vm)
            }
            VariableReferenceType.OBJECT_FIELDS -> {
                // Setting an object field
                val objectRef = varRef.objectRef
                    ?: throw IllegalStateException("Object reference not available")
                setObjectField(objectRef, name, valueStr, vm)
            }
            VariableReferenceType.ARRAY_ELEMENTS -> {
                // Setting an array element
                val arrayRef = varRef.objectRef as? ArrayReference
                    ?: throw IllegalStateException("Array reference not available")
                setArrayElement(arrayRef, name, valueStr, vm)
            }
        }
    }

    /**
     * Set a local variable in a stack frame
     */
    private fun setLocalVariable(frame: StackFrame, name: String, valueStr: String, vm: VirtualMachine): JsonElement {
        // Handle 'this' - cannot be reassigned
        if (name == "this") {
            throw IllegalArgumentException("Cannot reassign 'this'")
        }

        // Find the local variable
        val localVar = frame.visibleVariables().find { it.name() == name }
            ?: throw IllegalArgumentException("Variable not found: $name")

        // Parse the value and create a JDI Value
        val newValue = parseValue(valueStr, localVar.type(), vm)

        // Set the value
        frame.setValue(localVar, newValue)

        Logger.debug("Set local variable $name to $valueStr")

        // Return the new value
        return buildSetVariableResponse(newValue)
    }

    /**
     * Set an object field
     */
    private fun setObjectField(objectRef: ObjectReference, fieldName: String, valueStr: String, vm: VirtualMachine): JsonElement {
        val refType = objectRef.referenceType()
        val field = refType.fieldByName(fieldName)
            ?: throw IllegalArgumentException("Field not found: $fieldName")

        // Check if field is final
        if (field.isFinal) {
            throw IllegalArgumentException("Cannot modify final field: $fieldName")
        }

        // Parse the value and create a JDI Value
        val newValue = parseValue(valueStr, field.type(), vm)

        // Set the value
        objectRef.setValue(field, newValue)

        Logger.debug("Set object field $fieldName to $valueStr")

        return buildSetVariableResponse(newValue)
    }

    /**
     * Set an array element
     */
    private fun setArrayElement(arrayRef: ArrayReference, indexStr: String, valueStr: String, vm: VirtualMachine): JsonElement {
        // Parse the index (format: "[N]")
        val index = indexStr.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .toIntOrNull()
            ?: throw IllegalArgumentException("Invalid array index: $indexStr")

        if (index < 0 || index >= arrayRef.length()) {
            throw IllegalArgumentException("Array index out of bounds: $index")
        }

        // Get the component type of the array
        val arrayType = arrayRef.type() as ArrayType
        val componentType = arrayType.componentType()

        // Parse the value
        val newValue = parseValue(valueStr, componentType, vm)

        // Set the value
        arrayRef.setValue(index, newValue)

        Logger.debug("Set array element [$index] to $valueStr")

        return buildSetVariableResponse(newValue)
    }

    /**
     * Parse a string value into a JDI Value based on the target type
     */
    private fun parseValue(valueStr: String, targetType: Type, vm: VirtualMachine): Value? {
        val trimmed = valueStr.trim()

        // Handle null
        if (trimmed == "null") {
            if (targetType is PrimitiveType) {
                throw IllegalArgumentException("Cannot assign null to primitive type ${targetType.name()}")
            }
            return null
        }

        return when (targetType) {
            is BooleanType -> vm.mirrorOf(parseBoolean(trimmed))
            is ByteType -> vm.mirrorOf(parseByte(trimmed))
            is CharType -> vm.mirrorOf(parseChar(trimmed))
            is ShortType -> vm.mirrorOf(parseShort(trimmed))
            is IntegerType -> vm.mirrorOf(parseInt(trimmed))
            is LongType -> vm.mirrorOf(parseLong(trimmed))
            is FloatType -> vm.mirrorOf(parseFloat(trimmed))
            is DoubleType -> vm.mirrorOf(parseDouble(trimmed))
            is ReferenceType -> parseReferenceValue(trimmed, targetType, vm)
            else -> throw IllegalArgumentException("Unsupported type: ${targetType.name()}")
        }
    }

    private fun parseBoolean(value: String): Boolean {
        return when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean value: $value")
        }
    }

    private fun parseByte(value: String): Byte {
        return try {
            value.toByte()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid byte value: $value")
        }
    }

    private fun parseChar(value: String): Char {
        // Handle character literals like 'a' or single characters
        val unquoted = if (value.startsWith("'") && value.endsWith("'") && value.length >= 2) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

        // Handle escape sequences
        return when {
            unquoted.isEmpty() -> throw IllegalArgumentException("Empty character value")
            unquoted.length == 1 -> unquoted[0]
            unquoted.startsWith("\\") -> parseEscapeSequence(unquoted)
            else -> throw IllegalArgumentException("Invalid character value: $value")
        }
    }

    private fun parseEscapeSequence(escape: String): Char {
        return when (escape) {
            "\\n" -> '\n'
            "\\t" -> '\t'
            "\\r" -> '\r'
            "\\b" -> '\b'
            "\\\\" -> '\\'
            "\\'" -> '\''
            "\\\"" -> '"'
            else -> {
                // Handle unicode escape like \u0041
                if (escape.startsWith("\\u") && escape.length == 6) {
                    try {
                        escape.substring(2).toInt(16).toChar()
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Invalid unicode escape: $escape")
                    }
                } else {
                    throw IllegalArgumentException("Invalid escape sequence: $escape")
                }
            }
        }
    }

    private fun parseShort(value: String): Short {
        return try {
            value.toShort()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid short value: $value")
        }
    }

    private fun parseInt(value: String): Int {
        return try {
            // Handle hex (0x), octal (0), and binary (0b) prefixes
            when {
                value.startsWith("0x", ignoreCase = true) -> value.substring(2).toInt(16)
                value.startsWith("0b", ignoreCase = true) -> value.substring(2).toInt(2)
                value.startsWith("0") && value.length > 1 && value.all { it.isDigit() } -> value.toInt(8)
                else -> value.toInt()
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid int value: $value")
        }
    }

    private fun parseLong(value: String): Long {
        return try {
            val cleanValue = value.removeSuffix("L").removeSuffix("l")
            when {
                cleanValue.startsWith("0x", ignoreCase = true) -> cleanValue.substring(2).toLong(16)
                cleanValue.startsWith("0b", ignoreCase = true) -> cleanValue.substring(2).toLong(2)
                cleanValue.startsWith("0") && cleanValue.length > 1 && cleanValue.all { it.isDigit() } -> cleanValue.toLong(8)
                else -> cleanValue.toLong()
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid long value: $value")
        }
    }

    private fun parseFloat(value: String): Float {
        return try {
            value.removeSuffix("f").removeSuffix("F").toFloat()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid float value: $value")
        }
    }

    private fun parseDouble(value: String): Double {
        return try {
            value.removeSuffix("d").removeSuffix("D").toDouble()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid double value: $value")
        }
    }

    /**
     * Parse a reference type value (currently supports only String literals)
     */
    private fun parseReferenceValue(value: String, targetType: ReferenceType, vm: VirtualMachine): Value? {
        // Handle string literals
        if (targetType.name() == "java.lang.String") {
            val unquoted = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                // Parse string with escape sequences
                parseStringLiteral(value.substring(1, value.length - 1))
            } else {
                // Allow unquoted strings for convenience
                value
            }
            return vm.mirrorOf(unquoted)
        }

        // For other reference types, we cannot easily create new objects
        throw IllegalArgumentException("Cannot create new objects of type ${targetType.name()}. Only primitive values and strings are supported.")
    }

    /**
     * Parse a string literal, handling escape sequences
     */
    private fun parseStringLiteral(value: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    'n' -> { result.append('\n'); i += 2 }
                    't' -> { result.append('\t'); i += 2 }
                    'r' -> { result.append('\r'); i += 2 }
                    'b' -> { result.append('\b'); i += 2 }
                    '\\' -> { result.append('\\'); i += 2 }
                    '"' -> { result.append('"'); i += 2 }
                    '\'' -> { result.append('\''); i += 2 }
                    'u' -> {
                        // Unicode escape
                        if (i + 5 < value.length) {
                            try {
                                val code = value.substring(i + 2, i + 6).toInt(16)
                                result.append(code.toChar())
                                i += 6
                            } catch (e: NumberFormatException) {
                                result.append(value[i])
                                i++
                            }
                        } else {
                            result.append(value[i])
                            i++
                        }
                    }
                    else -> {
                        result.append(value[i])
                        i++
                    }
                }
            } else {
                result.append(value[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * Build the response for a setVariable request
     */
    private fun buildSetVariableResponse(value: Value?): JsonElement {
        val displayValue = formatValue(value)
        val typeName = value?.type()?.name() ?: "null"
        val variablesReference = createVariableReference(value)

        return buildJsonObject {
            put("value", displayValue)
            put("type", typeName)
            put("variablesReference", variablesReference)
        }
    }

    /**
     * Format a value for display
     */
    private fun formatValue(value: Value?): String {
        if (value == null) return "null"

        return when (value) {
            is StringReference -> {
                try {
                    "\"${value.value()}\""
                } catch (e: Exception) {
                    "\"<unavailable>\""
                }
            }
            is ArrayReference -> "Array[${value.length()}]"
            is ObjectReference -> {
                try {
                    value.toString()
                } catch (e: Exception) {
                    "instance of ${value.type().name()}"
                }
            }
            else -> value.toString()
        }
    }

    /**
     * Create a variable reference for structured values
     */
    private fun createVariableReference(value: Value?): Int {
        if (value == null) return 0

        return when (value) {
            is ArrayReference -> {
                if (value.length() > 0) {
                    server.variableReferenceManager.createArrayElementsReference(value, 0, -1)
                } else {
                    0
                }
            }
            is ObjectReference -> {
                val typeName = value.referenceType().name()
                // Don't expand String
                if (typeName == "java.lang.String") {
                    0
                } else if (value.referenceType().allFields().isNotEmpty()) {
                    server.variableReferenceManager.createObjectFieldsReference(value)
                } else {
                    0
                }
            }
            else -> 0
        }
    }
}
