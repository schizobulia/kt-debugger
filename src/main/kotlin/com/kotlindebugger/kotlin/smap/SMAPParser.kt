package com.kotlindebugger.kotlin.smap

/**
 * SMAP 解析器
 * 解析 SourceDebugExtension 属性中的 SMAP 信息
 */
object SMAPParser {

    private const val SMAP_HEADER = "SMAP"
    private const val STRATUM_SECTION = "*S"
    private const val FILE_SECTION = "*F"
    private const val LINE_SECTION = "*L"
    private const val END_SECTION = "*E"

    private const val KOTLIN_STRATUM = "Kotlin"
    private const val KOTLIN_DEBUG_STRATUM = "KotlinDebug"

    /**
     * 解析 SMAP 字符串
     */
    fun parse(smapString: String): SMAP? {
        if (smapString.isBlank()) return null

        val lines = smapString.lines().map { it.trim() }
        if (lines.isEmpty() || lines[0] != SMAP_HEADER) return null

        return try {
            parseInternal(lines)
        } catch (e: Exception) {
            System.err.println("Failed to parse SMAP: ${e.message}")
            null
        }
    }

    private fun parseInternal(lines: List<String>): SMAP? {
        var index = 0

        // 跳过 SMAP 头
        if (lines[index] != SMAP_HEADER) return null
        index++

        // 输出文件名
        val outputFileName = lines.getOrNull(index++) ?: return null

        // 默认分层
        val defaultStratum = lines.getOrNull(index++) ?: return null

        // 查找 Kotlin 分层
        val kotlinStratumStart = findStratumStart(lines, KOTLIN_STRATUM)
        val kotlinDebugStratumStart = findStratumStart(lines, KOTLIN_DEBUG_STRATUM)

        // 优先使用 KotlinDebug 分层（包含更详细的调用栈信息）
        val stratumStart = kotlinDebugStratumStart ?: kotlinStratumStart ?: return null

        // 解析文件部分和行映射部分
        val fileMappings = parseStratum(lines, stratumStart)

        return SMAP(
            outputFileName = outputFileName,
            defaultStratum = defaultStratum,
            fileMappings = fileMappings
        )
    }

    /**
     * 查找指定分层的起始位置
     */
    private fun findStratumStart(lines: List<String>, stratumName: String): Int? {
        for (i in lines.indices) {
            if (lines[i] == "$STRATUM_SECTION $stratumName") {
                return i
            }
        }
        return null
    }

    /**
     * 解析一个分层
     */
    private fun parseStratum(lines: List<String>, startIndex: Int): List<FileMapping> {
        var index = startIndex + 1 // 跳过 *S 行

        // 查找文件部分
        while (index < lines.size && lines[index] != FILE_SECTION) {
            index++
        }
        if (index >= lines.size) return emptyList()
        index++ // 跳过 *F

        // 解析文件列表
        val fileMap = mutableMapOf<Int, FileMapping>()
        while (index < lines.size && !lines[index].startsWith("*")) {
            val line = lines[index]
            if (line.startsWith("+") || line.startsWith("")) {
                val fileMapping = parseFileEntry(line, lines.getOrNull(index + 1))
                if (fileMapping != null) {
                    fileMap[fileMapping.id] = fileMapping
                    // 如果有路径行，跳过它
                    if (!line.startsWith("+") || (index + 1 < lines.size && !lines[index + 1].startsWith("*") && !lines[index + 1].startsWith("+"))) {
                        index++
                    }
                }
            }
            index++
        }

        // 查找行映射部分
        while (index < lines.size && lines[index] != LINE_SECTION) {
            index++
        }
        if (index >= lines.size) return fileMap.values.toList()
        index++ // 跳过 *L

        // 解析行映射
        while (index < lines.size && lines[index] != END_SECTION && !lines[index].startsWith("*")) {
            val line = lines[index]
            if (line.isNotBlank()) {
                parseLineMapping(line, fileMap)
            }
            index++
        }

        return fileMap.values.toList()
    }

    /**
     * 解析文件条目
     * 格式: + <file-id> <file-name> 或 <file-id> <file-name>
     * 下一行可能是路径
     */
    private fun parseFileEntry(line: String, nextLine: String?): FileMapping? {
        val trimmed = line.removePrefix("+").trim()
        val parts = trimmed.split(" ", limit = 2)
        if (parts.size < 2) return null

        val id = parts[0].toIntOrNull() ?: return null
        val name = parts[1]

        // 检查是否有路径行
        val path = if (line.startsWith("+") && nextLine != null && !nextLine.startsWith("*") && !nextLine.startsWith("+") && !nextLine.contains("#")) {
            nextLine.trim()
        } else {
            name
        }

        return FileMapping(id, name, path)
    }

    /**
     * 解析行映射
     * 格式: <source-line>#<file-id>,<repeat>:<dest-line>,<dest-increment>
     * 或: <source-line>#<file-id>:<dest-line>
     * 或: <source-line>:<dest-line>
     */
    private fun parseLineMapping(line: String, fileMap: MutableMap<Int, FileMapping>) {
        try {
            // 分割 : 左右两部分
            val colonIndex = line.indexOf(':')
            if (colonIndex < 0) return

            val leftPart = line.substring(0, colonIndex)
            val rightPart = line.substring(colonIndex + 1)

            // 解析左侧（源信息）
            val sourceInfo = parseSourceInfo(leftPart)

            // 解析右侧（目标信息）
            val destInfo = parseDestInfo(rightPart)

            // 获取文件映射
            val fileMapping = fileMap[sourceInfo.fileId] ?: return

            // 创建范围映射
            val range = sourceInfo.repeat
            val mapping = RangeMapping(
                sourceStart = sourceInfo.line,
                destStart = destInfo.line,
                range = range,
                parent = fileMapping
            )

            fileMapping.addLineMapping(mapping)

        } catch (e: Exception) {
            // 忽略解析错误的行
        }
    }

    /**
     * 解析源信息
     */
    private fun parseSourceInfo(part: String): SourceInfo {
        // 格式: <line>#<file-id>,<repeat> 或 <line>#<file-id> 或 <line>
        val hashIndex = part.indexOf('#')

        if (hashIndex < 0) {
            // 没有 # 号，使用默认文件 ID 1
            val commaIndex = part.indexOf(',')
            return if (commaIndex >= 0) {
                val line = part.substring(0, commaIndex).toInt()
                val repeat = part.substring(commaIndex + 1).toInt()
                SourceInfo(line, 1, repeat)
            } else {
                SourceInfo(part.toInt(), 1, 1)
            }
        }

        val line = part.substring(0, hashIndex).toInt()
        val afterHash = part.substring(hashIndex + 1)

        val commaIndex = afterHash.indexOf(',')
        return if (commaIndex >= 0) {
            val fileId = afterHash.substring(0, commaIndex).toInt()
            val repeat = afterHash.substring(commaIndex + 1).toInt()
            SourceInfo(line, fileId, repeat)
        } else {
            SourceInfo(line, afterHash.toInt(), 1)
        }
    }

    /**
     * 解析目标信息
     */
    private fun parseDestInfo(part: String): DestInfo {
        // 格式: <line>,<increment> 或 <line>
        val commaIndex = part.indexOf(',')
        return if (commaIndex >= 0) {
            val line = part.substring(0, commaIndex).toInt()
            val increment = part.substring(commaIndex + 1).toInt()
            DestInfo(line, increment)
        } else {
            DestInfo(part.toInt(), 1)
        }
    }

    private data class SourceInfo(val line: Int, val fileId: Int, val repeat: Int)
    private data class DestInfo(val line: Int, val increment: Int)
}
