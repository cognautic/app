package com.cognautic.app.core.tools

import java.io.File
import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

class RunCommandTool(private val workspacePath: String) : AgentTool {
    override val name = "run_command"
    override val description = "Run a terminal command in the workspace directory and get the output."

    override fun execute(args: Map<String, String>): String {
        val command = args["command"] ?: return "Error: Missing command argument"
        
        // Check if workspacePath is a local path. If it's a content:// URI, we can't easily run terminal commands.
        if (workspacePath.startsWith("content://")) {
            return "Error: Terminal commands are not supported for workspaces using Android Storage Access Framework (content:// URIs). Please use a local file path workspace if available."
        }

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(File(workspacePath))
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "Error: Command timed out after 60 seconds.\nOutput so far:\n$output"
            }

            val exitCode = process.exitValue()
            if (exitCode != 0 && output.isEmpty()) {
                return "Command failed with exit code $exitCode (no output)"
            }

            output.toString()
        } catch (e: Exception) {
            "Error executing command: ${e.message}"
        }
    }

    override fun getDefinition(): String {
        return """{"name":"run_command","description":"Execute shell command in workspace","parameters":{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}}"""
    }
}
