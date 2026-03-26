package com.cognautic.synax

import java.util.regex.Pattern

class CssLexer(private val input: String) : Lexer {
    private var position = 0

    private val patterns = listOf(
        TokenType.COMMENT to Pattern.compile("/\\*[\\s\\S]*?\\*/"),
        TokenType.KEYWORD to Pattern.compile("@[a-zA-Z-]+"),
        TokenType.ATTRIBUTE to Pattern.compile("[a-zA-Z-]+(?=\\s*:)"),
        TokenType.VALUE to Pattern.compile("#[a-fA-F0-9]{3,8}|[0-9]+(\\.[0-9]+)?(px|em|rem|%|s|ms|deg|vh|vw)?"),
        TokenType.IDENTIFIER to Pattern.compile("[.#][a-zA-Z-][a-zA-Z0-9-]*"),
        TokenType.IDENTIFIER to Pattern.compile("[a-zA-Z-][a-zA-Z0-9-]*"),
        TokenType.OPERATOR to Pattern.compile("[:!]"),
        TokenType.PUNCTUATION to Pattern.compile("[{};,]"),
        TokenType.WHITESPACE to Pattern.compile("\\s+")
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
