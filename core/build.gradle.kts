plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

// Plain-Kotlin test runner: no test framework dependency needed.
tasks.register<JavaExec>("runCoreTests") {
    group = "verification"
    description = "Runs the core logic tests (PIN hashing, lockout, timers, URL safety, tracker matching, presets)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("CoreTestsKt")
}
tasks.named("check") { dependsOn("runCoreTests") }
