@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "io.github.lumiagg.gestureevo"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "io.github.lumiagg.gestureevo"
        minSdk = 24
        targetSdk = 36
        versionCode = 261105001
        versionName = "2.2-1105001"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    // 签名参数由环境变量注入。keyPassword 可省略，缺省时回落到 storePassword
    // （PKCS12 keystore 里两者本就相同）。
    val signingKeys = listOf(
        "GESTUREEVO_KEYSTORE_FILE", "GESTUREEVO_KEYSTORE_PASSWORD",
        "GESTUREEVO_KEY_ALIAS", "GESTUREEVO_KEY_PASSWORD",
    )
    val signingEnvironment = signingKeys.map { providers.environmentVariable(it).orNull }
    // 三项必需（file/password/alias）。缺任一项时默认降级为未签名产物；
    // CI 上需要硬失败则设置 GESTUREEVO_REQUIRE_SIGNING=1，避免"以为签了、其实出了 unsigned 包"。
    val missingSigningKeys = signingKeys.take(3).zip(signingEnvironment).filter { it.second.isNullOrEmpty() }
        .map { it.first }
    // 注意：Kotlin 的 "1".toBoolean() 为 false，只认 "true"，这里显式接受 1/true/yes/on。
    val requireReleaseSigning = providers.environmentVariable("GESTUREEVO_REQUIRE_SIGNING").orNull
        ?.lowercase()
        ?.let { it == "1" || it == "true" || it == "yes" || it == "on" } ?: false
    if (requireReleaseSigning && missingSigningKeys.isNotEmpty()) {
        throw GradleException(
            "GESTUREEVO_REQUIRE_SIGNING=1 但缺少签名环境变量: ${missingSigningKeys.joinToString()}"
        )
    }
    val releaseSigning = if (missingSigningKeys.isEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(signingEnvironment[0]))
            storePassword = signingEnvironment[1]
            keyAlias = signingEnvironment[2]
            keyPassword = signingEnvironment[3] ?: signingEnvironment[1]
        }
    } else null

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = releaseSigning
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true  // 启用 BuildConfig
        viewBinding = true
        aidl = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/aidl")
            assets.srcDir(layout.buildDirectory.dir("generated/ocrModelAssets"))
            assets.srcDir(layout.buildDirectory.dir("generated/projectDocuments"))
        }
    }
    androidResources {
        noCompress += "xz"
    }
    packaging {
        jniLibs.keepDebugSymbols.add("**/libc++_shared.so")
    }
}

val compressOcrModels by tasks.registering(Exec::class) {
    val sourceDir = layout.projectDirectory.dir("src/main/modelAssets/models")
    val outputDir = layout.buildDirectory.dir("generated/ocrModelAssets/models")
    val archiveFile = outputDir.map { it.file("ocr_models.tar.xz") }
    val versionFile = outputDir.map { it.file("ocr_models.version") }

    inputs.dir(sourceDir)
    inputs.file(rootProject.layout.projectDirectory.file("tools/compress_ocr_models.py"))
    outputs.file(archiveFile)
    outputs.file(versionFile)

    val defaultPythonExecutable = providers.systemProperty("os.name").map { name ->
        if (name.startsWith("Windows", ignoreCase = true)) "python" else "python3"
    }

    commandLine(
        providers.gradleProperty("pythonExecutable").orElse(defaultPythonExecutable).get(),
        layout.projectDirectory.file("../tools/compress_ocr_models.py").asFile.absolutePath,
        "--source",
        sourceDir.asFile.absolutePath,
        "--output",
        archiveFile.get().asFile.absolutePath,
        "--version-output",
        versionFile.get().asFile.absolutePath
    )
}

val prepareProjectDocuments by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("generated/projectDocuments"))
    from(rootProject.file("LICENSE")) {
        into("licenses")
        rename { "project_license.txt" }
    }
}

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(compressOcrModels)
    dependsOn(prepareProjectDocuments)
}

configurations.configureEach {
    resolutionStrategy.force(
        libs.kotlinx.serialization.core.get().toString(),
        libs.kotlinx.serialization.json.get().toString()
    )
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Android UI and platform integrations.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.animation)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.brv)
    implementation(libs.appiconloader)

    // Persistence and JSON; processors share the runtime versions.
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Privileged platform access.
    implementation(libs.bundles.shizuku)
    implementation(libs.hiddenapibypass)

    // Text processing, model compression, barcode decoding and WebDAV.
    // Local binary provenance and checksum: third_party/binary-manifest.json.
    implementation(files("libs/tinypinyin-2.0.3.jar"))
    implementation(libs.ahocorasick)
    implementation(libs.xz)
    implementation(libs.zxing.core)
    implementation(libs.sardine.android)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}
