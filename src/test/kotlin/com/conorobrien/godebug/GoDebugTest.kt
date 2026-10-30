package com.conorobrien.godebug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class GoDebugTest {

    private val pkg = "/work/app/cmd/server"

    @Test
    fun `go run passes build flags before the package and program args after`() {
        val spec = GoLaunchSpec(packageDir = pkg, goToolArgs = "-race -tags=dev", programArgs = "--port 8080 \"a b\"")
        assertEquals(
            listOf("run", "-race", "-tags=dev", pkg, "--port", "8080", "a b"),
            GoCommands.runArgs(spec),
        )
    }

    @Test
    fun `go test adds -v, -run and -args`() {
        val spec = GoLaunchSpec(packageDir = pkg, kind = GoRunKind.TEST, testPattern = "^TestParse$", programArgs = "-update")
        assertEquals(
            listOf("test", "-v", "-run", "^TestParse$", pkg, "-args", "-update"),
            GoCommands.runArgs(spec),
        )
        assertEquals(listOf("test", "-v", pkg), GoCommands.runArgs(GoLaunchSpec(packageDir = pkg, kind = GoRunKind.TEST)))
    }

    @Test
    fun `delve launch arguments for an application`() {
        val args = GoCommands.delveLaunchArguments(
            GoLaunchSpec(packageDir = pkg, programArgs = "serve", goToolArgs = "-tags=dev", env = mapOf("APP_ENV" to "dev"))
        )
        assertEquals("debug", args["mode"])
        assertEquals(pkg, args["program"])
        assertEquals(pkg, args["cwd"])
        assertEquals("remote", args["outputMode"])
        assertEquals(listOf("serve"), args["args"])
        assertEquals("-tags=dev", args["buildFlags"])
        assertEquals(mapOf("APP_ENV" to "dev"), args["env"])
    }

    @Test
    fun `delve launch arguments for tests use test binary flags`() {
        val args = GoCommands.delveLaunchArguments(
            GoLaunchSpec(packageDir = pkg, kind = GoRunKind.TEST, testPattern = "TestX", workingDir = "/tmp/wd")
        )
        assertEquals("test", args["mode"])
        assertEquals(listOf("-test.v", "-test.run", "TestX"), args["args"])
        assertEquals("/tmp/wd", args["cwd"])
        assertFalse(args.containsKey("buildFlags"))
        assertFalse(args.containsKey("env"))
    }

    @Test
    fun `finds dlv in home go bin when PATH lacks it`() {
        val home = Path.of("/home/conor")
        val found = GoToolLocator.locate("dlv", mapOf("PATH" to "/usr/bin"), home, isWindows = false) {
            it.toString().replace('\\', '/') == "/home/conor/go/bin/dlv"
        }
        assertEquals("/home/conor/go/bin/dlv", found?.toString()?.replace('\\', '/'))
    }

    @Test
    fun `finds go under GOROOT and uses exe on windows`() {
        val found = GoToolLocator.locate(
            "go",
            mapOf("Path" to "C:/Windows", "GOROOT" to "C:/Go126"),
            Path.of("C:/Users/conor"),
            isWindows = true,
        ) { it.toString().replace('\\', '/') == "C:/Go126/bin/go.exe" }
        assertEquals("C:/Go126/bin/go.exe", found?.toString()?.replace('\\', '/'))
        assertNull(GoToolLocator.locate("dlv", emptyMap(), Path.of("/nowhere"), isWindows = false) { false })
    }

    @Test
    fun `breakpoints only on lines that can hold code`() {
        listOf("\tx := 1", "return err", "}", "for i := range n {", "fmt.Println(x) // log").forEach {
            assertTrue(it, GoSourceLines.isBreakable(it))
        }
        listOf("", "   ", "// comment", "/* block", " * more", "{", ")", "package main", "import \"fmt\"").forEach {
            assertFalse(it, GoSourceLines.isBreakable(it))
        }
    }
}
