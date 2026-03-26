package com.cognautic.synax

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinLexerTest {
    @Test
    fun testBasicKeywords() {
        val code = "val x = 10"
        val tokens = KotlinLexer(code).tokenize()
        
        val types = tokens.filter { it.type != TokenType.WHITESPACE }.map { it.type }
        assertEquals(listOf(TokenType.KEYWORD, TokenType.IDENTIFIER, TokenType.OPERATOR, TokenType.NUMBER), types)
    }

    @Test
    fun testStrings() {
        val code = """val s = "Hello World""""
        val tokens = KotlinLexer(code).tokenize()
        
        val stringToken = tokens.find { it.type == TokenType.STRING }
        assertEquals("\"Hello World\"", stringToken?.value)
    }

    @Test
    fun testComments() {
        val code = "// single line\n/* multi\nline */"
        val tokens = KotlinLexer(code).tokenize()
        
        val comments = tokens.filter { it.type == TokenType.COMMENT }
        assertEquals(2, comments.size)
        assertEquals("// single line", comments[0].value)
    }

    @Test
    fun testKotlinCode() {
        val code = """
            package com.example
            
            fun main() {
                println("Hello")
            }
        """.trimIndent()
        val tokens = KotlinLexer(code).tokenize()
        
        val nonWhitespace = tokens.filter { it.type != TokenType.WHITESPACE }
        val types = nonWhitespace.map { it.type }
        
        val expected = listOf(
            TokenType.KEYWORD, TokenType.IDENTIFIER, TokenType.PUNCTUATION, TokenType.IDENTIFIER, // package com.example
            TokenType.KEYWORD, TokenType.IDENTIFIER, TokenType.PUNCTUATION, TokenType.PUNCTUATION, TokenType.PUNCTUATION, // fun main() {
            TokenType.IDENTIFIER, TokenType.PUNCTUATION, TokenType.STRING, TokenType.PUNCTUATION, // println("Hello")
            TokenType.PUNCTUATION // }
        )
        assertEquals(expected, types)
    }
}
