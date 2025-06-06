plugins {
    id("net.kyori.blossom")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

sourceSets {
    main {
        blossom {
            javaSources {
                property("version", project.version.toString())
                property("impl_version", "git-ViaVersion-${project.version}:${rootProject.latestCommitHash()}")
            }
        }
    }
}

dependencies {
    api(libs.fastutil)
    api(libs.vianbt) {
        exclude("it.unimi.dsi", "fastutil")
    }
    api(libs.gson) {
        exclude("com.google.errorprone", "error_prone_annotations")
    }
    implementation(rootProject.libs.text) {
        exclude("com.google.code.gson", "gson")
        exclude("com.viaversion", "nbt")
    }
    api(libs.snakeYaml)

    compileOnlyApi(libs.netty)
    compileOnlyApi(libs.guava) {
        exclude("com.google.errorprone", "error_prone_annotations")
        exclude("org.jspecify", "jspecify")
        exclude("com.google.j2objc", "j2objc-annotations")
    }
    compileOnlyApi(libs.checkerQual)
}

java {
    withJavadocJar()
}
