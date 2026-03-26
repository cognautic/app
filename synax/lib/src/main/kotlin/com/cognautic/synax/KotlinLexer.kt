package com.cognautic.synax

import java.util.regex.Pattern

class KotlinLexer(private val input: String) : Lexer {
    private var position = 0

    private val patterns = listOf(
        TokenType.COMMENT to Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"),
        TokenType.STRING to Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"),
        TokenType.KEYWORD to Pattern.compile("\\b(package|import|class|fun|val|var|if|else|while|for|when|return|try|catch|finally|throw|as|is|in|this|super|interface|object|typealias|typeof|where|by|get|set|constructor|init|field|property|receiver|param|setparam|delegate|it|companion|data|enum|sealed|annotation|internal|external|private|protected|public|noinline|crossinline|inline|reified|tailrec|operator|infix|suspend|expect|actual|true|false|null)\\b"),
        TokenType.NUMBER to Pattern.compile("\\b(0x[0-9a-fA-F]+|0b[01]+|[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?)\\b"),
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
