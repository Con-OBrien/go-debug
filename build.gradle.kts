import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2026.2.3")
        // The Debug Adapter Protocol client is a separate product module, loaded in its own
        // classloader (unlike LSP), so it must be on the compile classpath explicitly.
        bundledModule("intellij.platform.dap")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Built and checked against the 2026.2 DAP API, which is still marked experimental.
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}
