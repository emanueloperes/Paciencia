import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Gera a lista de partidas com solução (SolvableSeeds.kt). Demora alguns minutos.
tasks.register<JavaExec>("generateSolvableSeeds") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.emanueloperes.paciencia.engine.GenerateSolvableSeedsKt")
    jvmArgs("-Xss256m", "-Xmx4g")
    args(file("src/main/kotlin/com/emanueloperes/paciencia/engine/SolvableSeeds.kt").path)
}
