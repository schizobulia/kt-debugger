package com.kotlindebugger.dap.handler

/**
 * 表达式解析器
 * 
 * 实现完整的表达式求值，参考 IntelliJ IDEA 的实现:
 * - KotlinExpressionParser: Kotlin表达式语法解析
 * - 支持运算符优先级
 * - 支持各种表达式类型
 * 
 * 支持的表达式类型:
 * - 字面量: 数字、字符串、布尔值、null
 * - 变量访问: identifier, this
 * - 成员访问: obj.field
 * - 数组访问: array[index]
 * - 方法调用: obj.method(args)
 * - 算术运算: +, -, *, /, %
 * - 比较运算: ==, !=, <, >, <=, >=
 * - 逻辑运算: &&, ||, !
 * - 位运算: and, or, xor, inv, shl, shr, ushr
 * - 类型检查: is, !is, as, as?
 * - 条件表达式: if-else
 * - 对象创建: ClassName(args)
 */

/**
 * 词法单元类型
 */
enum class TokenType {
    // 字面量
    INTEGER, LONG, FLOAT, DOUBLE, STRING, CHAR, BOOLEAN, NULL,
    
    // 标识符和关键字
    IDENTIFIER, THIS, IF, ELSE, IS, AS, NEW,
    
    // 算术运算符
    PLUS, MINUS, STAR, SLASH, PERCENT,
    
    // 比较运算符
    EQ, NE, LT, GT, LE, GE,
    
    // 逻辑运算符
    AND, OR, NOT,
    
    // 位运算符
    BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, SHL, SHR, USHR,
    
    // 分隔符
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,
    DOT, COMMA, COLON, QUESTION, SAFE_DOT, ELVIS,
    
    // 特殊
    EOF
}

/**
 * 词法单元
 */
data class Token(
    val type: TokenType,
    val value: Any?,
    val position: Int
) {
    override fun toString(): String = "Token($type, $value)"
}

/**
 * 词法分析器
 * 
 * 将表达式字符串转换为词法单元序列
 */
class Lexer(private val input: String) {
    private var pos = 0
    private val length = input.length
    
    private val keywords = mapOf(
        "null" to TokenType.NULL,
        "true" to TokenType.BOOLEAN,
        "false" to TokenType.BOOLEAN,
        "this" to TokenType.THIS,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
        "is" to TokenType.IS,
        "as" to TokenType.AS,
        "new" to TokenType.NEW,
        "and" to TokenType.BIT_AND,
        "or" to TokenType.BIT_OR,
        "xor" to TokenType.BIT_XOR,
        "inv" to TokenType.BIT_NOT,
        "shl" to TokenType.SHL,
        "shr" to TokenType.SHR,
        "ushr" to TokenType.USHR
    )
    
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < length) {
            skipWhitespace()
            if (pos >= length) break
            
            val token = nextToken()
            tokens.add(token)
        }
        tokens.add(Token(TokenType.EOF, null, pos))
        return tokens
    }
    
    private fun skipWhitespace() {
        while (pos < length && input[pos].isWhitespace()) {
            pos++
        }
    }
    
    private fun nextToken(): Token {
        val char = input[pos]
        
        return when {
            char.isDigit() -> scanNumber()
            char.isLetter() || char == '_' || char == '$' -> scanIdentifierOrKeyword()
            char == '"' -> scanString('"')
            char == '\'' -> scanChar()
            else -> scanOperatorOrPunctuation()
        }
    }
    
    private fun scanNumber(): Token {
        val startPos = pos
        val sb = StringBuilder()
        
        // 整数部分
        while (pos < length && (input[pos].isDigit() || input[pos] == '_')) {
            if (input[pos] != '_') {
                sb.append(input[pos])
            }
            pos++
        }
        
        // 检查后缀
        if (pos < length) {
            when (input[pos].lowercaseChar()) {
                'l' -> {
                    pos++
                    return Token(TokenType.LONG, sb.toString().toLong(), startPos)
                }
                'f' -> {
                    pos++
                    return Token(TokenType.FLOAT, sb.toString().toFloat(), startPos)
                }
                'd' -> {
                    pos++
                    return Token(TokenType.DOUBLE, sb.toString().toDouble(), startPos)
                }
            }
        }
        
        // 小数部分
        if (pos < length && input[pos] == '.') {
            // 检查是否是成员访问而不是小数点
            if (pos + 1 < length && input[pos + 1].isDigit()) {
                sb.append('.')
                pos++
                while (pos < length && (input[pos].isDigit() || input[pos] == '_')) {
                    if (input[pos] != '_') {
                        sb.append(input[pos])
                    }
                    pos++
                }
                
                // 检查后缀
                if (pos < length && (input[pos].lowercaseChar() == 'f')) {
                    pos++
                    return Token(TokenType.FLOAT, sb.toString().toFloat(), startPos)
                }
                if (pos < length && (input[pos].lowercaseChar() == 'd')) {
                    pos++
                }
                return Token(TokenType.DOUBLE, sb.toString().toDouble(), startPos)
            }
        }
        
        // 科学计数法
        if (pos < length && (input[pos] == 'e' || input[pos] == 'E')) {
            sb.append(input[pos])
            pos++
            if (pos < length && (input[pos] == '+' || input[pos] == '-')) {
                sb.append(input[pos])
                pos++
            }
            while (pos < length && input[pos].isDigit()) {
                sb.append(input[pos])
                pos++
            }
            return Token(TokenType.DOUBLE, sb.toString().toDouble(), startPos)
        }
        
        val value = sb.toString().toLongOrNull()
        return if (value != null && value > Int.MAX_VALUE) {
            Token(TokenType.LONG, value, startPos)
        } else {
            Token(TokenType.INTEGER, sb.toString().toInt(), startPos)
        }
    }
    
    private fun scanIdentifierOrKeyword(): Token {
        val startPos = pos
        val sb = StringBuilder()
        
        while (pos < length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '$')) {
            sb.append(input[pos])
            pos++
        }
        
        val text = sb.toString()
        val keywordType = keywords[text]
        
        return when {
            keywordType == TokenType.BOOLEAN -> Token(TokenType.BOOLEAN, text == "true", startPos)
            keywordType == TokenType.NULL -> Token(TokenType.NULL, null, startPos)
            keywordType != null -> Token(keywordType, text, startPos)
            else -> Token(TokenType.IDENTIFIER, text, startPos)
        }
    }
    
    private fun scanString(quote: Char): Token {
        val startPos = pos
        pos++ // skip opening quote
        val sb = StringBuilder()
        
        while (pos < length && input[pos] != quote) {
            if (input[pos] == '\\' && pos + 1 < length) {
                pos++
                sb.append(when (input[pos]) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    '\\' -> '\\'
                    '"' -> '"'
                    '\'' -> '\''
                    else -> input[pos]
                })
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        
        if (pos < length) {
            pos++ // skip closing quote
        }
        
        return Token(TokenType.STRING, sb.toString(), startPos)
    }
    
    private fun scanChar(): Token {
        val startPos = pos
        pos++ // skip opening quote
        
        val ch = if (pos < length && input[pos] == '\\' && pos + 1 < length) {
            pos++
            when (input[pos]) {
                'n' -> '\n'
                't' -> '\t'
                'r' -> '\r'
                '\\' -> '\\'
                '\'' -> '\''
                else -> input[pos]
            }
        } else if (pos < length) {
            input[pos]
        } else {
            ' '
        }
        pos++
        
        if (pos < length && input[pos] == '\'') {
            pos++ // skip closing quote
        }
        
        return Token(TokenType.CHAR, ch, startPos)
    }
    
    private fun scanOperatorOrPunctuation(): Token {
        val startPos = pos
        val char = input[pos]
        
        return when (char) {
            '+' -> { pos++; Token(TokenType.PLUS, "+", startPos) }
            '-' -> { pos++; Token(TokenType.MINUS, "-", startPos) }
            '*' -> { pos++; Token(TokenType.STAR, "*", startPos) }
            '/' -> { pos++; Token(TokenType.SLASH, "/", startPos) }
            '%' -> { pos++; Token(TokenType.PERCENT, "%", startPos) }
            '(' -> { pos++; Token(TokenType.LPAREN, "(", startPos) }
            ')' -> { pos++; Token(TokenType.RPAREN, ")", startPos) }
            '[' -> { pos++; Token(TokenType.LBRACKET, "[", startPos) }
            ']' -> { pos++; Token(TokenType.RBRACKET, "]", startPos) }
            '{' -> { pos++; Token(TokenType.LBRACE, "{", startPos) }
            '}' -> { pos++; Token(TokenType.RBRACE, "}", startPos) }
            ',' -> { pos++; Token(TokenType.COMMA, ",", startPos) }
            ':' -> { pos++; Token(TokenType.COLON, ":", startPos) }
            '.' -> {
                pos++
                Token(TokenType.DOT, ".", startPos)
            }
            '?' -> {
                when {
                    pos + 1 < length && input[pos + 1] == '.' -> {
                        pos += 2
                        Token(TokenType.SAFE_DOT, "?.", startPos)
                    }
                    pos + 1 < length && input[pos + 1] == ':' -> {
                        pos += 2
                        Token(TokenType.ELVIS, "?:", startPos)
                    }
                    else -> {
                        pos++
                        Token(TokenType.QUESTION, "?", startPos)
                    }
                }
            }
            '=' -> {
                if (pos + 1 < length && input[pos + 1] == '=') {
                    pos += 2
                    Token(TokenType.EQ, "==", startPos)
                } else {
                    throw EvaluationException("Assignment is not supported in expression evaluation")
                }
            }
            '!' -> {
                when {
                    pos + 1 < length && input[pos + 1] == '=' -> {
                        pos += 2
                        Token(TokenType.NE, "!=", startPos)
                    }
                    pos + 2 < length && input.substring(pos, pos + 3) == "!is" -> {
                        pos += 3
                        Token(TokenType.IS, "!is", startPos)
                    }
                    else -> {
                        pos++
                        Token(TokenType.NOT, "!", startPos)
                    }
                }
            }
            '<' -> {
                if (pos + 1 < length && input[pos + 1] == '=') {
                    pos += 2
                    Token(TokenType.LE, "<=", startPos)
                } else {
                    pos++
                    Token(TokenType.LT, "<", startPos)
                }
            }
            '>' -> {
                if (pos + 1 < length && input[pos + 1] == '=') {
                    pos += 2
                    Token(TokenType.GE, ">=", startPos)
                } else {
                    pos++
                    Token(TokenType.GT, ">", startPos)
                }
            }
            '&' -> {
                if (pos + 1 < length && input[pos + 1] == '&') {
                    pos += 2
                    Token(TokenType.AND, "&&", startPos)
                } else {
                    pos++
                    Token(TokenType.BIT_AND, "&", startPos)
                }
            }
            '|' -> {
                if (pos + 1 < length && input[pos + 1] == '|') {
                    pos += 2
                    Token(TokenType.OR, "||", startPos)
                } else {
                    pos++
                    Token(TokenType.BIT_OR, "|", startPos)
                }
            }
            '^' -> { pos++; Token(TokenType.BIT_XOR, "^", startPos) }
            '~' -> { pos++; Token(TokenType.BIT_NOT, "~", startPos) }
            else -> throw EvaluationException("Unexpected character: '$char' at position $pos")
        }
    }
}

/**
 * 表达式AST节点基类
 */
sealed class ExprNode

/**
 * 字面量节点
 */
data class LiteralNode(val value: Any?, val type: TokenType) : ExprNode()

/**
 * 标识符节点
 */
data class IdentifierNode(val name: String) : ExprNode()

/**
 * this引用节点
 */
object ThisNode : ExprNode()

/**
 * 一元运算节点
 */
data class UnaryNode(val operator: TokenType, val operand: ExprNode) : ExprNode()

/**
 * 二元运算节点
 */
data class BinaryNode(val left: ExprNode, val operator: TokenType, val right: ExprNode) : ExprNode()

/**
 * 成员访问节点
 */
data class MemberAccessNode(val obj: ExprNode, val member: String, val safe: Boolean = false) : ExprNode()

/**
 * 数组访问节点
 */
data class ArrayAccessNode(val array: ExprNode, val index: ExprNode) : ExprNode()

/**
 * 方法调用节点
 */
data class MethodCallNode(val receiver: ExprNode?, val methodName: String, val arguments: List<ExprNode>) : ExprNode()

/**
 * 类型检查节点 (is, !is)
 */
data class TypeCheckNode(val expr: ExprNode, val typeName: String, val negated: Boolean) : ExprNode()

/**
 * 类型转换节点 (as, as?)
 */
data class TypeCastNode(val expr: ExprNode, val typeName: String, val safe: Boolean) : ExprNode()

/**
 * 条件表达式节点 (if-else)
 */
data class ConditionalNode(val condition: ExprNode, val thenExpr: ExprNode, val elseExpr: ExprNode) : ExprNode()

/**
 * 对象创建节点
 */
data class NewObjectNode(val typeName: String, val arguments: List<ExprNode>) : ExprNode()

/**
 * Elvis运算符节点 (?:)
 */
data class ElvisNode(val left: ExprNode, val right: ExprNode) : ExprNode()

/**
 * 表达式解析器
 * 
 * 使用递归下降解析，支持完整的运算符优先级
 * 
 * 运算符优先级（从低到高）:
 * 1. || (逻辑或)
 * 2. && (逻辑与)
 * 3. | (位或)
 * 4. ^ (位异或)
 * 5. & (位与)
 * 6. ==, != (相等)
 * 7. <, >, <=, >=, is, !is (比较)
 * 8. .., shl, shr, ushr (范围和移位)
 * 9. +, - (加减)
 * 10. *, /, % (乘除)
 * 11. 一元运算符: -, !, ~
 * 12. 后缀运算符: ., ?., [], ()
 */
class ExpressionParser(private val tokens: List<Token>) {
    private var pos = 0
    
    fun parse(): ExprNode {
        val expr = parseExpression()
        if (!isAtEnd()) {
            throw EvaluationException("Unexpected token: ${current()}")
        }
        return expr
    }
    
    private fun parseExpression(): ExprNode {
        return parseElvis()
    }
    
    private fun parseElvis(): ExprNode {
        var left = parseOr()
        
        while (match(TokenType.ELVIS)) {
            val right = parseOr()
            left = ElvisNode(left, right)
        }
        
        return left
    }
    
    private fun parseOr(): ExprNode {
        var left = parseAnd()
        
        while (match(TokenType.OR)) {
            val right = parseAnd()
            left = BinaryNode(left, TokenType.OR, right)
        }
        
        return left
    }
    
    private fun parseAnd(): ExprNode {
        var left = parseBitOr()
        
        while (match(TokenType.AND)) {
            val right = parseBitOr()
            left = BinaryNode(left, TokenType.AND, right)
        }
        
        return left
    }
    
    private fun parseBitOr(): ExprNode {
        var left = parseBitXor()
        
        while (match(TokenType.BIT_OR)) {
            val right = parseBitXor()
            left = BinaryNode(left, TokenType.BIT_OR, right)
        }
        
        return left
    }
    
    private fun parseBitXor(): ExprNode {
        var left = parseBitAnd()
        
        while (match(TokenType.BIT_XOR)) {
            val right = parseBitAnd()
            left = BinaryNode(left, TokenType.BIT_XOR, right)
        }
        
        return left
    }
    
    private fun parseBitAnd(): ExprNode {
        var left = parseEquality()
        
        while (match(TokenType.BIT_AND)) {
            val right = parseEquality()
            left = BinaryNode(left, TokenType.BIT_AND, right)
        }
        
        return left
    }
    
    private fun parseEquality(): ExprNode {
        var left = parseComparison()
        
        while (check(TokenType.EQ) || check(TokenType.NE)) {
            val op = advance().type
            val right = parseComparison()
            left = BinaryNode(left, op, right)
        }
        
        return left
    }
    
    private fun parseComparison(): ExprNode {
        var left = parseShift()
        
        while (check(TokenType.LT) || check(TokenType.GT) || 
               check(TokenType.LE) || check(TokenType.GE) ||
               check(TokenType.IS)) {
            val op = advance()
            if (op.type == TokenType.IS) {
                val negated = op.value == "!is"
                val typeName = parseTypeName()
                left = TypeCheckNode(left, typeName, negated)
            } else {
                val right = parseShift()
                left = BinaryNode(left, op.type, right)
            }
        }
        
        return left
    }
    
    private fun parseShift(): ExprNode {
        var left = parseAdditive()
        
        while (check(TokenType.SHL) || check(TokenType.SHR) || check(TokenType.USHR)) {
            val op = advance().type
            val right = parseAdditive()
            left = BinaryNode(left, op, right)
        }
        
        return left
    }
    
    private fun parseAdditive(): ExprNode {
        var left = parseMultiplicative()
        
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            val op = advance().type
            val right = parseMultiplicative()
            left = BinaryNode(left, op, right)
        }
        
        return left
    }
    
    private fun parseMultiplicative(): ExprNode {
        var left = parseUnary()
        
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            val op = advance().type
            val right = parseUnary()
            left = BinaryNode(left, op, right)
        }
        
        return left
    }
    
    private fun parseUnary(): ExprNode {
        if (check(TokenType.MINUS) || check(TokenType.NOT) || check(TokenType.BIT_NOT) || check(TokenType.PLUS)) {
            val op = advance().type
            val operand = parseUnary()
            return UnaryNode(op, operand)
        }
        
        return parsePostfix()
    }
    
    private fun parsePostfix(): ExprNode {
        var expr = parsePrimary()
        
        while (true) {
            expr = when {
                match(TokenType.DOT) -> {
                    val name = expectIdentifier()
                    if (check(TokenType.LPAREN)) {
                        val args = parseArguments()
                        MethodCallNode(expr, name, args)
                    } else {
                        MemberAccessNode(expr, name, safe = false)
                    }
                }
                match(TokenType.SAFE_DOT) -> {
                    val name = expectIdentifier()
                    if (check(TokenType.LPAREN)) {
                        val args = parseArguments()
                        MethodCallNode(expr, name, args) // TODO: handle safe call for methods
                    } else {
                        MemberAccessNode(expr, name, safe = true)
                    }
                }
                match(TokenType.LBRACKET) -> {
                    val index = parseExpression()
                    expect(TokenType.RBRACKET)
                    ArrayAccessNode(expr, index)
                }
                check(TokenType.AS) -> {
                    advance()
                    val safe = match(TokenType.QUESTION)
                    val typeName = parseTypeName()
                    TypeCastNode(expr, typeName, safe)
                }
                else -> break
            }
        }
        
        return expr
    }
    
    private fun parsePrimary(): ExprNode {
        return when {
            check(TokenType.INTEGER) || check(TokenType.LONG) || 
            check(TokenType.FLOAT) || check(TokenType.DOUBLE) ||
            check(TokenType.STRING) || check(TokenType.CHAR) ||
            check(TokenType.BOOLEAN) || check(TokenType.NULL) -> {
                val token = advance()
                LiteralNode(token.value, token.type)
            }
            check(TokenType.THIS) -> {
                advance()
                ThisNode
            }
            check(TokenType.IF) -> parseIfExpression()
            check(TokenType.NEW) -> parseNewObject()
            check(TokenType.IDENTIFIER) -> {
                val name = advance().value as String
                when {
                    check(TokenType.LPAREN) -> {
                        // 方法调用或构造函数调用
                        val args = parseArguments()
                        if (name[0].isUpperCase()) {
                            // 可能是构造函数调用
                            NewObjectNode(name, args)
                        } else {
                            MethodCallNode(null, name, args)
                        }
                    }
                    else -> IdentifierNode(name)
                }
            }
            check(TokenType.LPAREN) -> {
                advance()
                val expr = parseExpression()
                expect(TokenType.RPAREN)
                expr
            }
            else -> throw EvaluationException("Unexpected token: ${current()}")
        }
    }
    
    private fun parseIfExpression(): ExprNode {
        expect(TokenType.IF)
        expect(TokenType.LPAREN)
        val condition = parseExpression()
        expect(TokenType.RPAREN)
        val thenExpr = parseExpression()
        expect(TokenType.ELSE)
        val elseExpr = parseExpression()
        return ConditionalNode(condition, thenExpr, elseExpr)
    }
    
    private fun parseNewObject(): ExprNode {
        expect(TokenType.NEW)
        val typeName = parseTypeName()
        val args = parseArguments()
        return NewObjectNode(typeName, args)
    }
    
    private fun parseArguments(): List<ExprNode> {
        expect(TokenType.LPAREN)
        val args = mutableListOf<ExprNode>()
        
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpression())
            while (match(TokenType.COMMA)) {
                args.add(parseExpression())
            }
        }
        
        expect(TokenType.RPAREN)
        return args
    }
    
    private fun parseTypeName(): String {
        val sb = StringBuilder()
        sb.append(expectIdentifier())
        
        while (match(TokenType.DOT)) {
            sb.append('.')
            sb.append(expectIdentifier())
        }
        
        // Handle generic types (simplified)
        if (check(TokenType.LT)) {
            advance()
            sb.append('<')
            sb.append(parseTypeName())
            while (match(TokenType.COMMA)) {
                sb.append(", ")
                sb.append(parseTypeName())
            }
            expect(TokenType.GT)
            sb.append('>')
        }
        
        // Handle array types
        while (check(TokenType.LBRACKET) && peek(1)?.type == TokenType.RBRACKET) {
            advance()
            advance()
            sb.append("[]")
        }
        
        // Handle nullable types
        if (match(TokenType.QUESTION)) {
            sb.append('?')
        }
        
        return sb.toString()
    }
    
    // Helper methods
    
    private fun current(): Token = tokens.getOrElse(pos) { tokens.last() }
    
    private fun check(type: TokenType): Boolean = !isAtEnd() && current().type == type
    
    private fun isAtEnd(): Boolean = current().type == TokenType.EOF
    
    private fun advance(): Token {
        if (!isAtEnd()) pos++
        return tokens[pos - 1]
    }
    
    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }
    
    private fun expect(type: TokenType): Token {
        if (check(type)) return advance()
        throw EvaluationException("Expected $type but got ${current().type}")
    }
    
    private fun expectIdentifier(): String {
        if (check(TokenType.IDENTIFIER)) {
            return advance().value as String
        }
        throw EvaluationException("Expected identifier but got ${current().type}")
    }
    
    private fun peek(offset: Int): Token? = tokens.getOrNull(pos + offset)
}

/**
 * 解析表达式字符串为AST
 */
fun parseExpression(expression: String): ExprNode {
    val lexer = Lexer(expression)
    val tokens = lexer.tokenize()
    val parser = ExpressionParser(tokens)
    return parser.parse()
}
