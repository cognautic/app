# Synax Documentation

## Getting Started

Synax is a simple library for syntax highlighting Kotlin code.

### Usage

```kotlin
// Highlight Kotlin (default)
val tokens = Synax.highlight(code)

// Highlight HTML
val htmlTokens = Synax.highlight(htmlCode, Language.HTML)

// Highlight CSS
val cssTokens = Synax.highlight(cssCode, Language.CSS)

for (token in tokens) {
    println("${token.type}: ${token.value}")
}
```

### Supported Languages

- `KOTLIN`
- `HTML`
- `XML`
- `CSS`
- `JAVA`
- `PYTHON`
- `JAVASCRIPT`
- `MARKDOWN` (Planned)

### Supported Token Types

- `KEYWORD`: Kotlin keywords (`fun`, `val`, `var`, etc.)
- `IDENTIFIER`: Variable and function names
- `STRING`: String literals and character literals
- `NUMBER`: Integer and floating-point numbers
- `COMMENT`: Single-line and multi-line comments
- `OPERATOR`: Mathematical and logical operators
- `PUNCTUATION`: Brackets, commas, etc.
- `WHITESPACE`: Spaces, tabs, and newlines

### Android Integration

Since Synax returns a list of tokens with start and end positions, you can easily convert them to `SpannableString` in Android:

```kotlin
val spannable = SpannableString(code)
val tokens = Synax.highlight(code)
val theme = SynaxTheme.DARK

for (token in tokens) {
    val color = when (token.type) {
        TokenType.KEYWORD -> theme.keywordColor
        TokenType.STRING -> theme.stringColor
        // ... and so on
        else -> theme.defaultColor
    }
    spannable.setSpan(
        ForegroundColorSpan(color),
        token.start,
        token.end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
}
```
