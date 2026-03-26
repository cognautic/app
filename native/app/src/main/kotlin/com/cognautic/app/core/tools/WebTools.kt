package com.cognautic.app.core.tools

import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebSearchTool : AgentTool {
    override val name = "web_search"
    override val description = "Search the web using DuckDuckGo. Returns instant answers and related topics."

    override fun execute(args: Map<String, String>): String {
        val query = args["query"] ?: return "Error: Missing query argument"
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CognauticAI/1.0")

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            val result = StringBuilder()
            
            val abstract = json.optString("AbstractText")
            if (abstract.isNotEmpty()) {
                result.append("Abstract: $abstract\n\n")
            }
            
            val related = json.optJSONArray("RelatedTopics")
            if (related != null && related.length() > 0) {
                result.append("Related Information:\n")
                for (i in 0 until minOf(related.length(), 5)) {
                    val topic = related.optJSONObject(i)
                    if (topic != null && topic.has("Text")) {
                        result.append("- ${topic.getString("Text")}\n")
                    }
                }
            }
            
            if (result.isEmpty()) {
                "No direct results found for '$query'. Try a different query or use 'read_url' if you have a specific link."
            } else {
                result.toString()
            }
        } catch (e: Exception) {
            "Error performing search: ${e.message}"
        }
    }

    override fun getDefinition(): String {
        return """
        {
            "name": "web_search",
            "description": "Search the web for information",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": { "type": "string", "description": "The search query" }
                },
                "required": ["query"]
            }
        }
        """.trimIndent()
    }
}

class ReadUrlTool : AgentTool {
    override val name = "read_url"
    override val description = "Fetch and read the text content of a website URL."

    override fun execute(args: Map<String, String>): String {
        val urlStr = args["url"] ?: return "Error: Missing url argument"
        return try {
            val doc = Jsoup.connect(urlStr)
                .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .timeout(10000)
                .get()
            
            // Basic cleanup: remove script and style elements
            doc.select("script, style, nav, footer").remove()
            
            val title = doc.title()
            val text = doc.body().text()
            
            // Limit content to avoid token overflow in LLM
            val maxChars = 8000
            val truncatedText = if (text.length > maxChars) text.substring(0, maxChars) + "..." else text
            
            "Title: $title\n\nContent:\n$truncatedText"
        } catch (e: Exception) {
            "Error reading URL: ${e.message}"
        }
    }

    override fun getDefinition(): String {
        return """
        {
            "name": "read_url",
            "description": "Read content from a website URL",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": { "type": "string", "description": "The full URL to read (e.g., https://example.com)" }
                },
                "required": ["url"]
            }
        }
        """.trimIndent()
    }
}
