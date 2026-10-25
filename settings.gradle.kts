pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.vaadin.com/vaadin-addons") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); maven { url = uri("https://maven.vaadin.com/vaadin-addons") } }
}
rootProject.name = "vaadin-browser-bridge"
