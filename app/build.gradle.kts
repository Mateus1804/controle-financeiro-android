import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val signingEnvironment = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val releaseVersionCode = providers.gradleProperty("appVersionCode").orElse("2").get().toIntOrNull()
    ?: throw GradleException("appVersionCode precisa ser um inteiro.")
require(releaseVersionCode >= 2) { "appVersionCode precisa ser maior que o da versão anterior (1)." }
val releaseVersionName = providers.gradleProperty("appVersionName").orElse("0.2.0").get()
require(releaseVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][A-Za-z0-9.]+)?"))) { "appVersionName precisa identificar uma versão, por exemplo 0.2.0." }

val validateReleaseSigning by tasks.registering {
    doLast {
        check(signingEnvironment.values.all { !it.isNullOrBlank() }) {
            "Configure ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS e ANDROID_KEY_PASSWORD fora do repositório. A assinatura debug não substitui a chave definitiva."
        }
        check(file(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH")!!).isFile) { "Keystore de assinatura indisponível." }
    }
}

android {
    namespace = "com.mateuswilliam.financas"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mateuswilliam.financas"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "com.mateuswilliam.financas.IsolatedAndroidTestRunner"
    }
    signingConfigs {
        create("release") {
            if (signingEnvironment.values.all { !it.isNullOrBlank() }) {
                storeFile = file(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH")!!)
                storePassword = signingEnvironment["ANDROID_KEYSTORE_PASSWORD"]
                keyAlias = signingEnvironment["ANDROID_KEY_ALIAS"]
                keyPassword = signingEnvironment["ANDROID_KEY_PASSWORD"]
            }
        }
    }
    buildTypes.getByName("release") { signingConfig = signingConfigs.getByName("release") }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
    testOptions { animationsDisabled = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
