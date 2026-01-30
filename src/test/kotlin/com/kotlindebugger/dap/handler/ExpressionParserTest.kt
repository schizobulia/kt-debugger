package com.kotlindebugger.dap.handler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

/**
 * 表达式解析器单元测试
 * 
 * 测试词法分析器和表达式解析器的基本功能
 */
class ExpressionParserTest {

    @Nested
    inner class LexerTests {
        
        @Test
        fun `test tokenize integer literal`() {
            val lexer = Lexer("42")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.INTEGER, tokens[0].type)
            assertEquals(42, tokens[0].value)
            assertEquals(TokenType.EOF, tokens[1].type)
        }
        
        @Test
        fun `test tokenize long literal`() {
            val lexer = Lexer("42L")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.LONG, tokens[0].type)
            assertEquals(42L, tokens[0].value)
        }
        
        @Test
        fun `test tokenize float literal`() {
            val lexer = Lexer("3.14f")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.FLOAT, tokens[0].type)
            assertEquals(3.14f, tokens[0].value)
        }
        
        @Test
        fun `test tokenize double literal`() {
            val lexer = Lexer("3.14")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.DOUBLE, tokens[0].type)
            assertEquals(3.14, tokens[0].value)
        }
        
        @Test
        fun `test tokenize string literal`() {
            val lexer = Lexer("\"hello world\"")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("hello world", tokens[0].value)
        }
        
        @Test
        fun `test tokenize char literal`() {
            val lexer = Lexer("'a'")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.CHAR, tokens[0].type)
            assertEquals('a', tokens[0].value)
        }
        
        @Test
        fun `test tokenize boolean literals`() {
            val lexerTrue = Lexer("true")
            val tokensTrue = lexerTrue.tokenize()
            assertEquals(TokenType.BOOLEAN, tokensTrue[0].type)
            assertEquals(true, tokensTrue[0].value)
            
            val lexerFalse = Lexer("false")
            val tokensFalse = lexerFalse.tokenize()
            assertEquals(TokenType.BOOLEAN, tokensFalse[0].type)
            assertEquals(false, tokensFalse[0].value)
        }
        
        @Test
        fun `test tokenize null literal`() {
            val lexer = Lexer("null")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.NULL, tokens[0].type)
        }
        
        @Test
        fun `test tokenize identifier`() {
            val lexer = Lexer("variableName")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("variableName", tokens[0].value)
        }
        
        @Test
        fun `test tokenize arithmetic operators`() {
            val lexer = Lexer("+ - * / %")
            val tokens = lexer.tokenize()
            
            assertEquals(6, tokens.size)
            assertEquals(TokenType.PLUS, tokens[0].type)
            assertEquals(TokenType.MINUS, tokens[1].type)
            assertEquals(TokenType.STAR, tokens[2].type)
            assertEquals(TokenType.SLASH, tokens[3].type)
            assertEquals(TokenType.PERCENT, tokens[4].type)
        }
        
        @Test
        fun `test tokenize comparison operators`() {
            val lexer = Lexer("== != < > <= >=")
            val tokens = lexer.tokenize()
            
            assertEquals(7, tokens.size)
            assertEquals(TokenType.EQ, tokens[0].type)
            assertEquals(TokenType.NE, tokens[1].type)
            assertEquals(TokenType.LT, tokens[2].type)
            assertEquals(TokenType.GT, tokens[3].type)
            assertEquals(TokenType.LE, tokens[4].type)
            assertEquals(TokenType.GE, tokens[5].type)
        }
        
        @Test
        fun `test tokenize logical operators`() {
            val lexer = Lexer("&& || !")
            val tokens = lexer.tokenize()
            
            assertEquals(4, tokens.size)
            assertEquals(TokenType.AND, tokens[0].type)
            assertEquals(TokenType.OR, tokens[1].type)
            assertEquals(TokenType.NOT, tokens[2].type)
        }
        
        @Test
        fun `test tokenize punctuation`() {
            val lexer = Lexer("( ) [ ] { } . ,")
            val tokens = lexer.tokenize()
            
            assertEquals(9, tokens.size)
            assertEquals(TokenType.LPAREN, tokens[0].type)
            assertEquals(TokenType.RPAREN, tokens[1].type)
            assertEquals(TokenType.LBRACKET, tokens[2].type)
            assertEquals(TokenType.RBRACKET, tokens[3].type)
            assertEquals(TokenType.LBRACE, tokens[4].type)
            assertEquals(TokenType.RBRACE, tokens[5].type)
            assertEquals(TokenType.DOT, tokens[6].type)
            assertEquals(TokenType.COMMA, tokens[7].type)
        }
        
        @Test
        fun `test tokenize elvis operator`() {
            val lexer = Lexer("?:")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.ELVIS, tokens[0].type)
        }
        
        @Test
        fun `test tokenize safe call operator`() {
            val lexer = Lexer("?.")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.SAFE_DOT, tokens[0].type)
        }
        
        @Test
        fun `test tokenize complex expression`() {
            val lexer = Lexer("a + b * c")
            val tokens = lexer.tokenize()
            
            assertEquals(6, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("a", tokens[0].value)
            assertEquals(TokenType.PLUS, tokens[1].type)
            assertEquals(TokenType.IDENTIFIER, tokens[2].type)
            assertEquals("b", tokens[2].value)
            assertEquals(TokenType.STAR, tokens[3].type)
            assertEquals(TokenType.IDENTIFIER, tokens[4].type)
            assertEquals("c", tokens[4].value)
        }
        
        @Test
        fun `test tokenize string with escape sequences`() {
            val lexer = Lexer("\"hello\\nworld\"")
            val tokens = lexer.tokenize()
            
            assertEquals(2, tokens.size)
            assertEquals(TokenType.STRING, tokens[0].type)
            assertEquals("hello\nworld", tokens[0].value)
        }
        
        @Test
        fun `test tokenize bitwise operators`() {
            val lexer = Lexer("and or xor")
            val tokens = lexer.tokenize()
            
            assertEquals(4, tokens.size)
            assertEquals(TokenType.BIT_AND, tokens[0].type)
            assertEquals(TokenType.BIT_OR, tokens[1].type)
            assertEquals(TokenType.BIT_XOR, tokens[2].type)
        }
        
        @Test
        fun `test tokenize shift operators`() {
            val lexer = Lexer("shl shr ushr")
            val tokens = lexer.tokenize()
            
            assertEquals(4, tokens.size)
            assertEquals(TokenType.SHL, tokens[0].type)
            assertEquals(TokenType.SHR, tokens[1].type)
            assertEquals(TokenType.USHR, tokens[2].type)
        }
    }
    
    @Nested
    inner class ParserTests {
        
        @Test
        fun `test parse integer literal`() {
            val node = parseExpression("42")
            assertTrue(node is LiteralNode)
            assertEquals(42, (node as LiteralNode).value)
            assertEquals(TokenType.INTEGER, node.type)
        }
        
        @Test
        fun `test parse boolean literal`() {
            val trueNode = parseExpression("true")
            assertTrue(trueNode is LiteralNode)
            assertEquals(true, (trueNode as LiteralNode).value)
            
            val falseNode = parseExpression("false")
            assertTrue(falseNode is LiteralNode)
            assertEquals(false, (falseNode as LiteralNode).value)
        }
        
        @Test
        fun `test parse null literal`() {
            val node = parseExpression("null")
            assertTrue(node is LiteralNode)
            assertNull((node as LiteralNode).value)
        }
        
        @Test
        fun `test parse string literal`() {
            val node = parseExpression("\"hello\"")
            assertTrue(node is LiteralNode)
            assertEquals("hello", (node as LiteralNode).value)
        }
        
        @Test
        fun `test parse identifier`() {
            val node = parseExpression("variableName")
            assertTrue(node is IdentifierNode)
            assertEquals("variableName", (node as IdentifierNode).name)
        }
        
        @Test
        fun `test parse this`() {
            val node = parseExpression("this")
            assertTrue(node is ThisNode)
        }
        
        @Test
        fun `test parse unary minus`() {
            val node = parseExpression("-42")
            assertTrue(node is UnaryNode)
            val unaryNode = node as UnaryNode
            assertEquals(TokenType.MINUS, unaryNode.operator)
            assertTrue(unaryNode.operand is LiteralNode)
        }
        
        @Test
        fun `test parse logical not`() {
            val node = parseExpression("!flag")
            assertTrue(node is UnaryNode)
            val unaryNode = node as UnaryNode
            assertEquals(TokenType.NOT, unaryNode.operator)
            assertTrue(unaryNode.operand is IdentifierNode)
        }
        
        @Test
        fun `test parse addition`() {
            val node = parseExpression("a + b")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.PLUS, binaryNode.operator)
            assertTrue(binaryNode.left is IdentifierNode)
            assertTrue(binaryNode.right is IdentifierNode)
        }
        
        @Test
        fun `test parse subtraction`() {
            val node = parseExpression("a - b")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.MINUS, binaryNode.operator)
        }
        
        @Test
        fun `test parse multiplication`() {
            val node = parseExpression("a * b")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.STAR, binaryNode.operator)
        }
        
        @Test
        fun `test parse division`() {
            val node = parseExpression("a / b")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.SLASH, binaryNode.operator)
        }
        
        @Test
        fun `test parse modulo`() {
            val node = parseExpression("a % b")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.PERCENT, binaryNode.operator)
        }
        
        @Test
        fun `test parse operator precedence - multiplication before addition`() {
            val node = parseExpression("a + b * c")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.PLUS, binaryNode.operator)
            assertTrue(binaryNode.left is IdentifierNode)
            assertTrue(binaryNode.right is BinaryNode)
            assertEquals(TokenType.STAR, (binaryNode.right as BinaryNode).operator)
        }
        
        @Test
        fun `test parse parentheses override precedence`() {
            val node = parseExpression("(a + b) * c")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.STAR, binaryNode.operator)
            assertTrue(binaryNode.left is BinaryNode)
            assertEquals(TokenType.PLUS, (binaryNode.left as BinaryNode).operator)
        }
        
        @Test
        fun `test parse comparison operators`() {
            val eqNode = parseExpression("a == b")
            assertTrue(eqNode is BinaryNode)
            assertEquals(TokenType.EQ, (eqNode as BinaryNode).operator)
            
            val neNode = parseExpression("a != b")
            assertTrue(neNode is BinaryNode)
            assertEquals(TokenType.NE, (neNode as BinaryNode).operator)
            
            val ltNode = parseExpression("a < b")
            assertTrue(ltNode is BinaryNode)
            assertEquals(TokenType.LT, (ltNode as BinaryNode).operator)
            
            val gtNode = parseExpression("a > b")
            assertTrue(gtNode is BinaryNode)
            assertEquals(TokenType.GT, (gtNode as BinaryNode).operator)
        }
        
        @Test
        fun `test parse logical operators`() {
            val andNode = parseExpression("a && b")
            assertTrue(andNode is BinaryNode)
            assertEquals(TokenType.AND, (andNode as BinaryNode).operator)
            
            val orNode = parseExpression("a || b")
            assertTrue(orNode is BinaryNode)
            assertEquals(TokenType.OR, (orNode as BinaryNode).operator)
        }
        
        @Test
        fun `test parse member access`() {
            val node = parseExpression("obj.field")
            assertTrue(node is MemberAccessNode)
            val memberNode = node as MemberAccessNode
            assertEquals("field", memberNode.member)
            assertFalse(memberNode.safe)
            assertTrue(memberNode.obj is IdentifierNode)
        }
        
        @Test
        fun `test parse safe member access`() {
            val node = parseExpression("obj?.field")
            assertTrue(node is MemberAccessNode)
            val memberNode = node as MemberAccessNode
            assertEquals("field", memberNode.member)
            assertTrue(memberNode.safe)
        }
        
        @Test
        fun `test parse chained member access`() {
            val node = parseExpression("a.b.c")
            assertTrue(node is MemberAccessNode)
            val memberNode = node as MemberAccessNode
            assertEquals("c", memberNode.member)
            assertTrue(memberNode.obj is MemberAccessNode)
            assertEquals("b", (memberNode.obj as MemberAccessNode).member)
        }
        
        @Test
        fun `test parse array access`() {
            val node = parseExpression("arr[0]")
            assertTrue(node is ArrayAccessNode)
            val arrayNode = node as ArrayAccessNode
            assertTrue(arrayNode.array is IdentifierNode)
            assertTrue(arrayNode.index is LiteralNode)
        }
        
        @Test
        fun `test parse array access with expression index`() {
            val node = parseExpression("arr[i + 1]")
            assertTrue(node is ArrayAccessNode)
            val arrayNode = node as ArrayAccessNode
            assertTrue(arrayNode.index is BinaryNode)
        }
        
        @Test
        fun `test parse method call without arguments`() {
            val node = parseExpression("method()")
            assertTrue(node is MethodCallNode)
            val methodNode = node as MethodCallNode
            assertEquals("method", methodNode.methodName)
            assertTrue(methodNode.arguments.isEmpty())
            assertNull(methodNode.receiver)
        }
        
        @Test
        fun `test parse method call with arguments`() {
            val node = parseExpression("method(a, b, c)")
            assertTrue(node is MethodCallNode)
            val methodNode = node as MethodCallNode
            assertEquals("method", methodNode.methodName)
            assertEquals(3, methodNode.arguments.size)
        }
        
        @Test
        fun `test parse method call on object`() {
            val node = parseExpression("obj.method()")
            assertTrue(node is MethodCallNode)
            val methodNode = node as MethodCallNode
            assertEquals("method", methodNode.methodName)
            assertTrue(methodNode.receiver is IdentifierNode)
        }
        
        @Test
        fun `test parse elvis operator`() {
            val node = parseExpression("a ?: b")
            assertTrue(node is ElvisNode)
            val elvisNode = node as ElvisNode
            assertTrue(elvisNode.left is IdentifierNode)
            assertTrue(elvisNode.right is IdentifierNode)
        }
        
        @Test
        fun `test parse type check (is)`() {
            val node = parseExpression("obj is String")
            assertTrue(node is TypeCheckNode)
            val typeCheckNode = node as TypeCheckNode
            assertFalse(typeCheckNode.negated)
            assertEquals("String", typeCheckNode.typeName)
        }
        
        @Test
        fun `test parse conditional expression`() {
            val node = parseExpression("if (a > b) a else b")
            assertTrue(node is ConditionalNode)
            val conditionalNode = node as ConditionalNode
            assertTrue(conditionalNode.condition is BinaryNode)
            assertTrue(conditionalNode.thenExpr is IdentifierNode)
            assertTrue(conditionalNode.elseExpr is IdentifierNode)
        }
        
        @Test
        fun `test parse new object`() {
            val node = parseExpression("new ArrayList()")
            assertTrue(node is NewObjectNode)
            val newNode = node as NewObjectNode
            assertEquals("ArrayList", newNode.typeName)
            assertTrue(newNode.arguments.isEmpty())
        }
        
        @Test
        fun `test parse new object with arguments`() {
            val node = parseExpression("new Point(10, 20)")
            assertTrue(node is NewObjectNode)
            val newNode = node as NewObjectNode
            assertEquals("Point", newNode.typeName)
            assertEquals(2, newNode.arguments.size)
        }
        
        @Test
        fun `test parse complex expression`() {
            val node = parseExpression("(a + b) * c - d / e")
            assertTrue(node is BinaryNode)
            val binaryNode = node as BinaryNode
            assertEquals(TokenType.MINUS, binaryNode.operator)
        }
        
        @Test
        fun `test parse bitwise and`() {
            val node = parseExpression("a and b")
            assertTrue(node is BinaryNode)
            assertEquals(TokenType.BIT_AND, (node as BinaryNode).operator)
        }
        
        @Test
        fun `test parse bitwise or`() {
            val node = parseExpression("a or b")
            assertTrue(node is BinaryNode)
            assertEquals(TokenType.BIT_OR, (node as BinaryNode).operator)
        }
        
        @Test
        fun `test parse bitwise xor`() {
            val node = parseExpression("a xor b")
            assertTrue(node is BinaryNode)
            assertEquals(TokenType.BIT_XOR, (node as BinaryNode).operator)
        }
        
        @Test
        fun `test parse shift left`() {
            val node = parseExpression("a shl 2")
            assertTrue(node is BinaryNode)
            assertEquals(TokenType.SHL, (node as BinaryNode).operator)
        }
        
        @Test
        fun `test parse shift right`() {
            val node = parseExpression("a shr 2")
            assertTrue(node is BinaryNode)
            assertEquals(TokenType.SHR, (node as BinaryNode).operator)
        }
        
        @Test
        fun `test parse unsigned shift right`() {
            val node = parseExpression("a ushr 2")
            assertTrue(node is BinaryNode)
            assertEquals(TokenType.USHR, (node as BinaryNode).operator)
        }
    }
    
    @Nested
    inner class ErrorHandlingTests {
        
        @Test
        fun `test empty expression throws error`() {
            val exception = assertThrows(EvaluationException::class.java) {
                parseExpression("")
            }
            assertNotNull(exception.message)
        }
        
        @Test
        fun `test invalid character throws error`() {
            val exception = assertThrows(EvaluationException::class.java) {
                parseExpression("@invalid")
            }
            assertNotNull(exception.message)
        }
        
        @Test
        fun `test assignment throws error`() {
            val exception = assertThrows(EvaluationException::class.java) {
                parseExpression("a = b")
            }
            assertTrue(exception.message!!.contains("Assignment"))
        }
        
        @Test
        fun `test unclosed parenthesis throws error`() {
            val exception = assertThrows(EvaluationException::class.java) {
                parseExpression("(a + b")
            }
            assertNotNull(exception.message)
        }
    }
    
    @Nested
    inner class ASTNodeTests {
        
        @Test
        fun `test LiteralNode data class`() {
            val node = LiteralNode(42, TokenType.INTEGER)
            assertEquals(42, node.value)
            assertEquals(TokenType.INTEGER, node.type)
        }
        
        @Test
        fun `test IdentifierNode data class`() {
            val node = IdentifierNode("name")
            assertEquals("name", node.name)
        }
        
        @Test
        fun `test ThisNode is singleton`() {
            val node1 = ThisNode
            val node2 = ThisNode
            assertSame(node1, node2)
        }
        
        @Test
        fun `test UnaryNode data class`() {
            val operand = LiteralNode(42, TokenType.INTEGER)
            val node = UnaryNode(TokenType.MINUS, operand)
            assertEquals(TokenType.MINUS, node.operator)
            assertSame(operand, node.operand)
        }
        
        @Test
        fun `test BinaryNode data class`() {
            val left = IdentifierNode("a")
            val right = IdentifierNode("b")
            val node = BinaryNode(left, TokenType.PLUS, right)
            assertSame(left, node.left)
            assertSame(right, node.right)
            assertEquals(TokenType.PLUS, node.operator)
        }
        
        @Test
        fun `test MemberAccessNode data class`() {
            val obj = IdentifierNode("obj")
            val node = MemberAccessNode(obj, "field", safe = true)
            assertSame(obj, node.obj)
            assertEquals("field", node.member)
            assertTrue(node.safe)
        }
        
        @Test
        fun `test ArrayAccessNode data class`() {
            val array = IdentifierNode("arr")
            val index = LiteralNode(0, TokenType.INTEGER)
            val node = ArrayAccessNode(array, index)
            assertSame(array, node.array)
            assertSame(index, node.index)
        }
        
        @Test
        fun `test MethodCallNode data class`() {
            val receiver = IdentifierNode("obj")
            val args = listOf<ExprNode>(LiteralNode(1, TokenType.INTEGER))
            val node = MethodCallNode(receiver, "method", args)
            assertSame(receiver, node.receiver)
            assertEquals("method", node.methodName)
            assertEquals(1, node.arguments.size)
        }
        
        @Test
        fun `test TypeCheckNode data class`() {
            val expr = IdentifierNode("obj")
            val node = TypeCheckNode(expr, "String", negated = false)
            assertSame(expr, node.expr)
            assertEquals("String", node.typeName)
            assertFalse(node.negated)
        }
        
        @Test
        fun `test TypeCastNode data class`() {
            val expr = IdentifierNode("obj")
            val node = TypeCastNode(expr, "String", safe = true)
            assertSame(expr, node.expr)
            assertEquals("String", node.typeName)
            assertTrue(node.safe)
        }
        
        @Test
        fun `test ConditionalNode data class`() {
            val condition = IdentifierNode("flag")
            val thenExpr = LiteralNode(1, TokenType.INTEGER)
            val elseExpr = LiteralNode(2, TokenType.INTEGER)
            val node = ConditionalNode(condition, thenExpr, elseExpr)
            assertSame(condition, node.condition)
            assertSame(thenExpr, node.thenExpr)
            assertSame(elseExpr, node.elseExpr)
        }
        
        @Test
        fun `test NewObjectNode data class`() {
            val args = listOf<ExprNode>(LiteralNode(10, TokenType.INTEGER))
            val node = NewObjectNode("ArrayList", args)
            assertEquals("ArrayList", node.typeName)
            assertEquals(1, node.arguments.size)
        }
        
        @Test
        fun `test ElvisNode data class`() {
            val left = IdentifierNode("a")
            val right = IdentifierNode("b")
            val node = ElvisNode(left, right)
            assertSame(left, node.left)
            assertSame(right, node.right)
        }
    }
    
    @Nested
    inner class TokenTests {
        
        @Test
        fun `test Token data class`() {
            val token = Token(TokenType.INTEGER, 42, 0)
            assertEquals(TokenType.INTEGER, token.type)
            assertEquals(42, token.value)
            assertEquals(0, token.position)
        }
        
        @Test
        fun `test Token toString`() {
            val token = Token(TokenType.STRING, "hello", 5)
            val str = token.toString()
            assertTrue(str.contains("STRING"))
            assertTrue(str.contains("hello"))
        }
        
        @Test
        fun `test Token equality`() {
            val token1 = Token(TokenType.INTEGER, 42, 0)
            val token2 = Token(TokenType.INTEGER, 42, 0)
            val token3 = Token(TokenType.INTEGER, 100, 0)
            
            assertEquals(token1, token2)
            assertNotEquals(token1, token3)
        }
    }
}
