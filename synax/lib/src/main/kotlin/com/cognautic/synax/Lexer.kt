package com.cognautic.synax

interface Lexer {
    fun tokenize(): List<Token>
}
