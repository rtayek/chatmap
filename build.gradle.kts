import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Container
import org.gradle.plugins.ide.eclipse.model.Library
import java.io.File

plugins {
    application
    eclipse
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("io.quarkus") version "3.39.1"
    checkstyle
    pmd
    id("com.github.spotbugs") version "6.5.10"
    jacoco
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val a2aVersion = "1.3.0.Final"
val quarkusVersion = "3.39.1"

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
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.7")

    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    implementation("org.a2aproject.sdk:a2a-java-sdk-client:$a2aVersion")
    implementation("org.a2aproject.sdk:a2a-java-sdk-client-transport-jsonrpc:$a2aVersion")
    implementation("org.a2aproject.sdk:a2a-java-sdk-reference-jsonrpc:$a2aVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "25.0.1"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("chatmap.presentation.ui.ChatMapLauncher")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    workingDir = layout.projectDirectory.asFile
}

tasks.register<JavaExec>("consolidateChats") {
    group = "application"
    description = "Scans workspace projects and consolidates chats into project handoffs."
    mainClass.set("chatmap.presentation.cli.ChatConsolidatorCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args = listOf("..", "./consolidated_chats")
}

tasks.register<JavaExec>("summarizeChat") {
    group = "application"
    description = "Summarizes and tags one already-imported chat by id. Usage: -Pargs=<chatId>"
    mainClass.set("chatmap.presentation.cli.SummarizeChatCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString())
    }
}

tasks.register<JavaExec>("importChatGptArchive") {
    group = "application"
    description = "Imports a ChatGPT export ZIP. Usage: -Pargs=<chatgpt-export.zip>"
    mainClass.set("chatmap.presentation.cli.ImportChatGptArchiveCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString())
    }
}

tasks.register<JavaExec>("conversationInventory") {
    group = "application"
    description = "Lists all discoverable conversations from configured ChatMap sources."
    mainClass.set("chatmap.presentation.cli.ConversationInventoryCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString())
    }
}

tasks.register<JavaExec>("importAllChats") {
    group = "application"
    description = "Imports every chat discoverable from the configured ChatMap sources."
    mainClass.set("chatmap.presentation.cli.ImportAllChatsCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString())
    }
}

tasks.register<JavaExec>("runPrompt") {
    group = "application"
    description = "Submits a prompt to an LLM backend and stores the result. Usage: -Pargs='<backendId> <prompt>'"
    mainClass.set("chatmap.presentation.cli.RunPromptCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}

tasks.register<JavaExec>("routePrompt") {
    group = "application"
    description = "Classifies, routes, submits, and stores a prompt. Usage: -Pargs='--project <name> <prompt>'"
    mainClass.set("chatmap.presentation.cli.RoutePromptCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}

tasks.register<JavaExec>("seedWorkspaceProjects") {
    group = "application"
    description = "Adds Ray's known local projects. Usage: -Pargs='--project <name>=<dir>'"
    mainClass.set("chatmap.presentation.cli.SeedWorkspaceProjectsCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}

tasks.register<JavaExec>("handoffOrchestrator") {
    group = "application"
    description = "Polls a Git handoff inbox repo and runs discovered tasks against isolated worktrees. " +
            "Usage: -Pargs='--inbox <dir> --registry <projects.properties> [--interval <seconds>] [--auto-push]'"
    mainClass.set("chatmap.presentation.cli.HandoffOrchestratorCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}

tasks.register<JavaExec>("handoffWatcher") {
    group = "application"
    description = "Collects handoff Markdown files from local sources into one inbox. " +
            "Usage: -Pargs='--inbox <dir> --source <dir> [--source <dir> ...] [--once]'"
    mainClass.set("handoff.HandoffWatcher")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}

tasks.register<JavaExec>("workerLifecycleDemo") {
    group = "application"
    description = "Runs the deterministic worker-lifecycle continuity demo."
    mainClass.set("chatmap.presentation.cli.WorkerLifecycleDemoCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}

tasks.register<JavaExec>("workerLifecycleRecord") {
    group = "application"
    description = "Displays one persisted worker-lifecycle record. Usage: -Phome=<dir> -Psession=<id>"
    mainClass.set("chatmap.presentation.cli.WorkerLifecycleRecordCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    val cliArgs = mutableListOf<String>()
    if (project.hasProperty("home")) {
        cliArgs.add("--home")
        cliArgs.add(project.property("home").toString())
    }
    cliArgs.add(providers.gradleProperty("session").getOrElse("1"))
    args = cliArgs
}

tasks.register<JavaExec>("workerLifecycleSoakTest") {
    group = "verification"
    description = "Executes the 1,000-cycle WorkerLifecycle soak harness."
    mainClass.set("chatmap.infrastructure.persistence.sqlite.WorkerLifecycleSoakHarness")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xms256m", "-Xmx512m")

    val cycles = project.findProperty("cycles") ?: "1000"
    val seed = project.findProperty("seed") ?: "42"
    val checkpoint = project.findProperty("checkpointInterval") ?: "500"

    val cliArgs = mutableListOf(
        "--cycles", cycles.toString(),
        "--seed", seed.toString(),
        "--checkpoint-interval", checkpoint.toString()
    )
    if (project.hasProperty("home")) {
        val homeVal = project.property("home").toString()
        if (homeVal.isNotBlank()) {
            cliArgs.add("--home")
            cliArgs.add(homeVal)
        }
    }
    args = cliArgs
}

tasks.register<JavaExec>("a2aRequest") {
    group = "application"
    description = "Sends one request to the experimental A2A worker."
    mainClass.set("chatmap.a2a.experiment.ExperimentClient")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    args(providers.gradleProperty("request").getOrElse("complete:hello"))
}

tasks.register<JavaExec>("a2aContinue") {
    group = "application"
    description = "Runs the same-task A2A input-continuation experiment."
    mainClass.set("chatmap.a2a.experiment.ContinuationClient")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.projectDirectory.asFile
    args(providers.gradleProperty("answer").getOrElse("continued hello"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (project.hasProperty("liveLlm")) {
        systemProperty("chatmap.live.llm", project.property("liveLlm").toString())
    }
    if (project.hasProperty("liveOllamaTarget")) {
        systemProperty("chatmap.live.ollama.target", project.property("liveOllamaTarget").toString())
    }
}

// --- Eclipse without Buildship: copy all dependency jars into lib/ and have
// --- the generated .classpath reference them there, so the IDE resolves them
// --- without the Gradle classpath container. Run: ./gradlew eclipse
val copyLibs by tasks.registering(Sync::class) {
    description = "Copies all dependency jars into lib/ for a non-Buildship Eclipse setup."
    group = "ide"
    // Union of every config the eclipse plugin lists in .classpath, so lib/ never
    // misses a referenced jar (e.g. compile-only transitives like apiguardian).
    from(configurations.compileClasspath)
    from(configurations.runtimeClasspath)
    from(configurations.testCompileClasspath)
    from(configurations.testRuntimeClasspath)
    into(layout.projectDirectory.dir("lib"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

eclipse {
    project {
        natures.remove("org.eclipse.buildship.core.gradleprojectnature")
        buildCommands.removeIf {
            it.name == "org.eclipse.buildship.core.gradleprojectbuilder"
        }
    }
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
                        val scope = lib.entryAttributes["gradle_used_by_scope"]?.toString()
                        if (scope.isNullOrEmpty()) {
                            lib.entryAttributes["gradle_used_by_scope"] = "test"
                        }
                    }
            }
        }
    }
}

tasks.named("eclipseClasspath") { dependsOn(copyLibs) }

tasks.named("eclipseJdt") {
    doLast {
        val prefs = layout.projectDirectory.file(".settings/org.eclipse.jdt.core.prefs").asFile
        if (prefs.isFile) {
            val cleaned = prefs.readLines()
                .dropWhile { it == "#" || it.matches(Regex("#[A-Z][A-Za-z]{2} .* \\d{4}")) }
            prefs.writeText(cleaned.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
        }
    }
}

// -----------------------------------------------------------------------------
// Checkstyle
// -----------------------------------------------------------------------------
checkstyle {
    toolVersion = "10.18.0"
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    isShowViolations = true
}

// -----------------------------------------------------------------------------
// PMD
// -----------------------------------------------------------------------------
pmd {
    toolVersion = "7.26.0"
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSets = emptyList()
    ruleSetConfig = resources.text.fromFile(file("${rootDir}/config/pmd/pmd.xml"))
}

// -----------------------------------------------------------------------------
// SpotBugs
// -----------------------------------------------------------------------------
spotbugs {
    toolVersion = "4.10.3"
    ignoreFailures.set(false)
    excludeFilter.set(file("${rootDir}/config/spotbugs/exclude.xml"))
}

// -----------------------------------------------------------------------------
// JaCoCo Code Coverage
// -----------------------------------------------------------------------------
jacoco {
    toolVersion = "0.8.15"
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
    }
}

// Unified check task lifecycle execution
tasks.named("check") {
    dependsOn(
        tasks.named("checkstyleMain"),
        tasks.named("pmdMain"),
        tasks.named("spotbugsMain"),
        tasks.named("jacocoTestReport")
    )
}
