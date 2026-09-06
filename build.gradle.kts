import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The libraries are currently published to JitPack. JitPack picks up the
// version from the repo label, resulting in all libraries from the repo
// having the same version in JitPack. Setting the version for all projects
// conveys this.
allprojects {
    group = "org.tree-ware.tree-ware-kotlin-elasticsearch"
    version = "0.5.0.0"
}

val elasticsearchVersion = "9.0.3"

plugins {
    kotlin("jvm") version "2.1.10"
    id("idea")
    id("org.tree-ware.core") version "0.6.1.0"
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(libs.treeWareKotlinCore)
    implementation(kotlin("stdlib"))

    implementation("co.elastic.clients:elasticsearch-java:$elasticsearchVersion")
    // Used by main source to parse password/association JSON into nested document objects.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation(libs.treeWareKotlinCoreTestFixtures)
    testImplementation(kotlin("test"))
    // Binds the slf4j facade used by lighthousegames logging so that
    // `logRequests = true` output is observable in tests.
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform {
        when (System.getProperty("integrationTests", "")) {
            "include" -> includeTags("integrationTest")
            "exclude" -> excludeTags("integrationTest")
            else -> {}
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}