package com.kotlindebugger.cli.output

/**
 * ANSI 颜色代码
 */
object AnsiColors {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"
    const val DIM = "\u001B[2m"
    const val UNDERLINE = "\u001B[4m"

    const val BLACK = "\u001B[30m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val MAGENTA = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"

    const val BG_BLACK = "\u001B[40m"
    const val BG_RED = "\u001B[41m"
    const val BG_GREEN = "\u001B[42m"
    const val BG_YELLOW = "\u001B[43m"
    const val BG_BLUE = "\u001B[44m"
}

/**
 * 输出格式化器
 */
class OutputFormatter(private val colorEnabled: Boolean = true) {

    fun color(text: String, vararg codes: String): String {
        return if (colorEnabled) {
            "${codes.joinToString("")}$text${AnsiColors.RESET}"
        } else {
            text
        }
    }

    fun bold(text: String) = color(text, AnsiColors.BOLD)
    fun dim(text: String) = color(text, AnsiColors.DIM)
    fun red(text: String) = color(text, AnsiColors.RED)
    fun green(text: String) = color(text, AnsiColors.GREEN)
    fun yellow(text: String) = color(text, AnsiColors.YELLOW)
    fun blue(text: String) = color(text, AnsiColors.BLUE)
    fun cyan(text: String) = color(text, AnsiColors.CYAN)
    fun magenta(text: String) = color(text, AnsiColors.MAGENTA)

    fun success(text: String) = green(text)
    fun error(text: String) = red(text)
    fun warning(text: String) = yellow(text)
    fun info(text: String) = cyan(text)

    fun header(text: String) = bold(cyan(text))
    fun prompt(text: String) = bold(green(text))

    /**
     * 高亮断点命中
     */
    fun highlightBreakpoint(file: String, line: Int): String {
        return box(
            "BREAKPOINT HIT",
            "${cyan(file)}:${yellow(line.toString())}"
        )
    }

    /**
     * 高亮单步执行
     */
    fun highlightStep(location: String): String {
        return info("➜ Stepped to ${bold(location)}")
    }

    /**
     * 高亮提示符
     */
    fun highlightPrompt(): String = bold(green("(kdb) "))

    /**
     * 显示智能提示
     */
    fun hint(text: String): String {
        return dim("💡 Hint: $text")
    }

    /**
     * 格式化表格
     */
    fun table(headers: List<String>, rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""

        // 计算每列宽度
        val columnWidths = headers.indices.map { col ->
            maxOf(
                headers[col].length,
                rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            )
        }

        val sb = StringBuilder()

        // 打印表头
        val headerLine = headers.mapIndexed { i, h ->
            h.padEnd(columnWidths[i])
        }.joinToString("  ")
        sb.appendLine(bold(headerLine))

        // 打印分隔线
        sb.appendLine(columnWidths.joinToString("  ") { "-".repeat(it) })

        // 打印数据行
        rows.forEach { row ->
            val line = row.mapIndexed { i, cell ->
                cell.padEnd(columnWidths.getOrElse(i) { cell.length })
            }.joinToString("  ")
            sb.appendLine(line)
        }

        return sb.toString()
    }

    /**
     * 格式化代码块
     */
    fun codeBlock(lines: List<Pair<Int, String>>, highlightLine: Int? = null): String {
        val sb = StringBuilder()
        val maxLineNum = lines.maxOfOrNull { it.first } ?: 0
        val lineNumWidth = maxLineNum.toString().length

        lines.forEach { (lineNum, content) ->
            val lineNumStr = lineNum.toString().padStart(lineNumWidth)
            val prefix = if (lineNum == highlightLine) {
                color("→ ", AnsiColors.GREEN, AnsiColors.BOLD)
            } else {
                "  "
            }

            val formattedLine = if (lineNum == highlightLine) {
                color("$lineNumStr│ $content", AnsiColors.YELLOW)
            } else {
                dim("$lineNumStr│ ") + content
            }

            sb.appendLine("$prefix$formattedLine")
        }

        return sb.toString()
    }

    /**
     * 格式化框
     */
    fun box(title: String, content: String): String {
        val lines = content.lines()
        val maxWidth = maxOf(title.length, lines.maxOfOrNull { it.length } ?: 0)
        val width = maxWidth + 4

        val sb = StringBuilder()
        sb.appendLine("┌─ ${bold(title)} ${"─".repeat(maxOf(0, width - title.length - 4))}┐")
        lines.forEach { line ->
            sb.appendLine("│ ${line.padEnd(width - 2)} │")
        }
        sb.appendLine("└${"─".repeat(width)}┘")

        return sb.toString()
    }
}
