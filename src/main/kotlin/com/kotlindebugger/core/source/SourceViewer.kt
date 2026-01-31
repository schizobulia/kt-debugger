package com.kotlindebugger.core.source

import com.kotlindebugger.common.model.SourcePosition
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 断点查询接口
 * 用于检查指定文件和行是否有断点
 */
fun interface BreakpointChecker {
    /**
     * 检查指定文件和行号是否设置了断点
     * @param fileName 源文件名
     * @param line 行号
     * @return 如果该行有断点返回 true，否则返回 false
     */
    fun hasBreakpointAt(fileName: String, line: Int): Boolean
}

/**
 * 源代码查看器
 * 负责读取和格式化源代码显示
 */
class SourceViewer(
    private val sourceRoots: List<Path> = emptyList(),
    private val breakpointChecker: BreakpointChecker? = null
) {

    /**
     * 获取指定位置周围的源代码
     */
    fun getSourceCode(position: SourcePosition, contextLines: Int = 5): SourceCodeView? {
        val sourcePath = findSourceFile(position.file) ?: return null

        try {
            val lines = Files.readAllLines(sourcePath)
            if (position.line > lines.size) {
                return null
            }

            val startLine = maxOf(1, position.line - contextLines)
            val endLine = minOf(lines.size, position.line + contextLines)

            val codeLines = (startLine..endLine).map { lineNum ->
                CodeLine(
                    lineNumber = lineNum,
                    content = lines[lineNum - 1],
                    isCurrentLine = lineNum == position.line,
                    isBreakpoint = breakpointChecker?.hasBreakpointAt(position.file, lineNum) ?: false
                )
            }

            return SourceCodeView(
                filePath = sourcePath.toString(),
                currentPosition = position,
                lines = codeLines,
                totalLines = lines.size
            )

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 获取整个文件内容
     */
    fun getWholeFile(fileName: String): SourceCodeView? {
        val sourcePath = findSourceFile(fileName) ?: return null

        try {
            val lines = Files.readAllLines(sourcePath)
            val codeLines = lines.mapIndexed { index, content ->
                CodeLine(
                    lineNumber = index + 1,
                    content = content,
                    isCurrentLine = false,
                    isBreakpoint = false
                )
            }

            return SourceCodeView(
                filePath = sourcePath.toString(),
                currentPosition = null,
                lines = codeLines,
                totalLines = lines.size
            )

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 查找源文件
     */
    private fun findSourceFile(fileName: String): Path? {
        // 如果是绝对路径，直接使用
        val absolutePath = Paths.get(fileName)
        if (absolutePath.isAbsolute && Files.exists(absolutePath)) {
            return absolutePath
        }

        // 在源代码根目录中搜索
        for (root in sourceRoots) {
            val candidatePath = root.resolve(fileName)
            if (Files.exists(candidatePath)) {
                return candidatePath
            }

            // 尝试递归搜索（在合理范围内）
            try {
                val found = Files.walk(root)
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(fileName) }
                    .findFirst()
                if (found.isPresent) {
                    return found.get()
                }
            } catch (e: Exception) {
                // 忽略搜索错误
            }
        }

        // 尝试在当前工作目录查找
        val currentDir = Paths.get("")
        val candidatePath = currentDir.resolve(fileName)
        if (Files.exists(candidatePath)) {
            return candidatePath
        }

        return null
    }

    /**
     * 添加源代码根目录
     */
    fun addSourceRoot(path: Path) {
        if (path !in sourceRoots) {
            sourceRoots.plus(path)
        }
    }

    /**
     * 搜索包含特定文本的文件
     */
    fun searchInSource(searchText: String, maxResults: Int = 20): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        for (root in sourceRoots) {
            try {
                val filePaths: List<Path> = Files.walk(root)
                    .use { stream ->
                        stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                            .limit(maxResults.toLong())
                            .toList()
                    }

                filePaths.forEach { filePath: Path ->
                    try {
                        val lines = Files.readAllLines(filePath)
                        lines.forEachIndexed { lineIndex, lineContent ->
                            if (lineContent.contains(searchText, ignoreCase = true)) {
                                results.add(
                                    SearchResult(
                                        filePath = filePath.toString(),
                                        lineNumber = lineIndex + 1,
                                        lineContent = lineContent.trim(),
                                        matchedText = searchText
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略读取错误的文件
                    }
                }
            } catch (e: Exception) {
                // 忽略搜索错误的目录
            }
        }

        return results.take(maxResults)
    }

    /**
     * 源代码视图
     */
    data class SourceCodeView(
        val filePath: String,
        val currentPosition: SourcePosition?,
        val lines: List<CodeLine>,
        val totalLines: Int
    )

    /**
     * 代码行
     */
    data class CodeLine(
        val lineNumber: Int,
        val content: String,
        val isCurrentLine: Boolean,
        val isBreakpoint: Boolean
    )

    /**
     * 搜索结果
     */
    data class SearchResult(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String,
        val matchedText: String
    )

    companion object {
        const val DEFAULT_CONTEXT_LINES = 5
    }
}