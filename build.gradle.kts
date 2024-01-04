plugins {
    kotlin("jvm").version("1.9.0")
    id("com.github.johnrengelman.shadow").version("7.0.0")
}

group = "me.purp.mynt-iouring"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.compileJava {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.compileKotlin.get().kotlinOptions {
    languageVersion = "1.7"
    jvmTarget = "17"
    freeCompilerArgs = listOf(
            "-Xcontext-receivers", "-Xinlilne-classes",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlin.contracts.ExperimentalContracts",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.DelicateCoroutinesApi"
    )
}

tasks.shadowJar {
    archiveFileName.set("${project.name}.jar")
    manifest.attributes["Main-Class"] = "me.purp.mynt.Main"
}

tasks.build { dependsOn(tasks.shadowJar) }
