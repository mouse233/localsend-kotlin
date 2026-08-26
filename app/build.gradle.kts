import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
}

abstract class GenerateAppDocuments : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        project.copy {
            from(project.rootProject.projectDir) {
                include("CHANGELOG.md", "LICENSE", "NOTICE")
            }
            into(outputDirectory.get().asFile)
        }
    }
}

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningEnabled = releaseSigningValues.all { !it.isNullOrBlank() }

check(releaseSigningEnabled || releaseSigningValues.all { it.isNullOrBlank() }) {
    "Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_RELEASE_STORE_PASSWORD, " +
        "ANDROID_RELEASE_KEY_ALIAS, and ANDROID_RELEASE_KEY_PASSWORD."
}

android {
    namespace = "io.github.mouse233.localsendkotlin"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mouse233.localsendkotlin"
        minSdk = 21
        targetSdk = 33
        versionCode = 14
        versionName = "0.7.1-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseSigningEnabled) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val copyAppDocuments = tasks.register<GenerateAppDocuments>("copyAppDocuments") {
    outputDirectory.set(layout.buildDirectory.dir("generated/assets/documents"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyAppDocuments, GenerateAppDocuments::outputDirectory)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.gson)
    implementation(libs.nanhttpd)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
