plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
}

group = "no.nav.tsm"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.di)

    implementation(tsmKtorLibs.core)
    implementation(tsmKtorLibs.kafka)
    implementation(tsmKtorLibs.kafka.sykmeldinger)

    // Databases
    implementation(libs.google.cloud.bigquery)

    // Monitoring and logging
    implementation(libs.logback.classic)
    implementation(libs.logback.encoder)


    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.gcloud)
}

tasks {
    shadowJar {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles {}
        from("src/main/resources/logback.xml") {
            into("/")
        }
    }
}
