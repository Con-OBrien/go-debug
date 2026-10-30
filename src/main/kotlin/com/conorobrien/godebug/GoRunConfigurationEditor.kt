package com.conorobrien.godebug

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class GoRunConfigurationEditor : SettingsEditor<GoRunConfiguration>() {

    private val packageDir = JBTextField()
    private val kind = ComboBox(GoRunKind.entries.toTypedArray())
    private val testPattern = JBTextField()
    private val programArgs = JBTextField()
    private val goToolArgs = JBTextField()
    private val workingDir = JBTextField().apply { emptyText.setText("Package directory") }
    private val env = EnvironmentVariablesComponent()

    override fun createEditor(): JComponent = panel {
        row("Package directory:") {
            cell(packageDir).align(AlignX.FILL)
                .comment("Absolute path of the package with <code>func main()</code>, or with the tests to run.")
        }
        row("Run:") { cell(kind) }
        row("Test pattern:") {
            cell(testPattern).align(AlignX.FILL)
                .comment("Passed to <code>-run</code>, e.g. <code>^TestParse$</code>. Blank runs every test.")
        }
        row("Program arguments:") { cell(programArgs).align(AlignX.FILL) }
        row("Go tool arguments:") {
            cell(goToolArgs).align(AlignX.FILL)
                .comment("Build flags, e.g. <code>-race -tags=integration</code>.")
        }
        row("Working directory:") { cell(workingDir).align(AlignX.FILL) }
        row { cell(env).align(AlignX.FILL) }
    }

    override fun resetEditorFrom(configuration: GoRunConfiguration) {
        packageDir.text = configuration.packageDir
        kind.selectedItem = configuration.kind
        testPattern.text = configuration.testPattern
        programArgs.text = configuration.programArgs
        goToolArgs.text = configuration.goToolArgs
        workingDir.text = configuration.workingDir
        env.envs = configuration.env
    }

    override fun applyEditorTo(configuration: GoRunConfiguration) {
        configuration.packageDir = packageDir.text.trim()
        configuration.kind = kind.selectedItem as? GoRunKind ?: GoRunKind.APPLICATION
        configuration.testPattern = testPattern.text.trim()
        configuration.programArgs = programArgs.text
        configuration.goToolArgs = goToolArgs.text
        configuration.workingDir = workingDir.text.trim()
        configuration.env = LinkedHashMap(env.envs)
    }
}
