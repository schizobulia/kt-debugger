package com.kotlindebugger.cli

import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.jline.reader.Candidate

/**
 * 调试器命令自动补全器
 */
class DebugCompleter : Completer {

    private val commands = setOf(
        // 会话管理
        "run", "r", "attach", "quit", "q", "exit", "help", "h", "?",

        // 断点管理
        "break", "b", "delete", "d", "list", "l", "enable", "disable",

        // 执行控制
        "continue", "c", "step", "s", "next", "n", "finish", "f", "interrupt",

        // 栈帧操作
        "backtrace", "bt", "where", "frame", "fr", "up", "down",

        // 变量查看
        "locals", "info locals", "print", "p",

        // 线程操作
        "threads", "thread", "t",

        // 信息查看
        "status"
    )

    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        val word = line.word()

        // 如果当前词为空，显示所有命令
        if (word.isEmpty()) {
            commands.sorted().forEach { cmd ->
                candidates.add(Candidate(cmd, cmd, null, null, null, null, true))
            }
            return
        }

        // 查找以当前词开头的命令
        commands.filter { it.startsWith(word) }
               .sorted()
               .forEach { cmd ->
                   candidates.add(Candidate(cmd, cmd, null, null, null, null, true))
               }
    }
}