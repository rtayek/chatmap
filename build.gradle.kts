import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Container
import org.gradle.plugins.ide.eclipse.model.Library
import java.io.File

plugins {
    application
    eclipse
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
        resources {
            setSrcDirs(listOf("src"))
            exclude("**/*.java")
        }
    }
    test {
        java {
            setSrcDirs(listOf("tst"))
        }
        resources {
            setSrcDirs(listOf("tst"))
            exclude("**/*.java")
        }
    }
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    implementation("org.slf4j:slf4j-api:2.0.18")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.18")
    implementation("com.microsoft.playwright:playwright:1.49.0")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "25.0.1"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("chatmap.ui.ChatMapLauncher")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("consolidateChats") {
    group = "application"
    description = "Scans workspace projects and consolidates chats into project handoffs."
    mainClass.set("chatmap.cli.ChatConsolidatorCli")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args = listOf("..", "./consolidated_chats")
}

tasks.register<JavaExec>("summarizeChat") {
    group = "application"
    description = "Summarizes and tags one already-imported chat by id. Usage: -Pargs=<chatId>"
    mainClass.set("chatmap.cli.SummarizeChatCli")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// --- Eclipse without Buildship: copy all dependency jars into lib/ and have
// --- the generated .classpath reference them there, so the IDE resolves them
// --- without the Gradle classpath container. Run: ./gradlew eclipse
val copyLibs by tasks.registering(Sync::class) {
    description = "Copies all dependency jars into lib/ for a non-Buildship Eclipse setup."
    group = "ide"
    from(configurations.testRuntimeClasspath) // superset: main runtime + test deps
    into(layout.projectDirectory.dir("lib"))
}

eclipse {
    classpath {
        file {
            whenMerged {
                val classpath = this as Classpath
                // Drop the Buildship container (unresolvable without Buildship installed).
                classpath.entries.removeIf {
                    it is Container && it.path.contains("buildship")
                }
                classpath.entries
                    .filterIsInstance<Library>()
                    .forEach { lib ->
                        // Point each library entry at the copied jar in lib/ (project-relative).
                        lib.path = "lib/" + File(lib.path).name
                        lib.sourcePath = null
                    }
            }
        }
    }
}

tasks.named("eclipseClasspath") { dependsOn(copyLibs) }

