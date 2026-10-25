pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.vaadin.com/vaadin-addons") }
    }
    plugins {
        id("org.springframework.boot") version providers.gradleProperty("springBootVersion").get()
        id("com.vaadin") version providers.gradleProperty("vaadinVersion").get()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.vaadin.com/vaadin-addons") }
    }
}

rootProject.name = "vaadin-remote-browser"
include(":sample")
