package com.cognautic.synax

enum class TokenType {
    KEYWORD,
    IDENTIFIER,
    STRING,
    NUMBER,
    COMMENT,
    OPERATOR,
    PUNCTUATION,
    WHITESPACE,
    TAG,
    ATTRIBUTE,
    VALUE,
    ENTITY,
    UNKNOWN
}

data class Token(
    val type: TokenType,
    val value: String,
    val start: Int,
    val end: Int
)
