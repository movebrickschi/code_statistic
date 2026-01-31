plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "io.github.movebrickschi.codestatistic"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots") // 根据实际仓库地址调整
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // 添加必要的插件依赖以支持编译
        instrumentationTools()
    }
    // 添加 SLF4J 依赖
    implementation("org.slf4j:slf4j-api:2.0.9")
    // 选择一个 SLF4J 实现，例如：
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "240"
        }

        changeNotes = """
            English:
            <ul>
                <li>v1.1.0: Statistics based on current Git branch, filtering invalid code changes (empty lines, comments, formatting changes, etc.)</li>
                <li>Support for viewing code statistics in real-time</li>
                <li>Added commit history tracking feature</li>
            </ul>
            
            中文:
            <ul>
                <li>v1.1.0: 基于当前 Git 分支进行统计，过滤无效代码变更（空行、注释、格式调整等）</li>
                <li>支持实时查看代码统计信息</li>
                <li>新增提交历史跟踪功能</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
