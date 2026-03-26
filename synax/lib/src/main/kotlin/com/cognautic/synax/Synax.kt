package com.cognautic.synax

data class SynaxTheme(
    val keywordColor: Int,
    val identifierColor: Int,
    val stringColor: Int,
    val numberColor: Int,
    val commentColor: Int,
    val operatorColor: Int,
    val punctuationColor: Int,
    val defaultColor: Int
) {
    companion object {
        val DARK = SynaxTheme(
            keywordColor = 0xFFFFFFFF.toInt(),        // White
            identifierColor = 0xFFE0E0E0.toInt(),     // Light gray
            stringColor = 0xFFD0D0D0.toInt(),         // Light gray
            numberColor = 0xFFC0C0C0.toInt(),         // Gray
            commentColor = 0xFF8A8A8A.toInt(),        // Dark gray
            operatorColor = 0xFFE0E0E0.toInt(),
            punctuationColor = 0xFFE0E0E0.toInt(),
            defaultColor = 0xFFE0E0E0.toInt()
        )
    }
}

enum class Language {
    KOTLIN,
    HTML,
    XML,
    CSS,
    JAVA,
    PYTHON,
    JAVASCRIPT,
    MARKDOWN
}

object Synax {
    fun highlight(input: String, language: Language = Language.KOTLIN): List<Token> {
        val lexer: Lexer = when (language) {
            Language.KOTLIN -> KotlinLexer(input)
            Language.JAVA -> JavaLexer(input)
            Language.PYTHON -> PythonLexer(input)
            Language.JAVASCRIPT -> JavaScriptLexer(input)
            Language.HTML, Language.XML -> HtmlLexer(input)
            Language.CSS -> CssLexer(input)
            else -> KotlinLexer(input) // Fallback
        }
        return lexer.tokenize()
    }
}
