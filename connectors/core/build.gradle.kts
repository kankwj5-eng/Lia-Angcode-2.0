plugins {
    id("com.android.library")
}

android {
    namespace = "com.kankwj.angcode.connectors"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":runtime-core"))
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.15.0")
    implementation("io.ktor:ktor-client-cio:3.5.1")
    testImplementation("junit:junit:4.13.2")
}
