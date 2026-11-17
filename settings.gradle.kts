// إعدادات المشروع: المستودعات المسموحة فقط (Google + Maven Central + مستودع Mozilla لمحرك GeckoView)
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            // حصر هذا المستودع بحزم Mozilla فقط لأسباب أمنية
            content { includeGroupByRegex("org\\.mozilla.*") }
        }
    }
}
rootProject.name = "VAYRO"
include(":app")
