plugins {
    id("com.android.library")
}

android {
    namespace = "com.kankwj.angcode.agents"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":runtime-core"))
    testImplementation("junit:junit:4.13.2")
}
