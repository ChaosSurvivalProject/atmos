plugins {
    id("fabric-loom") version "1.13.3"
    java
}

base {
    archivesName.set("atmos-client")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.17.2")

    // 仅依赖需要的 Fabric API 子模块（避开全量包，规避 Loom javadoc 处理问题）
    modImplementation("net.fabricmc.fabric-api:fabric-api-base:1.0.5+4ebb5c083e")
    modImplementation("net.fabricmc.fabric-api:fabric-networking-api-v1:5.1.6+6b6d71a53e")
    modImplementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:2.6.15+4ebb5c083e")
    modImplementation("net.fabricmc.fabric-api:fabric-rendering-v1:16.2.10+0290ad933e")

    // 共享协议模块（纯 Java，作为 includedJar 打进 mod）
    implementation(project(":protocol"))
    include(project(":protocol"))
}

loom {
    mods {
        create("atmos") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version.toString()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
