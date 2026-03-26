package com.cognautic.synax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiLanguageTest {
    @Test
    fun testHtmlHighlighting() {
        val html = """<div class="container">Hello &amp; world</div>"""
        val tokens = Synax.highlight(html, Language.HTML)
        
        val tags = tokens.filter { it.type == TokenType.TAG }
        assertEquals(2, tags.size) // <div and </div>
        assertEquals("<div", tags[0].value)
        assertEquals("</div>", tags[1].value)

        val attr = tokens.find { it.type == TokenType.ATTRIBUTE }
        assertEquals(" class", attr?.value)

        val entity = tokens.find { it.type == TokenType.ENTITY }
        assertEquals("&amp;", entity?.value)
    }

    @Test
    fun testCssHighlighting() {
        val css = """
            .button {
                color: #ffffff;
                margin-top: 10px;
            }
        """.trimIndent()
        val tokens = Synax.highlight(css, Language.CSS)
        
        val identifiers = tokens.filter { it.type == TokenType.IDENTIFIER }
        assertTrue(identifiers.any { it.value == ".button" })

        val attributes = tokens.filter { it.type == TokenType.ATTRIBUTE }
        assertTrue(attributes.any { it.value == "color" })
        assertTrue(attributes.any { it.value == "margin-top" })

        val values = tokens.filter { it.type == TokenType.VALUE }
        assertTrue(values.any { it.value.trim() == "#ffffff" })
    }

    @Test
    fun testJavaHighlighting() {
        val java = "public class Main { public static void main(String[] args) {} }"
        val tokens = Synax.highlight(java, Language.JAVA)
        val keywords = tokens.filter { it.type == TokenType.KEYWORD }.map { it.value }
        assertTrue(keywords.contains("public"))
        assertTrue(keywords.contains("class"))
        assertTrue(keywords.contains("static"))
    }

    @Test
    fun testPythonHighlighting() {
        val python = "def hello():\n    print(\"world\") # comment"
        val tokens = Synax.highlight(python, Language.PYTHON)
        val keywords = tokens.filter { it.type == TokenType.KEYWORD }.map { it.value }
        assertTrue(keywords.contains("def"))
        val comments = tokens.filter { it.type == TokenType.COMMENT }.map { it.value }
        assertTrue(comments.contains("# comment"))
    }

    @Test
    fun testJavaScriptHighlighting() {
        val js = "const x = () => { console.log('hi'); }"
        val tokens = Synax.highlight(js, Language.JAVASCRIPT)
        val keywords = tokens.filter { it.type == TokenType.KEYWORD }.map { it.value }
        assertTrue(keywords.contains("const"))
        val strings = tokens.filter { it.type == TokenType.STRING }.map { it.value }
        assertTrue(strings.contains("'hi'"))
    }
}
