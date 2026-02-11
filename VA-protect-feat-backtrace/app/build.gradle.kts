import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra

val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra
val androidKotlinJvmTarget: String by rootProject.extra

fun String.exec(): String {
    return Runtime.getRuntime()
        .exec(this.split(" ").toTypedArray())
        .apply { waitFor() }
        .inputStream.bufferedReader().readText().trim()
}

val commitCount = 1  // "git rev-list --count HEAD".exec().toInt()
val commitHash = "123456" //""git rev-parse --short HEAD".exec()

android {
    namespace = "xyz.mufanc.vap"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "xyz.mufanc.vap"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = commitCount
        versionName = "$commitHash.r$commitCount"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }

    kotlinOptions {
        jvmTarget = androidKotlinJvmTarget
    }

    sourceSets {
        named("main") {
            proto {
                srcDir("src/main/proto")
                include("**/*.proto")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // ui
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // api stub
    compileOnly(project(":api-stub"))

    // xposed
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.joor)

    // autox
    ksp(libs.autox.ksp)
    implementation(libs.autox.annotation)

    // websocket server
    implementation(libs.java.websocket)

    // gson
    implementation(libs.gson)

    // protobuf
    implementation(libs.protobuf.java)

    // libsu
    implementation(libs.libsu.core)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}
