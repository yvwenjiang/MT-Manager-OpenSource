plugins {
    `kotlin-dsl`
}

group = "com.mtopensource.buildlogic"

if (providers.gradleProperty("useChinaMirrors").getOrElse("false") == "true") {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
} else {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencies {
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
}
