plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.positivefinger.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.positivefinger.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.3.0"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.systemProperty("sumire.assets", file("src/main/assets").absolutePath) }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
