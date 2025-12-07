package com.kotlindebugger.kotlin.position

import com.kotlindebugger.common.model.SourcePosition
import com.kotlindebugger.common.util.JdiUtils.safeLineNumber
import com.kotlindebugger.common.util.JdiUtils.safeSourceName
import com.kotlindebugger.common.util.JdiUtils.safeLocationsOfLine
import com.kotlindebugger.kotlin.smap.SMAP
import com.kotlindebugger.kotlin.smap.SMAPCache
import com.sun.jdi.*

/**
 * Kotlin 位置管理器
 * 负责将字节码位置映射到 Kotlin 源代码位置
 * 处理内联函数、Lambda 等 Kotlin 特有的代码结构
 */
class KotlinPositionManager(
    private val vm: VirtualMachine,
    private val smapCache: SMAPCache = SMAPCache()
) {

    /**
     * 将 JDI Location 转换为源代码位置
     * 考虑 SMAP 映射（用于内联函数）
     */
    fun getSourcePosition(location: Location): SourcePosition? {
        val lineNumber = location.safeLineNumber()
        if (lineNumber < 0) return null

        val refType = location.declaringType()
        val sourceName = location.safeSourceName()

        // 尝试从 SMAP 获取更准确的位置
        val smap = getSMAP(refType)
        if (smap != null) {
            val mappedPosition = smap.findSourcePosition(lineNumber)
            if (mappedPosition != null) {
                return SourcePosition(
                    file = mappedPosition.sourceFile,
                    line = mappedPosition.sourceLine
                )
            }
        }

        // 没有 SMAP 或未找到映射，使用原始位置
        return if (sourceName != null) {
            SourcePosition(sourceName, lineNumber)
        } else {
            null
        }
    }

    /**
     * 获取内联函数的所有源位置
     * 当一个位置可能对应多个内联调用时使用
     */
    fun getInlinedSourcePositions(location: Location): List<SourcePosition> {
        val lineNumber = location.safeLineNumber()
        if (lineNumber < 0) return emptyList()

        val refType = location.declaringType()
        val smap = getSMAP(refType) ?: return emptyList()

        val positions = mutableListOf<SourcePosition>()

        // 查找所有映射到此行的源位置
        for (fileMapping in smap.fileMappings) {
            for (rangeMapping in fileMapping.lineMappings) {
                if (rangeMapping.containsDest(lineNumber)) {
                    val sourceLine = rangeMapping.mapDestToSource(lineNumber)
                    positions.add(SourcePosition(fileMapping.name, sourceLine))
                }
            }
        }

        return positions.distinctBy { "${it.file}:${it.line}" }
    }

    /**
     * 根据源位置查找所有匹配的类
     */
    fun findClassesForSource(sourceFile: String): List<ReferenceType> {
        return vm.allClasses().filter { refType ->
            try {
                val sourceName = refType.sourceName()
                sourceName == sourceFile ||
                        sourceName.endsWith("/$sourceFile") ||
                        sourceFile.endsWith(sourceName)
            } catch (e: AbsentInformationException) {
                false
            }
        }
    }

    /**
     * 根据源位置查找所有匹配的字节码位置
     */
    fun findLocations(sourceFile: String, line: Int): List<Location> {
        val locations = mutableListOf<Location>()

        for (refType in findClassesForSource(sourceFile)) {
            // 直接在类中查找行
            locations.addAll(refType.safeLocationsOfLine(line))

            // 查找 SMAP 映射的位置（用于内联函数）
            val smap = getSMAP(refType)
            if (smap != null) {
                val destLines = smap.findDestLines(sourceFile, line)
                for (destLine in destLines) {
                    locations.addAll(refType.safeLocationsOfLine(destLine))
                }
            }
        }

        return locations.distinctBy { "${it.declaringType().name()}:${it.safeLineNumber()}" }
    }

    /**
     * 检查位置是否在内联函数中
     */
    fun isInInlineFunction(location: Location): Boolean {
        val refType = location.declaringType()
        val smap = getSMAP(refType) ?: return false

        val lineNumber = location.safeLineNumber()
        if (lineNumber < 0) return false

        // 如果 SMAP 中有此行的映射，说明在内联函数中
        return smap.findSourcePosition(lineNumber) != null
    }

    /**
     * 检查是否是 Kotlin 生成的类
     */
    fun isKotlinClass(refType: ReferenceType): Boolean {
        val name = refType.name()
        return name.endsWith("Kt") ||
                name.contains("\$") ||
                getSMAP(refType) != null
    }

    /**
     * 检查是否是 Lambda 类
     */
    fun isLambdaClass(refType: ReferenceType): Boolean {
        val name = refType.name()
        return name.contains("\$\$Lambda\$") ||
                name.contains("\$lambda\$") ||
                name.matches(Regex(".*\\$\\d+\$.*"))
    }

    /**
     * 获取类的 SMAP
     */
    fun getSMAP(refType: ReferenceType): SMAP? {
        val className = refType.name()

        // 先检查缓存
        smapCache.get(className)?.let { return it }

        // 从类中提取 SMAP
        val smapString = SMAPCache.extractFromReferenceType(refType)
        return smapCache.getOrParse(className, smapString)
    }

    /**
     * 获取格式化的位置字符串
     */
    fun formatLocation(location: Location): String {
        val sourcePos = getSourcePosition(location)
        return if (sourcePos != null) {
            val inlineMarker = if (isInInlineFunction(location)) " [inline]" else ""
            "${sourcePos.file}:${sourcePos.line}$inlineMarker"
        } else {
            val method = location.method()
            "${location.declaringType().name()}.${method.name()}()"
        }
    }

    /**
     * 清除 SMAP 缓存
     */
    fun clearCache() {
        smapCache.clear()
    }
}
