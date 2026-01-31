package com.kotlindebugger.dap.handler

/**
 * 表达式解析器
 * 
 * 实现完整的表达式求值，参考 IntelliJ IDEA 和 Microsoft java-debug 的实现:
 * - KotlinExpressionParser: Kotlin表达式语法解析
 * - 支持运算符优先级
 * - 支持各种表达式类型
 * - 方法重载解析（按参数类型匹配）
 * 
 * 支持的表达式类型:
 * - 字面量: 数字、字符串、布尔值、null
 * - 字符串模板: "$variable" 和 "${expression}"
 * - 变量访问: identifier, this
 * - 成员访问: obj.field, obj?.field
 * - 属性访问器: 支持getter方法 (getXxx, isXxx)
 * - 数组访问: array[index]
 * - 方法调用: obj.method(args)，支持方法重载解析
 * - 算术运算: +, -, *, /, %
 * - 范围表达式: 1..10, 1 until 10, 10 downTo 1, step
 * - 包含检查: in, !in
 * - 比较运算: ==, !=, <, >, <=, >=
 * - 逻辑运算: &&, ||, !
 * - 位运算: and, or, xor, inv, shl, shr, ushr
 * - 类型检查: is, !is, as, as?
 * - 条件表达式: if-else
 * - Elvis运算符: ?:
 * - Lambda表达式: { params -> body }
 * - 展开运算符: *array
 * - 对象创建: ClassName(args), new ClassName(args)
 */

/**
 * 词法单元类型
 */
enum class TokenType {
    // 字面量
    INTEGER, LONG, FLOAT, DOUBLE, STRING, CHAR, BOOLEAN, NULL,
    
    // 字符串模板
    STRING_TEMPLATE, // 包含 $variable 或 ${expression} 的字符串
    
    // 标识符和关键字
    IDENTIFIER, THIS, IF, ELSE, IS, AS, NEW,
    
    // 算术运算符
    PLUS, MINUS, STAR, SLASH, PERCENT,
    
    // 范围运算符
    RANGE, UNTIL, DOWNTO, STEP,
    
    // 比较运算符
    EQ, NE, LT, GT, LE, GE,
    IN, NOT_IN,  // in 和 !in 运算符
    
    // 逻辑运算符
    AND, OR, NOT,
    
    // 位运算符
    BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, SHL, SHR, USHR,
    
    // 分隔符
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,
    DOT, COMMA, COLON, QUESTION, SAFE_DOT, ELVIS,
    SPREAD, // * for spread operator
    ARROW, // -> for lambda
    
    // 特殊
    EOF
}

/**
 * 字符串模板部分
 */
sealed class StringTemplatePart {
    data class Literal(val text: String) : StringTemplatePart()
    data class Variable(val name: String) : StringTemplatePart()
    data class Expression(val expression: String) : StringTemplatePart()
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
        "ushr" to TokenType.USHR,
        "in" to TokenType.IN,
        "until" to TokenType.UNTIL,
        "downTo" to TokenType.DOWNTO,
        "step" to TokenType.STEP
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
        var hasTemplates = false
        val templateParts = mutableListOf<StringTemplatePart>()
        var currentStringPart = StringBuilder()
        
        while (pos < length && input[pos] != quote) {
            if (input[pos] == '\\' && pos + 1 < length) {
                pos++
                currentStringPart.append(when (input[pos]) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    '\\' -> '\\'
                    '"' -> '"'
                    '\'' -> '\''
                    '$' -> '$'
                    else -> input[pos]
                })
                pos++
            } else if (input[pos] == '$' && pos + 1 < length) {
                hasTemplates = true
                // Save current string part if not empty
                if (currentStringPart.isNotEmpty()) {
                    templateParts.add(StringTemplatePart.Literal(currentStringPart.toString()))
                    currentStringPart = StringBuilder()
                }
                pos++ // skip $
                
                if (input[pos] == '{') {
                    // ${expression} form
                    pos++ // skip {
                    val exprStart = pos
                    var braceCount = 1
                    while (pos < length && braceCount > 0) {
                        when (input[pos]) {
                            '{' -> braceCount++
                            '}' -> braceCount--
                        }
                        if (braceCount > 0) pos++
                    }
                    val expression = input.substring(exprStart, pos)
                    templateParts.add(StringTemplatePart.Expression(expression))
                    if (pos < length && input[pos] == '}') {
                        pos++ // skip }
                    }
                } else if (input[pos].isLetterOrDigit() || input[pos] == '_') {
                    // $variable form - scan identifier
                    val identStart = pos
                    while (pos < length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                        pos++
                    }
                    val identifier = input.substring(identStart, pos)
                    templateParts.add(StringTemplatePart.Variable(identifier))
                } else {
                    // Standalone $ - treat as literal
                    currentStringPart.append('$')
                }
            } else {
                currentStringPart.append(input[pos])
                pos++
            }
        }
        
        if (pos >= length) {
            throw EvaluationException("Unterminated string literal starting at position $startPos")
        }
        
        pos++ // skip closing quote
        
        // If we found any templates, return STRING_TEMPLATE token
        if (hasTemplates) {
            if (currentStringPart.isNotEmpty()) {
                templateParts.add(StringTemplatePart.Literal(currentStringPart.toString()))
            }
            return Token(TokenType.STRING_TEMPLATE, templateParts, startPos)
        }
        
        // Otherwise, return simple STRING token
        return Token(TokenType.STRING, currentStringPart.toString(), startPos)
    }
    
    private fun scanChar(): Token {
        val startPos = pos
        pos++ // skip opening quote
        
        if (pos >= length) {
            throw EvaluationException("Unterminated character literal starting at position $startPos")
        }
        
        val ch = if (input[pos] == '\\' && pos + 1 < length) {
            pos++
            when (input[pos]) {
                'n' -> '\n'
                't' -> '\t'
                'r' -> '\r'
                '\\' -> '\\'
                '\'' -> '\''
                else -> input[pos]
            }
        } else {
            input[pos]
        }
        pos++
        
        if (pos >= length || input[pos] != '\'') {
            throw EvaluationException("Unterminated character literal starting at position $startPos")
        }
        
        pos++ // skip closing quote
        return Token(TokenType.CHAR, ch, startPos)
    }
    
    private fun scanOperatorOrPunctuation(): Token {
        val startPos = pos
        val char = input[pos]
        
        return when (char) {
            '+' -> { pos++; Token(TokenType.PLUS, "+", startPos) }
            '-' -> {
                when {
                    pos + 1 < length && input[pos + 1] == '>' -> {
                        pos += 2
                        Token(TokenType.ARROW, "->", startPos)
                    }
                    else -> { pos++; Token(TokenType.MINUS, "-", startPos) }
                }
            }
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
                when {
                    pos + 1 < length && input[pos + 1] == '.' -> {
                        pos += 2
                        Token(TokenType.RANGE, "..", startPos)
                    }
                    else -> {
                        pos++
                        Token(TokenType.DOT, ".", startPos)
                    }
                }
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
                    pos + 3 <= length && input.substring(pos, pos + 3) == "!is" -> {
                        pos += 3
                        Token(TokenType.IS, "!is", startPos)
                    }
                    pos + 3 <= length && input.substring(pos, pos + 3) == "!in" -> {
                        pos += 3
                        Token(TokenType.NOT_IN, "!in", startPos)
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
 * @param receiver The object on which the method is called, or null for local method calls
 * @param methodName The name of the method
 * @param arguments The list of argument expressions
 * @param safe If true, this is a safe call (?.) that returns null if receiver is null
 */
data class MethodCallNode(val receiver: ExprNode?, val methodName: String, val arguments: List<ExprNode>, val safe: Boolean = false) : ExprNode()

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
 * 字符串模板节点
 * 用于表示包含 $variable 或 ${expression} 的字符串
 */
data class StringTemplateNode(val parts: List<StringTemplatePart>) : ExprNode()

/**
 * 范围表达式节点 (1..10, 1 until 10, 10 downTo 1)
 * @param start 起始值
 * @param end 结束值
 * @param operator 范围运算符类型 (RANGE, UNTIL, DOWNTO)
 * @param step 步长表达式（可选）
 */
data class RangeNode(
    val start: ExprNode,
    val end: ExprNode,
    val operator: TokenType,
    val step: ExprNode? = null
) : ExprNode()

/**
 * 包含检查节点 (in, !in)
 * @param element 要检查的元素
 * @param collection 集合或范围
 * @param negated 是否为 !in
 */
data class ContainsNode(
    val element: ExprNode,
    val collection: ExprNode,
    val negated: Boolean = false
) : ExprNode()

/**
 * Lambda表达式节点
 * @param parameters 参数列表
 * @param body Lambda体表达式
 */
data class LambdaNode(
    val parameters: List<String>,
    val body: ExprNode
) : ExprNode()

/**
 * 展开运算符节点 (*array)
 * @param expression 要展开的数组/集合表达式
 */
data class SpreadNode(val expression: ExprNode) : ExprNode()

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
        var left = parseContains()
        
        while (check(TokenType.LT) || check(TokenType.GT) || 
               check(TokenType.LE) || check(TokenType.GE) ||
               check(TokenType.IS)) {
            val op = advance()
            if (op.type == TokenType.IS) {
                val negated = op.value == "!is"
                val typeName = parseTypeName()
                left = TypeCheckNode(left, typeName, negated)
            } else {
                val right = parseContains()
                left = BinaryNode(left, op.type, right)
            }
        }
        
        return left
    }
    
    private fun parseContains(): ExprNode {
        var left = parseRange()
        
        while (check(TokenType.IN) || check(TokenType.NOT_IN)) {
            val negated = current().type == TokenType.NOT_IN
            advance()
            val right = parseRange()
            left = ContainsNode(left, right, negated)
        }
        
        return left
    }
    
    private fun parseRange(): ExprNode {
        var left = parseShift()
        
        while (check(TokenType.RANGE) || check(TokenType.UNTIL) || check(TokenType.DOWNTO)) {
            val op = advance().type
            val right = parseShift()
            
            // Check for step
            val step = if (match(TokenType.STEP)) {
                parseShift()
            } else {
                null
            }
            
            left = RangeNode(left, right, op, step)
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
                        MethodCallNode(expr, name, args, safe = false)
                    } else {
                        MemberAccessNode(expr, name, safe = false)
                    }
                }
                match(TokenType.SAFE_DOT) -> {
                    val name = expectIdentifier()
                    if (check(TokenType.LPAREN)) {
                        val args = parseArguments()
                        MethodCallNode(expr, name, args, safe = true)
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
            check(TokenType.STRING_TEMPLATE) -> {
                val token = advance()
                @Suppress("UNCHECKED_CAST")
                StringTemplateNode(token.value as List<StringTemplatePart>)
            }
            check(TokenType.THIS) -> {
                advance()
                ThisNode
            }
            check(TokenType.IF) -> parseIfExpression()
            check(TokenType.NEW) -> parseNewObject()
            check(TokenType.STAR) -> {
                // Spread operator: *array
                advance()
                SpreadNode(parseUnary())
            }
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
            check(TokenType.LBRACE) -> {
                // Lambda expression: { params -> body } or { body }
                parseLambdaExpression()
            }
            else -> throw EvaluationException("Unexpected token: ${current()}")
        }
    }
    
    private fun parseLambdaExpression(): ExprNode {
        expect(TokenType.LBRACE)
        
        // Check if there's a parameter list followed by ->
        val parameters = mutableListOf<String>()
        val savedPos = pos
        
        // Try to parse parameter list
        try {
            if (check(TokenType.IDENTIFIER)) {
                parameters.add(advance().value as String)
                while (match(TokenType.COMMA)) {
                    if (check(TokenType.IDENTIFIER)) {
                        parameters.add(advance().value as String)
                    } else {
                        throw EvaluationException("Expected parameter name")
                    }
                }
                
                if (!match(TokenType.ARROW)) {
                    // Not a lambda with parameters, restore position
                    pos = savedPos
                    parameters.clear()
                }
            }
        } catch (e: Exception) {
            // Reset and try as a simple lambda without parameters
            pos = savedPos
            parameters.clear()
        }
        
        // If no explicit parameters and ARROW not found, use implicit 'it'
        if (parameters.isEmpty() && !check(TokenType.RBRACE)) {
            // Check for arrow without parameters: { -> body }
            if (match(TokenType.ARROW)) {
                // No parameters explicitly declared
            }
        }
        
        // Parse body (or handle empty lambda)
        val body = if (check(TokenType.RBRACE)) {
            // Empty lambda body - treat as null
            LiteralNode(null, TokenType.NULL)
        } else {
            parseExpression()
        }
        
        expect(TokenType.RBRACE)
        
        return LambdaNode(parameters, body)
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
