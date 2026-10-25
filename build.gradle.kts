plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vaadin") version "25.3.2"
}

group = "dev.w0fv1"
version = "0.1.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

vaadin { optimizeBundle = false }

dependencies {
    implementation(platform("com.vaadin:vaadin-bom:25.3.2"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.vaadin:vaadin-spring-boot-starter")
    developmentOnly(platform("com.vaadin:vaadin-bom:25.3.2"))
    developmentOnly("com.vaadin:vaadin-dev")
    implementation("com.microsoft.playwright:playwright:1.61.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.awaitility:awaitility")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("headed")
    }
}

tasks.register<Test>("headedTest") {
    description = "Runs browser tests that require a graphical desktop"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    useJUnitPlatform {
        includeTags("headed")
    }
    shouldRunAfter(tasks.test)
}
