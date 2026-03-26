package com.cognautic.synax

import java.util.regex.Pattern

class PythonLexer(private val input: String) : Lexer {
    private var position = 0

    private val patterns = listOf(
        TokenType.COMMENT to Pattern.compile("#.*"),
        TokenType.STRING to Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"),
        TokenType.KEYWORD to Pattern.compile("\\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\\b"),
        TokenType.NUMBER to Pattern.compile("\\b(0x[0-9a-fA-F]+|0b[01]+|0o[0-7]+|[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?)\\b"),
        TokenType.OPERATOR to Pattern.compile("[+\\-*/%<>=!&|^~?:]+"),
        TokenType.PUNCTUATION to Pattern.compile("[\\[\\](){},.;]"),
        TokenType.WHITESPACE to Pattern.compile("\\s+"),
        TokenType.IDENTIFIER to Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*")
    )

    override fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (position < input.length) {
            var matched = false
            val remaining = input.substring(position)

            for ((type, pattern) in patterns) {
                val matcher = pattern.matcher(remaining)
                if (matcher.lookingAt()) {
                    val value = matcher.group()
                    tokens.add(Token(type, value, position, position + value.length))
                    position += value.length
                    matched = true
                    break
                }
            }

            if (!matched) {
                tokens.add(Token(TokenType.UNKNOWN, input[position].toString(), position, position + 1))
                position++
            }
        }
        return tokens
    }
}
