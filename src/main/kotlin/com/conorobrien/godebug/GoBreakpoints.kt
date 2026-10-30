package com.conorobrien.godebug

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

/** Go breakpoints carry no extra settings; conditions and log messages are handled by the platform. */
class GoBreakpointProperties : XBreakpointProperties<GoBreakpointProperties>() {
    override fun getState(): GoBreakpointProperties = this
    override fun loadState(state: GoBreakpointProperties) {}
}

/**
 * Lets you click the gutter of a .go file to toggle a breakpoint. The platform's DAP client
 * sends these to Delve as source breakpoints, including conditions and hit counts.
 */
class GoLineBreakpointType : XLineBreakpointType<GoBreakpointProperties>("go-delve-line", "Go Line Breakpoints") {

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        if (!file.name.endsWith(".go")) return false
        // With JetBrains' Go plugin active, its own breakpoint type owns .go files.
        if (PluginManagerCore.isLoaded(PluginId.getId("org.jetbrains.plugins.go"))) return false
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return true
        if (line < 0 || line >= document.lineCount) return false
        val text = document.charsSequence
            .subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line))
            .toString()
        return GoSourceLines.isBreakable(text)
    }

    override fun createBreakpointProperties(file: VirtualFile, line: Int): GoBreakpointProperties? = null
}

/**
 * Required by the DAP client's breakpoint description. Delve has no exception breakpoint filters
 * (it always stops on an unrecovered panic), so this type is never registered or shown.
 */
class GoPanicBreakpointType : XBreakpointType<XBreakpoint<GoBreakpointProperties>, GoBreakpointProperties>(
    "go-delve-panic",
    "Go Panics",
) {
    override fun getDisplayText(breakpoint: XBreakpoint<GoBreakpointProperties>?): String = "Go panic"
}

internal object GoSourceLines {
    /**
     * Blank lines, comments, package/import clauses and lone opening brackets never hold code
     * Delve could stop on. A lone `}` is allowed: on a function's closing brace Delve stops just
     * before it returns.
     */
    fun isBreakable(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*") &&
            t != "{" && t != ")" && !t.startsWith("package ") && !t.startsWith("import ")
    }
}
