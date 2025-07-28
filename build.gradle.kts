import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The libraries are currently published to JitPack. JitPack picks up the
// version from the repo label, resulting in all libraries from the repo
// having the same version in JitPack. Setting the version for all projects
// conveys this.
allprojects {
    group = "org.tree-ware.tree-ware-kotlin-elasticsearch"
    version = "0.5.0.0"
}

val elasticsearchVersion = "8.12.0"

plugins {
    kotlin("jvm") version "2.1.10"
    id("idea")
    id("org.tree-ware.core") version "0.5.0.0"
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

    implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:$elasticsearchVersion")

    testImplementation(libs.treeWareKotlinCoreTestFixtures)
    testImplementation(kotlin("test"))
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