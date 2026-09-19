import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.aboutLibraries)
}

// 签名配置：优先读取根目录 keystore.properties（已加入 .gitignore，勿提交），
// 不存在时回退到环境变量（GitHub Actions 注入）。
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

val releaseStoreFile: File? = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?: System.getenv("RELEASE_KEYSTORE_FILE")?.let { File(it) }

val releaseStorePassword = keystoreProperties.getProperty("storePassword")
    ?: System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty()

val releaseKeyAlias = keystoreProperties.getProperty("keyAlias")
    ?: System.getenv("RELEASE_KEY_ALIAS").orEmpty()

val releaseKeyPassword = keystoreProperties.getProperty("keyPassword")
    ?: System.getenv("RELEASE_KEY_PASSWORD").orEmpty()

android {
    namespace = "com.yunmei.vibe"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.yunmei.vibe"
        minSdk = 26
        targetSdk = 37
        versionCode = 27
        versionName = "0.4.4"

        // CI 构建号：GitHub Actions 在 Debug 工作流里通过 -PbuildStamp=<run_number> 注入，
        // App 内 Debug 更新检查据此与远端 ci-* 标签的 run_number 精确比较；
        // 本地构建（未传参）为 "0"，即任何 CI 产物都视为更新。见 .github/workflows/build-debug.yml。
        buildConfigField("String", "BUILD_STAMP", "\"${project.findProperty("buildStamp") ?: "0"}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

// 开放源代码许可页的库清单由 AboutLibraries 在构建期自动生成（InstallerX Revived 同款）。
// 额外库（非 Gradle 依赖的上游项目）通过 configPath 下的 libraries/*.json 注入。
aboutLibraries {
    collect {
        configPath = file("config")
    }
    license {
        // 上游项目使用的许可证（GPL-3.0 / MIT）补全全文，
        // 否则 configPath 下 libraries/*.json 里的 licenses 引用无法解析。
        additionalLicenses.addAll("MIT")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Monet / Material You 动态取色引擎（KernelSU 同款封装）。
    implementation(libs.material.kolor)

    // 官方 Miuix 组件库（KernelSU-Style-UI-Kit 同款接入）。
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    // 官方 Miuix 设置项组件（SwitchPreference / OverlayDropdownPreference / ArrowPreference）。
    implementation(libs.miuix.preference)
    // miuix-nav：自带返回栈 + 预测性返回过渡运行时（InstallerX Revived 的返回动画依赖它）。
    implementation(libs.miuix.navigation)

    // 手势返回（navigationevent）：miuix-nav 的预测性返回与 entry 包装依赖它。
    implementation(libs.androidx.navigationevent.compose)

    // 导航层已整体切到 miuix-nav，androidx navigation3 的 runtime / ui 不再被任何代码引用；
    // 与上游 InstallerX Revived 的 build 文件保持同形态：保留版本目录条目，仅注释掉引用。
    // implementation(libs.androidx.navigation3.runtime)
    // implementation(libs.androidx.navigation3.ui)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 网络层：登录/学校/门锁/打卡/开锁密码接口。
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    // 加密存储（账号/门锁敏感数据）与二维码能力。
    implementation(libs.androidx.security.crypto)
    implementation(libs.zxing.core)

    // 偏好设置（DataStore）。
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.datastore.preferences)

    // FastBle 2.3.4 原本托管在已停服的 JCenter；改用本地 AAR，让构建不依赖外部镜像。
    implementation(files("libs/FastBleLib-2.3.4.aar"))

    // 开放源代码许可页（AboutLibraries，与 InstallerX Revived 同款实现）。
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
