import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.twtrubiks.clockstandby"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.twtrubiks.clockstandby"
        // Android 7.0；java.time 靠 core library desugaring 補上
        minSdk = 24
        targetSdk = 36
        // 發佈新版時 versionCode + 1、versionName 照 semver 改，再打同名的 tag（v1.0.0），見 docs/release.md
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 正式金鑰只有發佈 workflow（.github/workflows/release.yml）會用環境變數提供
        val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                // keytool 預設的 PKCS12 格式，金鑰和 keystore 是同一個密碼
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            // R8 刪掉沒用到的程式碼並最佳化，Compose 在 release 版會比 debug 版順很多
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 沒有正式金鑰（VM、錄影 workflow）時用 debug 金鑰簽，冒煙測試才能覆蓋安裝在 debug 版上
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // 預覽版：給使用者試用開發中的版本。和 release 一樣經過 R8，但用 debug 金鑰簽、
        // applicationId 加上 .preview，能和正式版同時裝在手機上，各自保留設定
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
