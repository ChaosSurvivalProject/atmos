plugins {
    java
    `java-library`
}

dependencies {
    // Paper API 提供 Bukkit/Paper 接口（compileOnly，运行时由服务端提供）
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation(project(":protocol"))
}

// 把 protocol 打进插件 jar（shadow 风格的 minimal fat jar，避免额外插件依赖）
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    dependsOn(":protocol:jar")
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.contains("protocol") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") {
    dependsOn(fatJar)
}
