package com.conorobrien.godebug

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Editor right-click → Run / Debug Go Package. Reuses (or creates) a Go run configuration for the
 * file's directory, so breakpoints and settings carry over between runs. In a *_test.go file it
 * runs the package's tests instead.
 *
 * TextMate-highlighted .go files have no Go PSI, so these actions stand in for gutter run icons.
 */
abstract class GoPackageAction(private val debug: Boolean) : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = e.project != null && file != null && !file.isDirectory && file.name.endsWith(".go")
        e.presentation.isEnabledAndVisible = visible
        if (visible) {
            val verb = if (debug) "Debug" else "Run"
            e.presentation.text = if (isTestFile(file!!)) "$verb Go Tests in Package" else "$verb Go Package"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val dir = file.parent ?: return
        val kind = if (isTestFile(file)) GoRunKind.TEST else GoRunKind.APPLICATION

        val settings = findOrCreate(project, dir.path, kind)
        val executor: Executor =
            if (debug) DefaultDebugExecutor.getDebugExecutorInstance() else DefaultRunExecutor.getRunExecutorInstance()
        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }

    private fun findOrCreate(project: Project, packageDir: String, kind: GoRunKind): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val type = ConfigurationTypeUtil.findConfigurationType(GoRunConfigurationType::class.java)

        runManager.getConfigurationSettingsList(type).firstOrNull { existing ->
            val configuration = existing.configuration as? GoRunConfiguration
            configuration != null && configuration.packageDir == packageDir && configuration.kind == kind &&
                configuration.testPattern.isBlank()
        }?.let {
            runManager.selectedConfiguration = it
            return it
        }

        val factory = type.configurationFactories.first()
        val settings = runManager.createConfiguration("Go", factory)
        (settings.configuration as GoRunConfiguration).apply {
            this.packageDir = packageDir
            this.kind = kind
            settings.name = suggestedName()
        }
        runManager.setUniqueNameIfNeeded(settings)
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        return settings
    }

    private fun isTestFile(file: VirtualFile): Boolean = file.name.endsWith("_test.go")
}

class RunGoPackageAction : GoPackageAction(debug = false)

class DebugGoPackageAction : GoPackageAction(debug = true)
