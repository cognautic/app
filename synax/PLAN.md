# Synax - Kotlin Syntax Highlighter for Android

Synax is a lightweight, efficient Kotlin syntax highlighter designed specifically for Android applications. It provides a simple API to convert Kotlin source code into styled spans for `TextView` or Composable text elements.

## Features
- Multi-language support (Kotlin, HTML, XML, CSS, etc.)
- Fast tokenization using regex
- Customizable theme support
- Android-ready output (styled ranges)
- Lightweight with minimal dependencies

## Project Structure
- `lib/`: Core library containing the lexer and highlighter logic.
- `lib/src/main/kotlin/com/cognautic/synax/`: implementation files.
- `lib/src/test/kotlin/com/cognautic/synax/`: Unit tests.

## Roadmap
1. [x] Core Lexer Engine
2. [x] Kotlin support
3. [x] HTML/XML support
4. [x] CSS support
5. [x] Java support
6. [x] Python support
7. [x] JavaScript support
8. [ ] Highlighter API for generating styled ranges
9. [x] Theme system
10. [ ] Android-specific helpers (SpannableString support)
11. [x] Extensive documentation
12. [x] Unit tests
