plugins {
    java
    eclipse
    id("io.quarkus") version "3.39.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val a2aVersion = "1.3.0.Final"
val quarkusVersion = "3.39.1"

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    implementation("org.a2aproject.sdk:a2a-java-sdk-client:$a2aVersion")
    implementation("org.a2aproject.sdk:a2a-java-sdk-client-transport-jsonrpc:$a2aVersion")
    implementation("org.a2aproject.sdk:a2a-java-sdk-reference-jsonrpc:$a2aVersion")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("tst"))
        resources.setSrcDirs(listOf("test-resources"))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("a2aRequest") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "chatmap.a2a.experiment.ExperimentClient"
    args(providers.gradleProperty("request").getOrElse("complete:hello"))
}
