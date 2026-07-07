package com.cognautic.app.core.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface AgentTool {
    val name: String
    val description: String
    fun execute(args: Map<String, String>): String
    fun getDefinition(): String
}

/**
 * Helper to resolve a file or directory path relative to a root (which could be a URI or a local path)
 */
object FileResolver {
    fun resolve(context: Context, root: String, relPath: String): DocumentFile? {
        var sanitized = relPath.trim()
        if (sanitized == "." || sanitized == "./") sanitized = ""
        sanitized = sanitized.removePrefix("./").removePrefix("/")

        if (root.startsWith("content://")) {
            val rootDoc = try {
                DocumentFile.fromTreeUri(context, Uri.parse(root))
            } catch (e: Exception) {
                null
            } ?: return null
            
            if (sanitized.isEmpty()) return rootDoc
            
            var current = rootDoc
            val segments = sanitized.split("/")
            for (segment in segments) {
                if (segment.isBlank()) continue
                current = current.findFile(segment) ?: return null
            }
            return current
        } else {
            val file = if (sanitized.isEmpty()) File(root) else File(root, sanitized)
            return if (file.exists()) DocumentFile.fromFile(file) else null
        }
    }

    // Special version for writing where we might need to create parents
    fun resolveForWrite(context: Context, root: String, relPath: String): DocumentFile? {
        var sanitized = relPath.trim()
        if (sanitized == "." || sanitized == "./") sanitized = ""
        sanitized = sanitized.removePrefix("./").removePrefix("/")

        if (root.startsWith("content://")) {
            val rootDoc = try {
                DocumentFile.fromTreeUri(context, Uri.parse(root))
            } catch (e: Exception) {
                null
            } ?: return null
            
            if (sanitized.isEmpty()) return rootDoc

            val segments = sanitized.split("/")
            var current = rootDoc
            
            for (i in 0 until segments.size - 1) {
                val segment = segments[i]
                if (segment.isBlank()) continue
                current = current.findFile(segment) ?: current.createDirectory(segment) ?: return null
            }
            
            val fileName = segments.last()
            val extension = fileName.substringAfterLast('.', "")
            val mimeType = if (extension.isNotEmpty()) {
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "text/plain"
            } else {
                "text/plain"
            }
            
            return current.findFile(fileName) ?: current.createFile(mimeType, fileName)
        } else {
            val file = if (sanitized.isEmpty()) File(root) else File(root, sanitized)
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                if (sanitized.isNotEmpty()) file.createNewFile()
                else return null // Cannot create root if missing this way
            }
            return DocumentFile.fromFile(file)
        }
    }

    fun readText(context: Context, doc: DocumentFile): String {
        return context.contentResolver.openInputStream(doc.uri)?.use { 
            it.bufferedReader().readText() 
        } ?: ""
    }

    fun writeText(context: Context, doc: DocumentFile, text: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { 
                it.bufferedWriter().use { writer -> writer.write(text) }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}

class ListFilesTool(private val context: Context, private val workspaceRoot: String) : AgentTool {
    override val name = "list_files"
    override val description = "List files in a directory relative to the workspace root."

    override fun execute(args: Map<String, String>): String {
        val relPath = (args["path"] ?: ".").trim().removePrefix("./").removePrefix("/")
        
        if (!workspaceRoot.startsWith("content://")) {
            val rootFile = File(workspaceRoot)
            val targetFile = if (relPath.isEmpty() || relPath == ".") rootFile else File(rootFile, relPath)
            
            if (!targetFile.exists()) return "Error: Path '$relPath' not found ($targetFile)"
            if (!targetFile.isDirectory) return "Error: '$relPath' is not a directory."
            
            val files = targetFile.listFiles() ?: return "Error: Could not list files (permission denied?)"
            if (files.isEmpty()) return "Empty directory"
            
            return files.sortedByDescending { it.isDirectory }.joinToString("\n") { 
                val type = if (it.isDirectory) "[DIR]" else "[FILE]"
                "$type ${it.name}" 
            }
        }

        val target = FileResolver.resolve(context, workspaceRoot, relPath)
            ?: return "Error: Path '$relPath' not found or inaccessible."
        
        if (!target.isDirectory) return "Error: '$relPath' is not a directory."

        val files = target.listFiles()
        if (files.isEmpty()) return "Empty directory"
        
        return files.sortedByDescending { it.isDirectory }.joinToString("\n") { 
            val type = if (it.isDirectory) "[DIR]" else "[FILE]"
            "$type ${it.name}" 
        }
    }

    override fun getDefinition(): String {
        return """{"name":"list_files","description":"List files in directory","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}"""
    }
}

class ReadFileTool(private val context: Context, private val workspaceRoot: String) : AgentTool {
    override val name = "read_file"
    override val description = "Read the contents of a file."

    override fun execute(args: Map<String, String>): String {
        val relPath = args["path"] ?: return "Error: Missing path argument"
        val target = FileResolver.resolve(context, workspaceRoot, relPath)
            ?: return "Error: File '$relPath' not found."
            
        if (target.isDirectory) return "Error: '$relPath' is a directory."

        return try {
            FileResolver.readText(context, target)
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    override fun getDefinition(): String {
         return """{"name":"read_file","description":"Read file contents","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}"""
    }
}

class WriteFileTool(private val context: Context, private val workspaceRoot: String) : AgentTool {
    override val name = "write_file"
    override val description = "Write content to a file. Overwrites if exists."

    override fun execute(args: Map<String, String>): String {
        val relPath = (args["path"] ?: return "Error: Missing path argument").trim().removePrefix("./").removePrefix("/")
        val content = args["content"] ?: return "Error: Missing content argument"
        
        if (!workspaceRoot.startsWith("content://")) {
            return try {
                val file = if (relPath.isEmpty()) File(workspaceRoot) else File(workspaceRoot, relPath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                "Success: File '$relPath' written."
            } catch (e: Exception) {
                "Error writing file: ${e.message}"
            }
        }

        val target = FileResolver.resolveForWrite(context, workspaceRoot, relPath)
            ?: return "Error: Could not create or access file at '$relPath'."
        
        return try {
            if (FileResolver.writeText(context, target, content)) {
                "Success: File '$relPath' written."
            } else {
                "Error: Failed to write to '$relPath'."
            }
        } catch (e: Exception) {
            "Error writing file: ${e.message}"
        }
    }

    override fun getDefinition(): String {
         return """{"name":"write_file","description":"Write text to file","parameters":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}}"""
    }
}

class ReplaceContentTool(private val context: Context, private val workspaceRoot: String) : AgentTool {
    override val name = "replace_content"
    override val description = "Replace a block of text in a file with new content. Use this for editing existing files."

    override fun execute(args: Map<String, String>): String {
        val relPath = (args["path"] ?: return "Error: Missing path argument").trim().removePrefix("./").removePrefix("/")
        val oldContent = args["old_content"] ?: return "Error: Missing old_content argument"
        val newContent = args["new_content"] ?: return "Error: Missing new_content argument"
        
        if (!workspaceRoot.startsWith("content://")) {
            return try {
                val file = if (relPath.isEmpty()) File(workspaceRoot) else File(workspaceRoot, relPath)
                if (!file.exists()) return "Error: File '$relPath' not found."
                
                val currentText = file.readText()
                if (!currentText.contains(oldContent)) {
                    return "Error: Could not find exactly matching 'old_content' in '$relPath'."
                }
                val updated = currentText.replace(oldContent, newContent)
                file.writeText(updated)
                "Success: Replaced content in '$relPath'."
            } catch (e: Exception) {
                "Error replacing content: ${e.message}"
            }
        }

        val target = FileResolver.resolve(context, workspaceRoot, relPath)
            ?: return "Error: File '$relPath' not found."
            
        if (target.isDirectory) return "Error: '$relPath' is a directory."
        
        return try {
            val currentContent = FileResolver.readText(context, target)
            if (!currentContent.contains(oldContent)) {
                return "Error: Could not find exactly matching 'old_content' in file '$relPath'. Ensure every character (including whitespace) matches exactly."
            }
            val updated = currentContent.replace(oldContent, newContent)
            if (FileResolver.writeText(context, target, updated)) {
                "Success: Replaced content in '$relPath'."
            } else {
                "Error: Failed to write updated content to '$relPath'."
            }
        } catch (e: Exception) {
            "Error replacing content: ${e.message}"
        }
    }

    override fun getDefinition(): String {
        return """{"name":"replace_content","description":"Replace text in file","parameters":{"type":"object","properties":{"path":{"type":"string"},"old_content":{"type":"string"},"new_content":{"type":"string"}},"required":["path","old_content","new_content"]}}"""
    }
}

class ApplyEditsTool(private val context: Context, private val workspaceRoot: String) : AgentTool {
    override val name = "apply_edits"
    override val description = "Apply multiple search-and-replace edits to a file. Use this for complex modifications."

    override fun execute(args: Map<String, String>): String {
        val relPath = args["path"] ?: return "Error: Missing path argument"
        val editsJson = args["edits"] ?: return "Error: Missing edits argument"
        
        val target = FileResolver.resolve(context, workspaceRoot, relPath)
            ?: return "Error: File '$relPath' not found."
            
        return try {
            val jsonArray = JSONArray(editsJson)
            var content = FileResolver.readText(context, target)
            var appliedCount = 0
            
            for (i in 0 until jsonArray.length()) {
                val edit = jsonArray.getJSONObject(i)
                val old = edit.getString("old_content")
                val new = edit.getString("new_content")
                
                if (content.contains(old)) {
                    content = content.replace(old, new)
                    appliedCount++
                } else {
                    return "Error: Could not find exact match for edit block $i in '$relPath'. No changes applied."
                }
            }
            
            if (FileResolver.writeText(context, target, content)) {
                "Success: Applied $appliedCount edits to '$relPath'."
            } else {
                "Error: Failed to write updated content to '$relPath'."
            }
        } catch (e: Exception) {
            "Error applying edits: ${e.message}"
        }
    }

    override fun getDefinition(): String {
        return """{"name":"apply_edits","description":"Apply multiple edits to file","parameters":{"type":"object","properties":{"path":{"type":"string"},"edits":{"type":"string","description":"JSON array of {old_content,new_content}"}},"required":["path","edits"]}}"""
    }
}

// Simple Registry
class ToolRegistry(context: Context, workspacePath: String) {
    val tools = listOf(
        ListFilesTool(context, workspacePath),
        ReadFileTool(context, workspacePath),
        WriteFileTool(context, workspacePath),
        ReplaceContentTool(context, workspacePath),
        ApplyEditsTool(context, workspacePath),
        RunCommandTool(workspacePath),
        WebSearchTool(),
        ReadUrlTool()
    )

    fun getTool(name: String): AgentTool? = tools.find { it.name == name }
    
fun getSystemPromptSupplement(): String {
        return """
        TOOLS (respond with ONLY JSON: {"tool":"name","args":{...}}):
        ${tools.map { "- ${it.name}: ${it.description}" }.joinToString("\n")}
        
        RULES: Use relative paths only. Inspect before editing. Run tests after changes.
        """.trimIndent()
    }
}
