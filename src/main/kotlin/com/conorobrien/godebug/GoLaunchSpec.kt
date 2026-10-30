package com.conorobrien.godebug

import com.intellij.util.execution.ParametersListUtil

enum class GoRunKind(val label: String) {
    APPLICATION("Application"),
    TEST("Tests");

    override fun toString(): String = label
}

/**
 * Everything needed to run or debug one Go package, independent of IntelliJ types so the
 * command lines and Delve arguments can be unit tested.
 */
data class GoLaunchSpec(
    /** Absolute directory of the package to build. */
    val packageDir: String,
    val kind: GoRunKind = GoRunKind.APPLICATION,
    /** `go test -run` pattern; blank runs every test in the package. */
    val testPattern: String = "",
    /** Arguments passed to the program (or to the test binary). */
    val programArgs: String = "",
    /** Flags for `go build` / `go test`, e.g. `-race -tags=integration`. */
    val goToolArgs: String = "",
    /** Blank means the package directory. */
    val workingDir: String = "",
    val env: Map<String, String> = emptyMap(),
) {
    val effectiveWorkingDir: String get() = workingDir.ifBlank { packageDir }
}

object GoCommands {

    /** Arguments after the `go` executable for a plain (non-debug) run. */
    fun runArgs(spec: GoLaunchSpec): List<String> = buildList {
        val toolArgs = parse(spec.goToolArgs)
        when (spec.kind) {
            GoRunKind.APPLICATION -> {
                add("run")
                addAll(toolArgs)
                add(spec.packageDir)
                addAll(parse(spec.programArgs))
            }
            GoRunKind.TEST -> {
                add("test")
                addAll(toolArgs)
                add("-v")
                if (spec.testPattern.isNotBlank()) {
                    add("-run")
                    add(spec.testPattern.trim())
                }
                add(spec.packageDir)
                val programArgs = parse(spec.programArgs)
                if (programArgs.isNotEmpty()) {
                    add("-args")
                    addAll(programArgs)
                }
            }
        }
    }

    /**
     * Arguments for Delve's DAP `launch` request.
     * See https://github.com/go-delve/delve/blob/master/Documentation/api/dap/README.md
     */
    fun delveLaunchArguments(spec: GoLaunchSpec): Map<String, Any> = buildMap {
        put("name", "Go")
        put("mode", if (spec.kind == GoRunKind.TEST) "test" else "debug")
        put("program", spec.packageDir)
        put("cwd", spec.effectiveWorkingDir)
        put("stopOnEntry", false)
        // Send the program's stdout/stderr as DAP output events so it appears in the Debug console,
        // rather than on dlv's own stdout which the IDE doesn't show.
        put("outputMode", "remote")

        val args = buildList {
            if (spec.kind == GoRunKind.TEST) {
                add("-test.v")
                if (spec.testPattern.isNotBlank()) {
                    add("-test.run")
                    add(spec.testPattern.trim())
                }
            }
            addAll(parse(spec.programArgs))
        }
        if (args.isNotEmpty()) put("args", args)
        if (spec.goToolArgs.isNotBlank()) put("buildFlags", spec.goToolArgs.trim())
        if (spec.env.isNotEmpty()) put("env", spec.env)
    }

    private fun parse(text: String): List<String> =
        if (text.isBlank()) emptyList() else ParametersListUtil.parse(text, false, true)
}
