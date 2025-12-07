package com.kotlindemo.debugger.inline

import com.sun.jdi.ClassType
import com.sun.jdi.Location
import com.sun.jdi.Method
import java.io.BufferedReader
import java.io.StringReader
import java.util.regex.Pattern

/**
 * SMAP (Source Map) 解析器
 * 用于解析Kotlin编译器生成的调试信息
 */
class SMAPParser {

    private val SECTION_PATTERN = Pattern.compile("\\*\\* (.*) \\*\\*")
    private val FILE_PATTERN = Pattern.compile("\\*F (?:L )?(.*)")
    private val LINE_PATTERN = Pattern.compile("(\\d+)(?:#(\\d+))?(?:,(\\d+))?")
    private val STRATUM_PATTERN = Pattern.compile("\\*S (.*)")

    /**
     * 解析类文件的SMAP信息
     */
    fun parseSMAP(classType: ClassType): Map<String, SourceMapInfo> {
        val smapInfo = mutableMapOf<String, SourceMapInfo>()

        // 从调试属性中读取SMAP信息
        classType.allMethods().forEach { method ->
            val smapData = extractSMAPFromMethod(method)
            if (smapData != null) {
                val sourceMap = parseSMAPData(smapData)
                smapInfo[method.name()] = sourceMap
            }
        }

        return smapInfo
    }

    /**
     * 从方法中提取SMAP信息
     */
    private fun extractSMAPFromMethod(method: Method): String? {
        // SMAP信息通常存储在方法的调试属性中
        // 这里需要根据实际的字节码格式来解析
        return method.variables().firstOrNull()?.name()?.let { name ->
            if (name.contains("smap")) {
                // 实际实现需要从字节码的SourceDebugExtension属性中读取
                extractSourceDebugExtension(method)
            } else null
        }
    }

    /**
     * 从字節码的SourceDebugExtension属性中提取SMAP数据
     */
    private fun extractSourceDebugExtension(method: Method): String? {
        // 这里需要使用字节码操作库（如ASM）来读取SourceDebugExtension属性
        // 暂时返回示例数据
        return """
            *S Kotlin
            *F
            + 1 Test.kt
            Test.kt
            *L
            1#1,2:1
            3#1,2:3
            *E
        """.trimIndent()
    }

    /**
     * 解析SMAP数据
     */
    private fun parseSMAPData(smapData: String): SourceMapInfo {
        val reader = BufferedReader(StringReader(smapData))
        var currentStratum = "Kotlin"
        val lineMappings = mutableListOf<LineMapping>()
        var fileId = "1"
        var path = ""

        reader.useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("*S ") -> {
                        currentStratum = line.substring(3).trim()
                    }
                    line.startsWith("*F") -> {
                        // 文件信息处理
                    }
                    line.startsWith("*L") -> {
                        // 行号映射处理
                    }
                    line.matches(Regex("\\d+.*")) -> {
                        parseLineMapping(line)?.let { mapping ->
                            lineMappings.add(mapping)
                        }
                    }
                }
            }
        }

        return SourceMapInfo(
            stratum = currentStratum,
            fileId = fileId,
            path = path,
            lineMappings = lineMappings
        )
    }

    /**
     * 解析单行映射
     */
    private fun parseLineMapping(line: String): LineMapping? {
        val parts = line.split(",")
        if (parts.isEmpty()) return null

        // 解析输入行号
        val inputParts = parts[0].split("#")
        val inputStartLine = inputParts[0].toIntOrNull() ?: return null
        val fileId = if (inputParts.size > 1) inputParts[1] else "1"

        // 解析输出范围
        if (parts.size < 2) return null

        val outputRange = parts[1].split(":")
        if (outputRange.size != 2) return null

        val outputStartLine = outputRange[0].toIntOrNull() ?: return null
        val outputLineCount = outputRange[1].toIntOrNull() ?: 1

        return LineMapping(
            inputStartLine = inputStartLine,
            inputLineCount = 1,
            outputStartLine = outputStartLine,
            outputLineCount = outputLineCount
        )
    }

    /**
     * 将字节码位置映射到源码位置
     */
    fun mapBytecodeToSource(
        location: Location,
        sourceMapInfo: SourceMapInfo?
    ): SourceLocation? {
        if (sourceMapInfo == null) return null

        val lineNumber = location.lineNumber()
        val sourceName = location.sourceName()

        // 查找对应的源码行号
        val sourceMapping = sourceMapInfo.lineMappings.find { mapping ->
            lineNumber >= mapping.outputStartLine &&
            lineNumber < mapping.outputStartLine + mapping.outputLineCount
        }

        return sourceMapping?.let { mapping ->
            val sourceLine = mapping.inputStartLine + (lineNumber - mapping.outputStartLine)
            SourceLocation(
                sourcePath = sourceMapInfo.path,
                sourceName = sourceName ?: "",
                lineNumber = sourceLine
            )
        }
    }

    /**
     * 将源码位置映射到字节码位置
     */
    fun mapSourceToBytecode(
        sourceLocation: SourceLocation,
        sourceMapInfo: SourceMapInfo?,
        method: Method
    ): List<Location> {
        if (sourceMapInfo == null) return emptyList()

        // 查找对应的字节码位置
        val mappings = sourceMapInfo.lineMappings.filter { mapping ->
            sourceLocation.lineNumber >= mapping.inputStartLine &&
            sourceLocation.lineNumber < mapping.inputStartLine + mapping.inputLineCount
        }

        return mappings.flatMap { mapping ->
            val offset = sourceLocation.lineNumber - mapping.inputStartLine
            val bytecodeLine = mapping.outputStartLine + offset

            // 在方法中查找对应的字节码位置
            method.allLineLocations().filter { location ->
                location.lineNumber() == bytecodeLine
            }
        }
    }
}