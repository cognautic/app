package com.cognautic.synax

import java.util.regex.Pattern

class HtmlLexer(private val input: String) : Lexer {
    private var position = 0

    private val patterns = listOf(
        TokenType.COMMENT to Pattern.compile("<!--[\\s\\S]*?-->"),
        TokenType.TAG to Pattern.compile("</?[a-zA-Z0-9]+>?"),
        TokenType.ATTRIBUTE to Pattern.compile("\\s+[a-zA-Z0-9-]+(?==)"),
        TokenType.OPERATOR to Pattern.compile("="),
        TokenType.STRING to Pattern.compile("\"[^\"]*\"|'[^']*'"),
        TokenType.ENTITY to Pattern.compile("&[a-zA-Z0-9#]+;"),
        TokenType.WHITESPACE to Pattern.compile("\\s+"),
        TokenType.PUNCTUATION to Pattern.compile("[<>/]"),
        TokenType.UNKNOWN to Pattern.compile("[^\\s<>&\"'=/]+") // This will catch text content as UNKNOWN or we could add a TEXT type
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
