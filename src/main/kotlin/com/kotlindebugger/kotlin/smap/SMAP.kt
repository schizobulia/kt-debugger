package com.kotlindebugger.kotlin.smap

/**
 * SMAP (Source Map) 数据结构
 * 用于映射内联函数的字节码位置到源代码位置
 *
 * SMAP 格式参考 JSR-045:
 * SMAP
 * <output-filename>
 * <default-stratum>
 * *S <stratum-name>
 * *F
 * + <file-id> <file-name>
 * <file-path>
 * *L
 * <source-line>#<file-id>,<repeat-count>:<output-line>,<output-increment>
 * *E
 */
data class SMAP(
    val outputFileName: String,
    val defaultStratum: String,
    val fileMappings: List<FileMapping>
) {
    // 预计算的行映射索引，用于快速查找
    private val destLineIndex: List<RangeMapping> by lazy {
        fileMappings.flatMap { it.lineMappings }
            .sortedBy { it.destStart }
    }

    /**
     * 根据目标行号（字节码行号）查找源位置
     */
    fun findSourcePosition(destLine: Int): SourceMappingResult? {
        for (mapping in destLineIndex) {
            if (destLine in mapping.destStart until (mapping.destStart + mapping.range)) {
                val offset = destLine - mapping.destStart
                return SourceMappingResult(
                    sourceFile = mapping.parent.name,
                    sourcePath = mapping.parent.path,
                    sourceLine = mapping.sourceStart + offset
                )
            }
        }
        return null
    }

    /**
     * 根据源文件和行号查找所有对应的目标行号
     */
    fun findDestLines(sourceFile: String, sourceLine: Int): List<Int> {
        return fileMappings
            .filter { it.name == sourceFile || it.path.endsWith(sourceFile) }
            .flatMap { fileMapping ->
                fileMapping.lineMappings
                    .filter { mapping ->
                        sourceLine in mapping.sourceStart until (mapping.sourceStart + mapping.range)
                    }
                    .map { mapping ->
                        val offset = sourceLine - mapping.sourceStart
                        mapping.destStart + offset
                    }
            }
    }

    /**
     * 获取所有内联的源文件
     */
    fun getInlinedFiles(): List<String> {
        return fileMappings.map { it.name }.distinct()
    }

    override fun toString(): String {
        return buildString {
            appendLine("SMAP for $outputFileName (default stratum: $defaultStratum)")
            fileMappings.forEach { fileMapping ->
                appendLine("  File: ${fileMapping.name} (${fileMapping.path})")
                fileMapping.lineMappings.forEach { range ->
                    appendLine("    ${range.sourceStart}-${range.sourceStart + range.range - 1} -> ${range.destStart}-${range.destStart + range.range - 1}")
                }
            }
        }
    }
}

/**
 * 文件映射
 */
data class FileMapping(
    val id: Int,
    val name: String,
    val path: String
) {
    val lineMappings = mutableListOf<RangeMapping>()

    fun addLineMapping(mapping: RangeMapping) {
        lineMappings.add(mapping)
    }
}

/**
 * 行范围映射
 */
data class RangeMapping(
    val sourceStart: Int,      // 源文件起始行
    val destStart: Int,        // 目标文件（字节码）起始行
    val range: Int,            // 映射范围（行数）
    val parent: FileMapping    // 所属文件映射
) {
    /**
     * 检查目标行是否在此映射范围内
     */
    fun containsDest(destLine: Int): Boolean {
        return destLine in destStart until (destStart + range)
    }

    /**
     * 检查源行是否在此映射范围内
     */
    fun containsSource(sourceLine: Int): Boolean {
        return sourceLine in sourceStart until (sourceStart + range)
    }

    /**
     * 将目标行映射到源行
     */
    fun mapDestToSource(destLine: Int): Int {
        require(containsDest(destLine)) { "Line $destLine not in range" }
        return sourceStart + (destLine - destStart)
    }

    /**
     * 将源行映射到目标行
     */
    fun mapSourceToDest(sourceLine: Int): Int {
        require(containsSource(sourceLine)) { "Line $sourceLine not in range" }
        return destStart + (sourceLine - sourceStart)
    }
}

/**
 * 源位置映射结果
 */
data class SourceMappingResult(
    val sourceFile: String,
    val sourcePath: String,
    val sourceLine: Int
)
