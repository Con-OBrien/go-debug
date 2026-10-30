package com.conorobrien.godebug

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.dap.DapBreakpointsDescription
import com.intellij.platform.dap.DapLaunchArgumentsProvider
import com.intellij.platform.dap.DapStartRequest
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.DebugAdapterId
import com.intellij.platform.dap.DebugAdapterSupportProvider
import com.intellij.platform.dap.LaunchRequestArguments
import com.intellij.platform.dap.connection.DebugAdapterHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path

/** Identifies Delve to the platform's DAP client. Matched by equality, so it's a singleton. */
object DelveAdapterId : DebugAdapterId("go", "Delve")

class DelveSupportProvider : DebugAdapterSupportProvider<DelveAdapterId> {
    override val adapterId: DelveAdapterId = DelveAdapterId
    override fun createDebugAdapterDescriptor(project: Project): DebugAdapterDescriptor<DelveAdapterId> = DelveDescriptor()
}

/** Tells the platform's DAP program runner which run configurations it should debug, and with what. */
class DelveLaunchArgumentsProvider : DapLaunchArgumentsProvider {

    // Only Debug: plain Run goes through the standard runner and `go run`, without Delve.
    override fun isApplicable(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is GoRunConfiguration

    override fun getLaunchArguments(project: Project, profile: RunProfile): LaunchRequestArguments {
        val spec = (profile as GoRunConfiguration).toSpec()
        return LaunchRequestArguments(DelveAdapterId, DapStartRequest.Launch, GoCommands.delveLaunchArguments(spec))
    }
}

/**
 * Starts `dlv dap` for each debug session and connects to it. Delve's DAP server only speaks
 * TCP (no stdio mode), so it listens on a free loopback port and the platform talks to the socket.
 */
class DelveDescriptor : DebugAdapterDescriptor<DelveAdapterId>() {

    override val id: DelveAdapterId = DelveAdapterId

    override val breakpointsDescription: DapBreakpointsDescription =
        DapBreakpointsDescription(GoLineBreakpointType::class.java, GoPanicBreakpointType::class.java)

    override suspend fun launchDebugAdapter(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult?,
        sessionId: String,
    ): DebugAdapterHandle {
        val configuration = environment.runProfile as? GoRunConfiguration
            ?: throw ExecutionException("Go Debug can only debug Go run configurations")
        val spec = configuration.toSpec()
        val tools = GoToolLocator.toolchain()
        val dlv = tools.dlv ?: throw ExecutionException(
            "Delve (dlv) wasn't found. Install it with: ${GoToolLocator.INSTALL_DELVE}"
        )

        val port = freeLoopbackPort()
        val commandLine = GeneralCommandLine(dlv.toString(), "dap", "--listen=127.0.0.1:$port")
            .withWorkingDirectory(Path.of(spec.effectiveWorkingDir))
            .withEnvironment(GoToolLocator.pathWithGo(tools.go))

        val output = StringBuffer()
        val process = OSProcessHandler(commandLine)
        process.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (output.length < MAX_CAPTURED_OUTPUT) output.append(event.text)
            }
        })
        // Draining dlv's stdout/stderr keeps it from blocking on a full pipe.
        process.startNotify()

        val socket = try {
            connect(port, process, output)
        } catch (e: Throwable) {
            process.destroyProcess()
            throw e
        }
        return DelveConnection(socket, process)
    }

    private suspend fun connect(port: Int, process: OSProcessHandler, output: StringBuffer): Socket =
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + CONNECT_TIMEOUT_MS * 1_000_000
            var lastError: IOException? = null
            while (System.nanoTime() < deadline) {
                if (process.isProcessTerminated) {
                    throw ExecutionException("Delve exited before it accepted a connection:\n${output.toString().trim()}")
                }
                try {
                    return@withContext Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 1_000) }
                } catch (e: IOException) {
                    lastError = e
                    delay(100)
                }
            }
            throw ExecutionException(
                "Couldn't connect to Delve on port $port within ${CONNECT_TIMEOUT_MS / 1000}s. " +
                    "Output so far:\n${output.toString().trim()}",
                lastError,
            )
        }

    private fun freeLoopbackPort(): Int = ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val MAX_CAPTURED_OUTPUT = 16_000
    }
}

/** The DAP stream to Delve. Disconnecting closes the socket and makes sure dlv is gone. */
private class DelveConnection(private val socket: Socket, private val process: OSProcessHandler) : DebugAdapterHandle {

    override val input: InputStream = socket.getInputStream()
    override val output: OutputStream = socket.getOutputStream()

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching { socket.close() }
            // dlv exits on its own after a DAP disconnect; give it a moment, then make sure.
            if (!process.waitFor(2_000)) process.destroyProcess()
        }
    }
}
