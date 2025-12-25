plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.gamearchive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.gamearchive"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 分包配置
        splits {
            abi {
                isEnable = true  // 开启分包
                reset()          // 重置默认列表（默认包含一堆不用的架构）

                include("armeabi-v7a", "arm64-v8a")

                // 是否生成一个包含所有架构的“通用包” (Universal APK)
                isUniversalApk = false
            }
        }
    }



    // 打包时忽略非致命的Lint错误，防止构建中断
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        release {
            // 开启代码混淆
            isMinifyEnabled = false
            // 开启资源压缩
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // 基础界面组件
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // 网络请求框架
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // 图片加载框架 (包含GIF支持)
    implementation("io.coil-kt:coil:2.4.0")
    implementation("io.coil-kt:coil-gif:2.4.0")

    // 协程与生命周期扩展库
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.0")
}