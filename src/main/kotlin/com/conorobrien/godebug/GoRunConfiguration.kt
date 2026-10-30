package com.conorobrien.godebug

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

class GoRunConfigurationType : ConfigurationType {
    private val factory = GoRunConfigurationFactory(this)

    override fun getDisplayName(): String = "Go"
    override fun getConfigurationTypeDescription(): String = "Run or debug a Go package or its tests"
    override fun getIcon(): Icon = ICON
    override fun getId(): String = ID
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)

    companion object {
        const val ID = "com.conorobrien.godebug.GoRunConfiguration"
        val ICON: Icon = IconLoader.getIcon("/icons/go.svg", GoRunConfigurationType::class.java)
    }
}

class GoRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "Go"
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        GoRunConfiguration(project, this, "Go")
}

class GoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<Any>(project, factory, name) {

    var packageDir: String = project.basePath.orEmpty()
    var kind: GoRunKind = GoRunKind.APPLICATION
    var testPattern: String = ""
    var programArgs: String = ""
    var goToolArgs: String = ""
    var workingDir: String = ""
    var env: Map<String, String> = emptyMap()

    fun toSpec(): GoLaunchSpec = GoLaunchSpec(
        packageDir = packageDir.trim(),
        kind = kind,
        testPattern = testPattern,
        programArgs = programArgs,
        goToolArgs = goToolArgs,
        workingDir = workingDir.trim(),
        env = env,
    )

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = GoRunConfigurationEditor()

    override fun checkConfiguration() {
        val dir = packageDir.trim()
        if (dir.isEmpty()) throw RuntimeConfigurationError("Choose the package directory to run")
        if (!Files.isDirectory(Path.of(dir))) throw RuntimeConfigurationError("Package directory doesn't exist: $dir")
        val wd = workingDir.trim()
        if (wd.isNotEmpty() && !Files.isDirectory(Path.of(wd))) {
            throw RuntimeConfigurationError("Working directory doesn't exist: $wd")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            // The platform's DAP runner starts Delve itself (see DelveDescriptor.launchDebugAdapter);
            // there is no separate process to run here.
            RunProfileState { _, _ -> null }
        } else {
            GoRunState(environment, toSpec())
        }

    override fun suggestedName(): String {
        val base = Path.of(packageDir.ifBlank { "." }).fileName?.toString() ?: "Go"
        return if (kind == GoRunKind.TEST) "go test $base" else "go run $base"
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        packageDir = JDOMExternalizerUtil.readField(element, "packageDir", packageDir)
        kind = runCatching { GoRunKind.valueOf(JDOMExternalizerUtil.readField(element, "kind", kind.name)) }
            .getOrDefault(GoRunKind.APPLICATION)
        testPattern = JDOMExternalizerUtil.readField(element, "testPattern", "")
        programArgs = JDOMExternalizerUtil.readField(element, "programArgs", "")
        goToolArgs = JDOMExternalizerUtil.readField(element, "goToolArgs", "")
        workingDir = JDOMExternalizerUtil.readField(element, "workingDir", "")
        env = LinkedHashMap<String, String>().also { EnvironmentVariablesComponent.readExternal(element, it) }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "packageDir", packageDir)
        JDOMExternalizerUtil.writeField(element, "kind", kind.name)
        JDOMExternalizerUtil.writeField(element, "testPattern", testPattern)
        JDOMExternalizerUtil.writeField(element, "programArgs", programArgs)
        JDOMExternalizerUtil.writeField(element, "goToolArgs", goToolArgs)
        JDOMExternalizerUtil.writeField(element, "workingDir", workingDir)
        EnvironmentVariablesComponent.writeExternal(element, env)
    }
}

/** Plain Run: `go run <pkg>` or `go test -v <pkg>`, shown in the Run tool window. */
class GoRunState(environment: ExecutionEnvironment, private val spec: GoLaunchSpec) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val go = GoToolLocator.toolchain().go
            ?: throw ExecutionException("Go wasn't found. Install it from https://go.dev/dl/ or add it to PATH.")
        val commandLine = GeneralCommandLine(listOf(go.toString()) + GoCommands.runArgs(spec))
            .withWorkingDirectory(Path.of(spec.effectiveWorkingDir))
            .withEnvironment(GoToolLocator.pathWithGo(go) + spec.env)
        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
