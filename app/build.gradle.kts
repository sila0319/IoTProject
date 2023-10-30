plugins {
    id("com.android.application")
}

android {
    namespace = "org.airpage.heartbeat" // 변경된 부분
    compileSdk = 32 // 변경된 부분

    defaultConfig {
        applicationId = "org.airpage.heartbeat" // 변경된 부분
        minSdk = 21 // 변경된 부분
        targetSdk = 32 // 변경된 부분
        versionCode = 1 // 변경된 부분
        versionName = "1.0" // 변경된 부분

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false // 변경된 부분
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // 변경된 부분
        targetCompatibility = JavaVersion.VERSION_1_8 // 변경된 부분
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    implementation("com.google.android.gms:play-services-fitness:16.0.1")
    implementation("com.google.android.gms:play-services-auth:16.0.1")
}
