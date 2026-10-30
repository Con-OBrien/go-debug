package com.conorobrien.godebug

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import java.nio.file.Files
import java.nio.file.Path

/** The two executables this plugin runs. */
data class GoToolchain(val go: Path?, val dlv: Path?)

/**
 * Finds `go` and `dlv` without running anything. Pure function of its inputs so it can be tested.
 *
 * IDEs started from the Start menu or the Dock often lack the shell PATH, so this also looks
 * where Go installers and `go install` put binaries: GOROOT/bin, GOBIN, GOPATH/bin, ~/go/bin.
 */
object GoToolLocator {

    fun locate(
        tool: String,
        env: Map<String, String>,
        home: Path,
        isWindows: Boolean,
        isExecutable: (Path) -> Boolean = ::defaultIsExecutable,
    ): Path? {
        val exe = if (isWindows) "$tool.exe" else tool
        val separator = if (isWindows) ";" else ":"
        val get = { key: String ->
            if (isWindows) env.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value else env[key]
        }

        val dirs = buildList {
            addAll(get("PATH").orEmpty().split(separator))
            get("GOROOT")?.let { add(Path.of(it.trim()).resolve("bin").toString()) }
            get("GOBIN")?.let { add(it) }
            get("GOPATH").orEmpty().split(separator).filter { it.isNotBlank() }
                .forEach { add(Path.of(it.trim()).resolve("bin").toString()) }
            add(home.resolve("go").resolve("bin").toString())
            if (isWindows) {
                add("C:\\Program Files\\Go\\bin")
            } else {
                addAll(listOf("/usr/local/go/bin", "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"))
            }
        }

        return dirs.asSequence()
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { Path.of(it).resolve(exe) }.getOrNull() }
            .firstOrNull(isExecutable)
    }

    fun toolchain(): GoToolchain {
        val env = EnvironmentUtil.getEnvironmentMap()
        val home = Path.of(System.getProperty("user.home"))
        return GoToolchain(
            go = locate("go", env, home, SystemInfo.isWindows),
            dlv = locate("dlv", env, home, SystemInfo.isWindows),
        )
    }

    /**
     * PATH for child processes with go's bin directory first, so `dlv` (which shells out to
     * `go build`) and `go run` use the same toolchain even when the IDE's PATH lacks it.
     */
    fun pathWithGo(go: Path?, isWindows: Boolean = SystemInfo.isWindows): Map<String, String> {
        val goBin = go?.parent?.toString() ?: return emptyMap()
        val env = EnvironmentUtil.getEnvironmentMap()
        val key = if (isWindows) env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "Path" else "PATH"
        val separator = if (isWindows) ";" else ":"
        val current = env[key].orEmpty()
        return mapOf(key to if (current.isEmpty()) goBin else goBin + separator + current)
    }

    const val INSTALL_DELVE = "go install github.com/go-delve/delve/cmd/dlv@latest"

    private fun defaultIsExecutable(path: Path): Boolean = Files.isRegularFile(path) && Files.isExecutable(path)
}
