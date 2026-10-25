plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vaadin")
}

group = "dev.w0fv1"
version = "0.2.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

vaadin {
    optimizeBundle = false
}

springBoot {
    mainClass.set("dev.w0fv1.remotebrowser.sample.RemoteBrowserSampleApplication")
}

dependencies {
    implementation(project(":"))
    developmentOnly("com.vaadin:vaadin-dev")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
