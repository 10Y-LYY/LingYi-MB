plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lingyi.mbos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lingyi.mbos"
        minSdk = 24
        targetSdk = 34
        versionCode = 53
        // versionName 由下方各 productFlavor 覆盖（stable / preview / devtest）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "release"

    productFlavors {
        create("stable") {
            // 基础 applicationId = com.lingyi.mbos（无后缀），作为正式发布包
            dimension = "release"
            versionName = "4.2.7"
            buildConfigField("String", "DEFAULT_CHANNEL", "\"stable\"")
            buildConfigField("boolean", "IS_DEVTEST", "false")
            resValue("string", "app_name", "灵逸 MB")
            resValue("bool", "devtest_only", "false")
        }
        create("preview") {
            dimension = "release"
            applicationIdSuffix = ".preview"
            // 本次「Voyage 风格界面」为预览版专属发布：仅预览通道独立升构建号，
            // 稳定版 / 开发版沿用 53（其线上 APK 与 version*.json 均不动，不触发误更新）
            versionCode = 59
            versionName = "4.2.7-preview"
            buildConfigField("String", "DEFAULT_CHANNEL", "\"preview\"")
            // 预览版同样自带开发者模式（开发者工具箱 + 对外接口默认开启）
            buildConfigField("boolean", "IS_DEVTEST", "true")
            resValue("string", "app_name", "灵逸 MB 预览")
            resValue("bool", "devtest_only", "true")
        }
        create("devtest") {
            dimension = "release"
            applicationIdSuffix = ".dev"
            versionName = "4.2.7-dev"
            buildConfigField("String", "DEFAULT_CHANNEL", "\"dev\"")
            buildConfigField("boolean", "IS_DEVTEST", "true")
            resValue("string", "app_name", "灵逸 MB 开发")
            resValue("bool", "devtest_only", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // 让 Robolectric 使用合并后的完整资源表（含 appcompat/material 等库资源），
            // 否则 abc_vector_test 等库资源找不到。DSL 接收器类型未直接暴露该属性，做一次转型。
            (this as com.android.build.api.dsl.UnitTestOptions).isIncludeAndroidResources = true
            // Robolectric 全页面生命周期测试较吃内存；沙箱内存有限，单 JVM 跑全部类会 OOM。
            // 这里给 2g 堆，并改用「分批 --tests 运行」的方式控制每批内存（见构建脚本）。
            all { it.maxHeapSize = "2g" }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.mockito:mockito-core:5.12.0")
}
